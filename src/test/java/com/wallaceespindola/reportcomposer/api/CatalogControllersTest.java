package com.wallaceespindola.reportcomposer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallaceespindola.reportcomposer.TestFixtures;
import com.wallaceespindola.reportcomposer.api.exception.ConflictException;
import com.wallaceespindola.reportcomposer.repository.TenantReportContractRepository;
import com.wallaceespindola.reportcomposer.repository.TenantRepository;
import com.wallaceespindola.reportcomposer.service.AdminService;
import com.wallaceespindola.reportcomposer.strategy.AccountStatementStrategy;
import com.wallaceespindola.reportcomposer.strategy.ReportStrategyResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({TenantController.class, ReportTypeController.class, HealthController.class})
class CatalogControllersTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TenantRepository tenantRepository;
    @MockitoBean private TenantReportContractRepository contractRepository;
    @MockitoBean private ReportStrategyResolver strategyResolver;
    @MockitoBean private AdminService adminService;

    @Test
    void tenantsExposeOnlyContractedAndRegisteredReportTypes() throws Exception {
        when(tenantRepository.findAll()).thenReturn(List.of(TestFixtures.tenantBE()));
        when(contractRepository.findByTenantIdAndEnabledTrue("BE"))
                .thenReturn(List.of(
                        TestFixtures.contract("BE", "ACCOUNT_STATEMENT"),
                        TestFixtures.contract("BE", "NOT_IN_CATALOG")));
        when(strategyResolver.isRegistered("ACCOUNT_STATEMENT")).thenReturn(true);
        when(strategyResolver.isRegistered("NOT_IN_CATALOG")).thenReturn(false);

        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.tenants[0].tenantId").value("BE"))
                .andExpect(jsonPath("$.tenants[0].reportTypes.length()").value(1))
                .andExpect(jsonPath("$.tenants[0].reportTypes[0]").value("ACCOUNT_STATEMENT"));
    }

    @Test
    void reportTypesListTheAgreedCatalog() throws Exception {
        when(strategyResolver.catalog()).thenReturn(List.of(new AccountStatementStrategy()));
        mockMvc.perform(get("/api/v1/report-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportTypes[0].code").value("ACCOUNT_STATEMENT"))
                .andExpect(jsonPath("$.reportTypes[0].description").isNotEmpty());
    }

    @Test
    void createTenantReturns201WithSeedCounts() throws Exception {
        when(adminService.createTenant(any())).thenReturn(new AdminService.TenantCreated("NL", 25, 130));

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"NL\",\"countryCode\":\"NL\",\"locale\":\"nl-NL\","
                                + "\"currency\":\"EUR\",\"seedAccounts\":25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("NL"))
                .andExpect(jsonPath("$.accountsCreated").value(25))
                .andExpect(jsonPath("$.transactionsCreated").value(130));
    }

    @Test
    void createTenantValidatesRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"NL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateTenantIs409() throws Exception {
        when(adminService.createTenant(any())).thenThrow(new ConflictException("Tenant 'BE' already exists"));
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"BE\",\"countryCode\":\"BE\",\"locale\":\"nl-BE\","
                                + "\"currency\":\"EUR\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createContractReturns201() throws Exception {
        when(adminService.createContract(eq("NL"), any()))
                .thenReturn(TestFixtures.contract("NL", "TAX_SUMMARY"));

        mockMvc.perform(post("/api/v1/tenants/NL/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"TAX_SUMMARY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("NL"))
                .andExpect(jsonPath("$.reportType").value("TAX_SUMMARY"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void generateTransactionsReturns201WithCounts() throws Exception {
        when(adminService.generateTransactions(eq("BE"), any()))
                .thenReturn(new AdminService.TransactionsGenerated(50, 260));

        mockMvc.perform(post("/api/v1/tenants/BE/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"2026-07-01\",\"perAccount\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accounts").value(50))
                .andExpect(jsonPath("$.transactionsCreated").value(260));
    }

    @Test
    void healthIsUpWithTimestamp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
