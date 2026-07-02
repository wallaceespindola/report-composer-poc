package com.wallaceespindola.reportcomposer.strategy;

public class UnknownReportTypeException extends RuntimeException {

    public UnknownReportTypeException(String reportType) {
        super("No registered strategy for report type '" + reportType + "'");
    }
}
