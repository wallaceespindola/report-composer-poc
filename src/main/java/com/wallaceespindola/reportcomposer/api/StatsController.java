package com.wallaceespindola.reportcomposer.api;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.StatsResponse;
import com.wallaceespindola.reportcomposer.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** Entity counts, job/work-unit status breakdown, artifact totals, live worker pods. */
    @GetMapping
    public StatsResponse stats() {
        return statsService.stats();
    }
}
