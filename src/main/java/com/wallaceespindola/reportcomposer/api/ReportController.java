package com.wallaceespindola.reportcomposer.api;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ArtifactListResponse;
import com.wallaceespindola.reportcomposer.service.ReportDownloadService;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportDownloadService downloadService;

    /** List generated documents (newest first), optionally filtered. */
    @GetMapping
    public ArtifactListResponse list(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return new ArtifactListResponse(Instant.now(), downloadService.list(tenantId, reportType, businessDate));
    }

    /** Download (attachment) or preview (inline=true) a generated report. */
    @GetMapping("/{workUnitId}")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable long workUnitId, @RequestParam(defaultValue = "false") boolean inline) {
        var download = downloadService.download(workUnitId);
        String objectKey = download.artifact().getObjectKey();
        String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        String disposition = (inline ? "inline" : "attachment") + "; filename=\"" + fileName + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(download.artifact().getContentType()))
                .contentLength(download.artifact().getSizeBytes())
                .body(new InputStreamResource(download.content()));
    }
}
