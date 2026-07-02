package com.wallaceespindola.reportcomposer.repository;

import com.wallaceespindola.reportcomposer.domain.ReportArtifact;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportArtifactRepository extends JpaRepository<ReportArtifact, Long> {

    Optional<ReportArtifact> findByWorkUnitId(Long workUnitId);
}
