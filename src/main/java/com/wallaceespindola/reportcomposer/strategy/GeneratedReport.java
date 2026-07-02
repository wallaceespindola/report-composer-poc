package com.wallaceespindola.reportcomposer.strategy;

import java.nio.charset.StandardCharsets;

/** The rendered report produced by a {@link ReportTypeStrategy}. */
public record GeneratedReport(String fileName, String contentType, byte[] content) {

    public static GeneratedReport text(String fileName, String body) {
        return new GeneratedReport(fileName, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }
}
