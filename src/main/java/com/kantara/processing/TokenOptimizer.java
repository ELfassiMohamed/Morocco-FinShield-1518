package com.kantara.processing;

public class TokenOptimizer {
    public String optimize(String content) {
        if (content == null) return "";
        
        return content
            // Collapse multiple newlines into two
            .replaceAll("\\n{3,}", "\n\n")
            // Remove trailing whitespace per line
            .replaceAll("(?m)[ \\t]+$", "")
            // Remove page number boilerplates like "Page 1 of 5"
            .replaceAll("(?i)\\bpage\\s+\\d+\\s+of\\s+\\d+\\b", "")
            // Strip multiple spaces
            .replaceAll("[ \\t]{2,}", " ")
            .trim();
    }
}
