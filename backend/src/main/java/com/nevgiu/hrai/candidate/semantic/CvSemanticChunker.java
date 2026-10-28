package com.nevgiu.hrai.candidate.semantic;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Creates deterministic, overlapping chunks without mixing text from separate OCR pages. */
@Component
class CvSemanticChunker {
    private final CvSemanticIndexProperties properties;

    CvSemanticChunker(CvSemanticIndexProperties properties) {
        this.properties = properties;
    }

    /**
     * Splits the immutable extraction snapshot into bounded chunks in stable document/page order.
     * Stable ordering is important because chunk indexes are used to derive repeatable vector-store IDs.
     */
    List<CvSemanticChunk> chunk(CvSemanticSource source) {
        List<CvSemanticChunk> chunks = new ArrayList<>();
        for (CvSemanticTextUnit unit : source.textUnits()) {
            // Process each OCR page independently so one retrieved chunk always maps to one reviewer-visible page.
            String text = normalize(unit.text());
            int start = 0;
            while (start < text.length() && chunks.size() < properties.maxChunksPerDocument()) {
                int proposedEnd = Math.min(start + properties.chunkCharacters(), text.length());
                int end = boundary(text, start, proposedEnd);
                String content = text.substring(start, end).trim();
                if (!content.isEmpty()) {
                    chunks.add(new CvSemanticChunk(chunks.size(), unit.pageNumber(), content));
                }
                if (end == text.length()) break;
                // Overlap preserves meaning that spans a boundary while guaranteeing forward progress.
                start = Math.max(start + 1, end - properties.overlapCharacters());
            }
            // The limit covers the complete CV, not each page, to bound provider cost for unusually long files.
            if (chunks.size() == properties.maxChunksPerDocument()) break;
        }
        return List.copyOf(chunks);
    }

    private int boundary(String text, int start, int proposedEnd) {
        if (proposedEnd == text.length()) return proposedEnd;
        int whitespace = text.lastIndexOf(' ', proposedEnd);
        // Avoid tiny chunks when a long token or OCR artifact contains no useful nearby boundary.
        return whitespace > start + properties.chunkCharacters() / 2 ? whitespace : proposedEnd;
    }

    private String normalize(String text) {
        // OCR commonly emits repeated newlines/tabs; normalization makes retries produce identical chunk boundaries.
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
