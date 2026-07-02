package com.wallaceespindola.reportcomposer.strategy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Registry of all strategy beans, keyed by reportType. Fails fast on unknown types. */
@Component
public class ReportStrategyResolver {

    private final Map<String, ReportTypeStrategy> byType;

    public ReportStrategyResolver(List<ReportTypeStrategy> strategies) {
        this.byType = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(ReportTypeStrategy::supports, Function.identity()));
    }

    public ReportTypeStrategy resolve(String reportType) {
        ReportTypeStrategy strategy = byType.get(reportType);
        if (strategy == null) {
            throw new UnknownReportTypeException(reportType);
        }
        return strategy;
    }

    public boolean isRegistered(String reportType) {
        return byType.containsKey(reportType);
    }

    /** The agreed catalog of available report types. */
    public List<ReportTypeStrategy> catalog() {
        return byType.values().stream()
                .sorted((a, b) -> a.supports().compareTo(b.supports()))
                .toList();
    }
}
