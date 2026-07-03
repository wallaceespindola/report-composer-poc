package com.wallaceespindola.reportcomposer.repository;

import com.wallaceespindola.reportcomposer.domain.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findByTenantIdAndEligibleTrueOrderByAccountId(String tenantId);

    long countByTenantId(String tenantId);
}
