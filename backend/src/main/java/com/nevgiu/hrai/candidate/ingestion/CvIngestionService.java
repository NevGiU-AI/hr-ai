package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.CvDocumentSource;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.candidate.ingestion.dto.CvArchiveImportResult;
import com.nevgiu.hrai.candidate.ingestion.dto.CvImportResult;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class CvIngestionService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private final CvIngestionProperties properties;
    private final CvDocumentImportService documentImportService;
    private final ResourceLoader resourceLoader;

    public CvIngestionService(
            CvIngestionProperties properties,
            CvDocumentImportService documentImportService,
            ResourceLoader resourceLoader
    ) {
        this.properties = properties;
        this.documentImportService = documentImportService;
        this.resourceLoader = resourceLoader;
    }

    public CvImportResult importPdf(MultipartFile file, String organizationId) {
        if (file == null || file.isEmpty()) {
            throw new CvIngestionException(HttpStatus.BAD_REQUEST, "A non-empty PDF file is required");
        }
        try {
            return importPdf(file.getOriginalFilename(), file.getContentType(), file.getBytes(),
                    CvDocumentSource.USER_UPLOAD, organizationId);
        } catch (IOException e) {
            throw new CvIngestionException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded PDF");
        }
    }

    public CvArchiveImportResult importArchive(MultipartFile file, String organizationId) {
        if (file == null || file.isEmpty()) {
            throw new CvIngestionException(HttpStatus.BAD_REQUEST, "A non-empty ZIP file is required");
        }
        validateArchiveName(file.getOriginalFilename());
        try {
            return importArchive(file.getInputStream(), file.getSize(), CvDocumentSource.USER_UPLOAD, organizationId);
        } catch (IOException e) {
            throw new CvIngestionException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded ZIP archive");
        }
    }

    public CvArchiveImportResult importInitialArchive(String organizationId) {
        if (!properties.initialImportEnabled()) {
            throw new CvIngestionException(HttpStatus.FORBIDDEN, "Initial CV import is disabled");
        }
        Resource resource = resourceLoader.getResource(properties.initialResource());
        if (!resource.exists()) {
            throw new CvIngestionException(HttpStatus.NOT_FOUND, "Initial CV archive was not found");
        }
        try (InputStream input = resource.getInputStream()) {
            return importArchive(input, resource.contentLength(), CvDocumentSource.INITIAL_DATA, organizationId);
        } catch (IOException e) {
            throw new CvIngestionException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read the initial CV archive");
        }
    }

    CvArchiveImportResult importArchive(InputStream input, long archiveSize, CvDocumentSource source,
                                        String organizationId) throws IOException {
        if (archiveSize > properties.maxArchiveSize()) {
            throw new CvIngestionException(HttpStatus.PAYLOAD_TOO_LARGE, "ZIP archive exceeds the configured size limit");
        }

        List<CvImportResult> results = new ArrayList<>();
        long totalExpanded = 0;
        int fileCount = 0;

        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                fileCount++;
                if (fileCount > properties.maxArchiveEntries()) {
                    throw new CvIngestionException(HttpStatus.PAYLOAD_TOO_LARGE, "ZIP archive contains too many files");
                }

                String entryName = validateEntryName(entry.getName());
                if (!entryName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    results.add(result(entryName, CvIngestionStatus.SKIPPED, List.of("Unsupported archive entry; only PDF files are imported")));
                    zip.closeEntry();
                    continue;
                }

                try {
                    byte[] content = readEntry(zip, properties.maxPdfSize());
                    totalExpanded += content.length;
                    if (totalExpanded > properties.maxExpandedSize()) {
                        throw new CvIngestionException(HttpStatus.PAYLOAD_TOO_LARGE, "ZIP archive expands beyond the configured limit");
                    }
                    if (entry.getCompressedSize() > 0
                            && ((double) content.length / entry.getCompressedSize()) > properties.maxCompressionRatio()) {
                        throw new CvIngestionException(HttpStatus.PAYLOAD_TOO_LARGE, "ZIP entry has an unsafe compression ratio: " + entryName);
                    }
                    results.add(documentImportService.importPdf(
                            entryName, PDF_CONTENT_TYPE, content, source, organizationId));
                } catch (CvIngestionException e) {
                    if (e.getStatus() == HttpStatus.PAYLOAD_TOO_LARGE) {
                        throw e;
                    }
                    results.add(result(entryName, CvIngestionStatus.FAILED, List.of(e.getMessage())));
                } catch (RuntimeException e) {
                    results.add(result(entryName, CvIngestionStatus.FAILED, List.of("Unexpected ingestion failure")));
                } finally {
                    zip.closeEntry();
                }
            }
        }

        return summarize(results);
    }

    public CvImportResult importPdf(String filename, String contentType, byte[] content, CvDocumentSource source,
                                    String organizationId) {
        return documentImportService.importPdf(filename, contentType, content, source, organizationId);
    }

    private void validateArchiveName(String filename) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new CvIngestionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only ZIP archives are supported");
        }
    }

    private String validateEntryName(String name) {
        if (name == null || name.isBlank()) {
            throw new CvIngestionException(HttpStatus.BAD_REQUEST, "ZIP archive contains an unnamed entry");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) {
            throw new CvIngestionException(HttpStatus.BAD_REQUEST, "ZIP archive contains an absolute path");
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new CvIngestionException(HttpStatus.BAD_REQUEST, "ZIP archive contains path traversal");
            }
        }
        return normalized;
    }

    private byte[] readEntry(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new CvIngestionException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF entry exceeds the configured size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private CvImportResult result(String filename, CvIngestionStatus status, List<String> warnings) {
        return new CvImportResult(null, null, filename, status, null, 0, warnings);
    }

    private CvArchiveImportResult summarize(List<CvImportResult> results) {
        int imported = 0, duplicates = 0, needsReview = 0, skipped = 0, failed = 0;
        for (CvImportResult result : results) {
            switch (result.status()) {
                case IMPORTED -> imported++;
                case DUPLICATE -> duplicates++;
                case NEEDS_REVIEW -> needsReview++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        return new CvArchiveImportResult(results.size(), imported, duplicates, needsReview, skipped, failed, List.copyOf(results));
    }
}
