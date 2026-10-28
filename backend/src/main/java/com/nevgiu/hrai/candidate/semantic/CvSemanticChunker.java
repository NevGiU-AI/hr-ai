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

    List<CvSemanticChunk> chunk(CvSemanticSource source) {
        List<CvSemanticChunk> chunks = new ArrayList<>();
        for (CvSemanticTextUnit unit : source.textUnits()) {
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
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
