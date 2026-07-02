package com.wallaceespindola.reportcomposer.repository;

import com.wallaceespindola.reportcomposer.domain.Tenant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    List<Tenant> findByEnabledTrue();
}
