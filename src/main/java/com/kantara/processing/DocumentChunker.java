package com.kantara.processing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DocumentChunker {
    private static final int DEFAULT_TARGET_TOKENS = 900;
    private static final int DEFAULT_OVERLAP_TOKENS = 80;
    private static final int CHARS_PER_TOKEN = 4;

    public List<Map<String, Object>> chunk(List<Map<String, Object>> blocks) {
        return chunk(blocks, DEFAULT_TARGET_TOKENS, DEFAULT_OVERLAP_TOKENS);
    }

    public List<Map<String, Object>> chunk(
            List<Map<String, Object>> blocks,
            int targetTokens,
            int overlapTokens
    ) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        List<String> currentSources = new ArrayList<>();
        int chunkIndex = 1;
        int targetChars = Math.max(400, targetTokens * CHARS_PER_TOKEN);
        int overlapChars = Math.max(0, overlapTokens * CHARS_PER_TOKEN);

        for (Map<String, Object> block : blocks) {
            String text = asString(block.get("text"));
            if (text.isBlank()) {
                continue;
            }

            String source = asString(block.get("source"));
            if (currentText.length() > 0 && currentText.length() + text.length() + 2 > targetChars) {
                chunks.add(createChunk(chunkIndex++, currentText.toString(), currentSources));
                String overlap = tail(currentText.toString(), overlapChars);
                currentText.setLength(0);
                currentSources = new ArrayList<>();
                if (!overlap.isBlank()) {
                    currentText.append(overlap.trim());
                }
            }

            if (currentText.length() > 0) {
                currentText.append("\n\n");
            }
            currentText.append(text.trim());
            if (!source.isBlank() && !currentSources.contains(source)) {
                currentSources.add(source);
            }
        }

        if (currentText.length() > 0) {
            chunks.add(createChunk(chunkIndex, currentText.toString(), currentSources));
        }

        return chunks;
    }

    private Map<String, Object> createChunk(int index, String text, List<String> sources) {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", "chunk-" + index);
        chunk.put("index", index);
        chunk.put("token_estimate", estimateTokens(text));
        chunk.put("sources", List.copyOf(sources));
        chunk.put("text", text.trim());
        return chunk;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    private String tail(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }

        int start = Math.max(0, text.length() - maxChars);
        int paragraph = text.indexOf("\n\n", start);
        if (paragraph >= 0 && paragraph + 2 < text.length()) {
            return text.substring(paragraph + 2);
        }
        return text.substring(start);
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}
