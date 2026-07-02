package com.wallaceespindola.reportcomposer.service;

import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.api.exception.NotFoundException;
import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import com.wallaceespindola.reportcomposer.domain.WorkUnitStatus;
import com.wallaceespindola.reportcomposer.repository.ReportArtifactRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import com.wallaceespindola.reportcomposer.storage.ArtifactStorage;
import java.io.InputStream;
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
}
