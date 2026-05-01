package com.kantara.formatter;

import java.util.Map;

public interface OutputFormatter {
    String format(Map<String, Object> extractedData, String sourceType);
    String formatName();
}
