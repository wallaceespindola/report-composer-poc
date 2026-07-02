package com.wallaceespindola.reportcomposer.repository;

import com.wallaceespindola.reportcomposer.domain.TransactionEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findByAccountIdAndBusinessDateOrderById(String accountId, LocalDate businessDate);
}
