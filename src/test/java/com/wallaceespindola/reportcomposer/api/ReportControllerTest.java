package com.wallaceespindola.reportcomposer.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import com.wallaceespindola.reportcomposer.service.ReportDownloadService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReportDownloadService downloadService;

    @Test
    void downloadsArtifactWithAttachmentHeaders() throws Exception {
        byte[] body = "REPORT BODY".getBytes(StandardCharsets.UTF_8);
        ReportArtifact artifact = ReportArtifact.builder()
                .workUnitId(11L)
                .objectKey("BE/ACCOUNT_STATEMENT/2026-06-30/BE_BE-ACC-0001_2026-06-30_statement.txt")
                .contentType("text/plain; charset=utf-8")
                .sizeBytes(body.length)
                .build();
        when(downloadService.download(11L))
                .thenReturn(new ReportDownloadService.Download(artifact, new ByteArrayInputStream(body)));

        mockMvc.perform(get("/api/v1/reports/11"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"BE_BE-ACC-0001_2026-06-30_statement.txt\""))
                .andExpect(content().string("REPORT BODY"));
    }

    @Test
    void missingArtifactIs404() throws Exception {
        when(downloadService.download(99L)).thenThrow(new NotFoundException("no artifact"));
        mockMvc.perform(get("/api/v1/reports/99")).andExpect(status().isNotFound());
    }

    @Test
    void inlineParamSwitchesContentDisposition() throws Exception {
        byte[] body = "X".getBytes(StandardCharsets.UTF_8);
        ReportArtifact artifact = ReportArtifact.builder()
                .workUnitId(11L)
                .objectKey("BE/TAX_SUMMARY/2026-06-30/file.txt")
                .contentType("text/plain")
                .sizeBytes(body.length)
                .build();
        when(downloadService.download(11L))
                .thenReturn(new ReportDownloadService.Download(artifact, new ByteArrayInputStream(body)));

        mockMvc.perform(get("/api/v1/reports/11").param("inline", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"file.txt\""));
    }

    @Test
    void listReturnsArtifactsWithTimestamp() throws Exception {
        when(downloadService.list("BE", null, null))
                .thenReturn(java.util.List.of(new com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ArtifactDto(
                        11L, "BE", "BE-ACC-0001", "ACCOUNT_STATEMENT",
                        java.time.LocalDate.parse("2026-06-30"), "file.txt", "text/plain",
                        123L, "abc", java.time.Instant.now())));

        mockMvc.perform(get("/api/v1/reports").param("tenantId", "BE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.artifacts[0].workUnitId").value(11))
                .andExpect(jsonPath("$.artifacts[0].fileName").value("file.txt"));
    }
}
