package com.wallaceespindola.reportcomposer.api;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ReportTypeDto;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ReportTypeListResponse;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/report-types")
@RequiredArgsConstructor
public class ReportTypeController {

    private final ReportStrategyResolver strategyResolver;

    /** The agreed catalog: every registered ReportTypeStrategy (PRD §7). */
    @GetMapping
    public ReportTypeListResponse reportTypes() {
        return new ReportTypeListResponse(
                Instant.now(),
                strategyResolver.catalog().stream()
                        .map(s -> new ReportTypeDto(s.supports(), s.description()))
                        .toList());
    }
}
