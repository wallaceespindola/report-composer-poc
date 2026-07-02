package com.wallaceespindola.reportcomposer.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${app.role}' == 'api' && '${app.launcher}' == 'k8s'")
public class K8sClientConfig {

    /** In-cluster config via the API pod's ServiceAccount (RBAC in k8s/03-rbac.yaml). */
    @Bean(destroyMethod = "close")
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
