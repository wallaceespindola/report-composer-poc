package com.wallaceespindola.reportcomposer.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.StatsResponse;
import com.wallaceespindola.reportcomposer.service.StatsService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private StatsService statsService;

    @Test
    void exposesCountsAndActiveWorkers() throws Exception {
        when(statsService.stats()).thenReturn(new StatsResponse(
                Instant.now(), 3, 6, 150, 800,
                Map.of("COMPLETED", 2L), Map.of("COMPLETED", 100L),
                100, 54321, 3, 6));

        mockMvc.perform(get("/api/v1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.tenants").value(3))
                .andExpect(jsonPath("$.jobsByStatus.COMPLETED").value(2))
                .andExpect(jsonPath("$.artifactBytes").value(54321))
                .andExpect(jsonPath("$.activeWorkerPods").value(3))
                .andExpect(jsonPath("$.workerConsumerThreads").value(6));
    }
}
