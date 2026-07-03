package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.api.dto.ApiDtos.ArtifactDto;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import com.wallaceespindola.reportcomposer.domain.ReportWorkUnit;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.storage.ArtifactStorage;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportDownloadService {

    private final ReportArtifactRepository artifactRepository;
    private final ReportWorkUnitRepository workUnitRepository;
    private final ArtifactStorage artifactStorage;

    public record Download(ReportArtifact artifact, InputStream content) {}

    public Download download(long workUnitId) {
        var unit = workUnitRepository.findById(workUnitId)
                .orElseThrow(() -> new NotFoundException("No work unit " + workUnitId));
        if (unit.getStatus() != WorkUnitStatus.COMPLETED) {
            throw new ConflictException("Work unit " + workUnitId + " is not COMPLETED yet");
        }
        ReportArtifact artifact = artifactRepository.findByWorkUnitId(workUnitId)
                .orElseThrow(() -> new NotFoundException("No artifact for work unit " + workUnitId));
        return new Download(artifact, artifactStorage.get(artifact.getObjectKey()));
    }

    /** All generated documents, newest first, optionally filtered. POC scale: capped at 500. */
    public List<ArtifactDto> list(String tenantId, String reportType, LocalDate businessDate) {
        return artifactRepository.findAll().stream()
                .sorted(Comparator.comparing(ReportArtifact::getCreatedAt).reversed())
                .map(artifact -> {
                    ReportWorkUnit unit = workUnitRepository.findById(artifact.getWorkUnitId()).orElse(null);
                    if (unit == null) {
                        return null;
                    }
                    String objectKey = artifact.getObjectKey();
                    return new ArtifactDto(
                            unit.getId(),
                            unit.getTenantId(),
                            unit.getAccountId(),
                            unit.getReportType(),
                            unit.getBusinessDate(),
                            objectKey.substring(objectKey.lastIndexOf('/') + 1),
                            artifact.getContentType(),
                            artifact.getSizeBytes(),
                            artifact.getChecksum(),
                            artifact.getCreatedAt());
                })
                .filter(dto -> dto != null)
                .filter(dto -> tenantId == null || dto.tenantId().equals(tenantId))
                .filter(dto -> reportType == null || dto.reportType().equals(reportType))
                .filter(dto -> businessDate == null || dto.businessDate().equals(businessDate))
                .limit(500)
                .toList();
    }
}
