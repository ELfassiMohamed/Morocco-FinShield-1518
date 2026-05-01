package com.kantara.extractor;

import java.util.List;
import java.util.Map;
import java.io.InputStream;

public interface Extractable {
    List<Map<String, String>> extract(String filePath);
    List<Map<String, String>> extract(InputStream stream, String fileName);
}
