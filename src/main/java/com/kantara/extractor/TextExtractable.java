package com.kantara.extractor;

import java.util.List;
import java.io.InputStream;

public interface TextExtractable {
    String extractText(String filePath);
    String extractText(InputStream stream);
    List<String> extractSections(String text);
}
