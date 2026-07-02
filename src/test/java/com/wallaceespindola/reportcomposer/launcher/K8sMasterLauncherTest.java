package com.wallaceespindola.reportcomposer.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallaceespindola.reportcomposer.TestFixtures;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Fabric8 mock server test for the API-spawns-master flow (PRD §10.1, FR-16). */
@EnableKubernetesMockClient(crud = true)
class K8sMasterLauncherTest {

    KubernetesClient client;

    private K8sMasterLauncher launcher() {
        return new K8sMasterLauncher(client, TestFixtures.appProperties("api", "k8s"));
    }

    private Map<String, String> envOfCreatedJob() {
        List<Job> jobs = client.batch().v1().jobs().inNamespace("report-composer").list().getItems();
        assertThat(jobs).hasSize(1);
        return jobs.get(0).getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().stream()
                .collect(Collectors.toMap(EnvVar::getName, e -> e.getValue() == null ? "" : e.getValue()));
    }

    @Test
    void launchCreatesMasterJobWithJobKeyEnv() {
        Long executionId = launcher().launch("BE", "ACCOUNT_STATEMENT", TestFixtures.BUSINESS_DATE);

        assertThat(executionId).isNull(); // execution id is created by the master pod
        Map<String, String> env = envOfCreatedJob();
        assertThat(env)
                .containsEntry("APP_ROLE", "master")
                .containsEntry("JOB_TENANT_ID", "BE")
                .containsEntry("JOB_REPORT_TYPE", "ACCOUNT_STATEMENT")
                .containsEntry("JOB_BUSINESS_DATE", "2026-06-30");
    }

    @Test
    void restartCreatesMasterJobWithRestartExecutionId() {
        launcher().restart(77L);

        Map<String, String> env = envOfCreatedJob();
        assertThat(env).containsEntry("RESTART_EXECUTION_ID", "77");
        assertThat(env).containsEntry("APP_ROLE", "master");
    }
}
