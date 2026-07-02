package com.wallaceespindola.reportcomposer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String locale;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private boolean enabled;

    @Lob
    @Column(name = "config_json")
    private String configJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
