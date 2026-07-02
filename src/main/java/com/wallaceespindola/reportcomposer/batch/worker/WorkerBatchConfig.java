package com.wallaceespindola.reportcomposer.batch.worker;

import com.wallaceespindola.reportcomposer.batch.BatchMessageSerde;
import com.wallaceespindola.reportcomposer.batch.master.ManagerBatchConfig;
import com.wallaceespindola.reportcomposer.config.AppProperties;
import org.springframework.batch.core.Step;
import org.springframework.batch.integration.config.annotation.EnableBatchIntegration;
import org.springframework.batch.integration.partition.RemotePartitioningWorkerStepBuilderFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Worker side: consumes StepExecutionRequests from the Kafka request topic as part of
 * a consumer group (each partition processed by exactly one worker, FR-4) and executes
 * one remote StepExecution per message. Workers are generic — they resolve the strategy
 * by reportType at runtime, so newly onboarded tenants need no worker change (FR-17).
 */
@Configuration
@EnableBatchIntegration
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class WorkerBatchConfig {

    @Bean
    public DirectChannel workerRequests() {
        return new DirectChannel();
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, byte[]> requestListenerContainer(
            ConsumerFactory<String, byte[]> batchConsumerFactory, AppProperties props) {
        ContainerProperties containerProperties = new ContainerProperties(props.kafka().requestTopic());
        containerProperties.setGroupId(props.kafka().consumerGroup());
        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(batchConsumerFactory, containerProperties);
        // each consumer thread drives exactly one remote StepExecution at a time (PRD §3.2)
        container.setConcurrency(props.kafka().concurrency());
        return container;
    }

    @Bean
    public IntegrationFlow workerInboundFlow(
            ConcurrentMessageListenerContainer<String, byte[]> requestListenerContainer) {
        return IntegrationFlow.from(Kafka.messageDrivenChannelAdapter(requestListenerContainer))
                .transform(byte[].class, BatchMessageSerde::deserialize)
                .channel(workerRequests())
                .get();
    }

    @Bean
    public Step workerStep(
            RemotePartitioningWorkerStepBuilderFactory workerStepBuilderFactory,
            ReportWorkerTasklet reportWorkerTasklet,
            PlatformTransactionManager transactionManager) {
        return workerStepBuilderFactory
                .get(ManagerBatchConfig.WORKER_STEP_NAME)
                .inputChannel(workerRequests())
                .tasklet(reportWorkerTasklet, transactionManager)
                .build();
    }
}
