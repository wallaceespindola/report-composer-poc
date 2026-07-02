package com.wallaceespindola.reportcomposer.api;

import com.wallaceespindola.reportcomposer.service.ReportDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportDownloadService downloadService;

    @GetMapping("/{workUnitId}")
    public ResponseEntity<InputStreamResource> download(@PathVariable long workUnitId) {
        var download = downloadService.download(workUnitId);
        String objectKey = download.artifact().getObjectKey();
        String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(download.artifact().getContentType()))
                .contentLength(download.artifact().getSizeBytes())
                .body(new InputStreamResource(download.content()));
    }
}
