package com.wallaceespindola.reportcomposer.batch.master;

import com.wallaceespindola.reportcomposer.batch.BatchMessageSerde;
import com.wallaceespindola.reportcomposer.config.AppProperties;
import com.wallaceespindola.reportcomposer.repository.AccountRepository;
import com.wallaceespindola.reportcomposer.repository.ReportJobRepository;
import com.wallaceespindola.reportcomposer.repository.ReportWorkUnitRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.config.annotation.EnableBatchIntegration;
import org.springframework.batch.integration.partition.RemotePartitioningManagerStepBuilderFactory;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Manager (master) side of the remote partitioning topology. Active in the api role
 * (LAUNCHER_MODE=local runs the manager step in-process) and in the master role
 * (dedicated pod spawned as a Kubernetes Job).
 */
@Configuration
@EnableBatchIntegration
@ConditionalOnExpression("'${app.role}' == 'api' || '${app.role}' == 'master'")
public class ManagerBatchConfig {

    public static final String JOB_NAME = "reportJob";
    public static final String WORKER_STEP_NAME = "workerStep";

    @Bean
    public DirectChannel managerRequests() {
        return new DirectChannel();
    }

    /** Publishes StepExecutionRequests to the Kafka request topic, keyed for spread across partitions. */
    @Bean
    public IntegrationFlow managerOutboundFlow(KafkaTemplate<String, byte[]> batchKafkaTemplate, AppProperties props) {
        return IntegrationFlow.from(managerRequests())
                .handle(message -> {
                    StepExecutionRequest request = (StepExecutionRequest) message.getPayload();
                    batchKafkaTemplate.send(
                            props.kafka().requestTopic(),
                            String.valueOf(request.getStepExecutionId()),
                            BatchMessageSerde.serialize(request));
                })
                .get();
    }

    @Bean
    @StepScope
    public AccountPartitioner accountPartitioner(
            AccountRepository accountRepository,
            ReportJobRepository reportJobRepository,
            ReportWorkUnitRepository workUnitRepository,
            @Value("#{jobParameters['tenantId']}") String tenantId,
            @Value("#{jobParameters['reportType']}") String reportType,
            @Value("#{jobParameters['businessDate']}") String businessDate) {
        return new AccountPartitioner(
                accountRepository, reportJobRepository, workUnitRepository, tenantId, reportType, businessDate);
    }

    @Bean
    public Step managerStep(
            RemotePartitioningManagerStepBuilderFactory managerStepBuilderFactory,
            AccountPartitioner accountPartitioner) {
        // Polling/aggregation variant (PRD §9): workers update the shared job repository,
        // the manager polls it for partition completion — no reply aggregation in memory.
        return managerStepBuilderFactory
                .get("managerStep")
                .partitioner(WORKER_STEP_NAME, accountPartitioner)
                .outputChannel(managerRequests())
                .pollInterval(1000)
                .build();
    }

    @Bean
    public ReportJobExecutionListener reportJobExecutionListener(ReportJobRepository reportJobRepository) {
        return new ReportJobExecutionListener(reportJobRepository);
    }

    @Bean
    public Job reportJob(JobRepository jobRepository, Step managerStep, ReportJobExecutionListener listener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(listener)
                .start(managerStep)
                .build();
    }
}
