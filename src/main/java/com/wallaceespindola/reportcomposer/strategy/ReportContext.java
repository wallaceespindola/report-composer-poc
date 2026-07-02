package com.wallaceespindola.reportcomposer.strategy;

import com.wallaceespindola.reportcomposer.domain.Tenant;
import java.time.LocalDate;

/** Everything a strategy needs to render one report. */
public record ReportContext(Tenant tenant, String accountId, LocalDate businessDate, String contractParamsJson) {}
