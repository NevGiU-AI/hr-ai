package com.nevgiu.hrai.candidate.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class FileSystemOriginalCvStorage implements OriginalCvStorage {

    private static final String QUARANTINE_DIRECTORY = "_quarantine";
    private final Path root;

    public FileSystemOriginalCvStorage(CvStorageProperties properties) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
    }

    @Override
    public QuarantinedCv quarantine(String organizationId, byte[] content) {
        String storageKey = QUARANTINE_DIRECTORY + "/" + sha256(organizationId)
                + "/" + UUID.randomUUID() + ".pdf";
        Path target = resolve(storageKey);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, target);
            }
            return new QuarantinedCv(storageKey);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw new OriginalCvStorageException("Unable to quarantine the original CV", e);
        }
    }

    @Override
    public StoredCv promote(String quarantineKey) {
        String prefix = QUARANTINE_DIRECTORY + "/";
        if (quarantineKey == null || !quarantineKey.startsWith(prefix)) {
            throw new IllegalArgumentException("Storage key is not quarantined");
        }
        Path source = resolve(quarantineKey);
        String storageKey = quarantineKey.substring(prefix.length());
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(source, target);
            }
            return new StoredCv(storageKey, Instant.now());
        } catch (IOException e) {
            throw new OriginalCvStorageException("Unable to promote the quarantined CV", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new OriginalCvStorageException("Unable to delete the original CV", e);
        }
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the configured CV storage root");
        }
        return resolved;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original storage failure.
        }
    }
}
