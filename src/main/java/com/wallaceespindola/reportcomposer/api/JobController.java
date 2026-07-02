package com.wallaceespindola.reportcomposer.api;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.JobDetailResponse;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.JobListResponse;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.JobRestartResponse;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.JobStartRequest;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.JobStartResponse;
import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.PartitionListResponse;
import com.wallaceespindola.reportcomposer.service.JobService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobStartResponse> start(@Valid @RequestBody JobStartRequest request) {
        Long executionId = jobService.start(request.tenantId(), request.reportType(), request.businessDate());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new JobStartResponse(Instant.now(), executionId, "ACCEPTED"));
    }

    @GetMapping
    public JobListResponse list(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @RequestParam(required = false) String status) {
        return new JobListResponse(Instant.now(), jobService.list(tenantId, reportType, businessDate, status));
    }

    @GetMapping("/{jobExecutionId}")
    public JobDetailResponse get(@PathVariable long jobExecutionId) {
        return new JobDetailResponse(Instant.now(), jobService.get(jobExecutionId));
    }

    @GetMapping("/{jobExecutionId}/partitions")
    public PartitionListResponse partitions(@PathVariable long jobExecutionId) {
        return new PartitionListResponse(Instant.now(), jobService.partitions(jobExecutionId));
    }

    @PostMapping("/{jobExecutionId}/restart")
    public ResponseEntity<JobRestartResponse> restart(@PathVariable long jobExecutionId) {
        Long newExecutionId = jobService.restart(jobExecutionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new JobRestartResponse(Instant.now(), newExecutionId, "RESTARTING"));
    }
}
