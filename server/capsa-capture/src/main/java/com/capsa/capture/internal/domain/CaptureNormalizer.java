package com.capsa.capture.internal.domain;

public final class CaptureNormalizer {

    private CaptureNormalizer() {}

    public static String normalize(String content) {
        if (content == null) return "";
        return content.strip().toLowerCase().replaceAll("\\s+", " ");
    }
}
