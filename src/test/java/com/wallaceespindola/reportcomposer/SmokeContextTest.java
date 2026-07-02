package com.wallaceespindola.reportcomposer;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallaceespindola.reportcomposer.launcher.LocalMasterLauncher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Role-based wiring smoke tests: the full Spring context must start for each APP_ROLE
 * without external infrastructure (in-memory H2; Kafka/MinIO clients connect lazily).
 */
class SmokeContextTest {

    @Nested
    @SpringBootTest(properties = {
        "app.role=api", "app.launcher=local", "app.seed.enabled=true",
        "spring.kafka.admin.properties.default.api.timeout.ms=1000",
        "spring.kafka.admin.properties.request.timeout.ms=1000"
    })
    class ApiRole {

        @Autowired private ApplicationContext context;

        @Test
        void managerTopologyAndLocalLauncherArePresentAndDataIsSeeded() {
            assertThat(context.getBean("reportJob", Job.class)).isNotNull();
            assertThat(context.getBean("managerStep", Step.class)).isNotNull();
            assertThat(context.getBean(LocalMasterLauncher.class)).isNotNull();
            assertThat(context.containsBean("workerStep")).isFalse();
            // auto-seed ran against the in-memory DB (3 tenants x 5 accounts in test profile)
            assertThat(context.getBean(com.wallaceespindola.reportcomposer.repository.AccountRepository.class)
                            .count())
                    .isGreaterThan(0);
        }
    }

    @Nested
    @SpringBootTest(properties = {
        "app.role=worker", "app.seed.enabled=false",
        "spring.kafka.admin.properties.default.api.timeout.ms=1000",
        "spring.kafka.admin.properties.request.timeout.ms=1000",
        "spring.kafka.consumer.properties.default.api.timeout.ms=1000"
    })
    class WorkerRole {

        @Autowired private ApplicationContext context;

        @Test
        void workerStepIsPresentAndManagerJobIsNot() {
            assertThat(context.getBean("workerStep", Step.class)).isNotNull();
            assertThat(context.containsBean("reportJob")).isFalse();
            assertThat(context.containsBean("managerStep")).isFalse();
        }
    }
}
