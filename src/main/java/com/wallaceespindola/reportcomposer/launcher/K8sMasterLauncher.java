package com.wallaceespindola.reportcomposer.launcher;

import com.wallaceespindola.reportcomposer.config.AppProperties;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Creates the master as a Kubernetes Job through the K8s API (Fabric8) on each job
 * request, injecting the job key as env vars into the templated manifest (PRD §10.1).
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.role}' == 'api' && '${app.launcher}' == 'k8s'")
public class K8sMasterLauncher implements MasterLauncher {

    private final KubernetesClient client;
    private final AppProperties props;

    public K8sMasterLauncher(KubernetesClient client, AppProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public Long launch(String tenantId, String reportType, LocalDate businessDate) {
        createMasterJob(Map.of(
                "JOB_TENANT_ID", tenantId,
                "JOB_REPORT_TYPE", reportType,
                "JOB_BUSINESS_DATE", businessDate.toString()));
        return null; // the master pod creates the JobExecution; report_job carries it once started
    }

    @Override
    public Long restart(long jobExecutionId) {
        createMasterJob(Map.of("RESTART_EXECUTION_ID", String.valueOf(jobExecutionId)));
        return null;
    }

    private void createMasterJob(Map<String, String> envOverrides) {
        Job job = loadTemplate();
        Container container =
                job.getSpec().getTemplate().getSpec().getContainers().get(0);
        List<EnvVar> env = container.getEnv();
        envOverrides.forEach((name, value) -> {
            env.removeIf(e -> e.getName().equals(name));
            env.add(new EnvVar(name, value, null));
        });

        Job created = client.batch().v1().jobs()
                .inNamespace(props.k8s().namespace())
                .resource(job)
                .create();
        log.info("Created master Job {} in namespace {} with env {}",
                created.getMetadata().getName(), props.k8s().namespace(), envOverrides.keySet());
    }

    private Job loadTemplate() {
        String path = props.k8s().masterJobTemplate();
        try {
            InputStream in = Files.exists(Path.of(path))
                    ? new FileInputStream(path)
                    : getClass().getClassLoader().getResourceAsStream(path);
            if (in == null) {
                throw new IllegalStateException("Master Job template not found: " + path);
            }
            try (in) {
                return client.batch().v1().jobs().load(in).item();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load master Job template " + path, e);
        }
    }
}
