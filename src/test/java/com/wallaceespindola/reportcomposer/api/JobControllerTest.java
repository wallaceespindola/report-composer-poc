package com.wallaceespindola.reportcomposer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.api.dto.JobSummaryDto;
import com.wallaceespindola.reportcomposer.api.dto.PartitionDto;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.service.JobService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JobService jobService;

    private static final String VALID_BODY =
            "{\"tenantId\":\"BE\",\"reportType\":\"ACCOUNT_STATEMENT\",\"businessDate\":\"2026-06-30\"}";

    @Test
    void startReturns202WithExecutionIdAndTimestamp() throws Exception {
        when(jobService.start("BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE)).thenReturn(42L);

        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(42))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void startRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"ACCOUNT_STATEMENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void startRejectsMalformedDate() throws Exception {
        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"BE\",\"reportType\":\"X\",\"businessDate\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateInFlightJobIs409() throws Exception {
        when(jobService.start(any(), any(), any())).thenThrow(new ConflictException("in flight"));
        mockMvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("in flight"));
    }

    @Test
    void getReturnsJobDetail() throws Exception {
        when(jobService.get(42L)).thenReturn(summary());
        mockMvc.perform(get("/api/v1/jobs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.tenantId").value("BE"))
                .andExpect(jsonPath("$.job.partitionsTotal").value(50));
    }

    @Test
    void unknownJobIs404() throws Exception {
        when(jobService.get(99L)).thenThrow(new NotFoundException("nope"));
        mockMvc.perform(get("/api/v1/jobs/99")).andExpect(status().isNotFound());
    }

    @Test
    void listPassesFilters() throws Exception {
        when(jobService.list(eq("BE"), isNull(), isNull(), eq("COMPLETED"))).thenReturn(List.of(summary()));
        mockMvc.perform(get("/api/v1/jobs").param("tenantId", "BE").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].reportType").value("ACCOUNT_STATEMENT"));
    }

    @Test
    void partitionsReturnsPerAccountStatus() throws Exception {
        when(jobService.partitions(42L))
                .thenReturn(List.of(new PartitionDto(11L, "BE-ACC-0001", "COMPLETED", 1, "key")));
        mockMvc.perform(get("/api/v1/jobs/42/partitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partitions[0].accountId").value("BE-ACC-0001"));
    }

    @Test
    void restartReturns202() throws Exception {
        when(jobService.restart(42L)).thenReturn(43L);
        mockMvc.perform(post("/api/v1/jobs/42/restart"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(43))
                .andExpect(jsonPath("$.status").value("RESTARTING"));
    }

    private static JobSummaryDto summary() {
        return new JobSummaryDto(
                42L, "BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE, "COMPLETED",
                Instant.now(), Instant.now(), 50, 50, 0);
    }
}
