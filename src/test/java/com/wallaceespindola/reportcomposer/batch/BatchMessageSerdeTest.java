package com.wallaceespindola.reportcomposer.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.batch.integration.partition.StepExecutionRequest;

class BatchMessageSerdeTest {

    @Test
    void roundTripsStepExecutionRequest() {
        StepExecutionRequest original = new StepExecutionRequest("workerStep", 42L, 7L);

        StepExecutionRequest copy = BatchMessageSerde.deserialize(BatchMessageSerde.serialize(original));

        assertThat(copy.getStepName()).isEqualTo("workerStep");
        assertThat(copy.getJobExecutionId()).isEqualTo(42L);
        assertThat(copy.getStepExecutionId()).isEqualTo(7L);
    }

    @Test
    void garbageBytesFailFast() {
        assertThatThrownBy(() -> BatchMessageSerde.deserialize(new byte[] {1, 2, 3}))
                .isInstanceOf(IllegalStateException.class);
    }
}
