package com.wallaceespindola.reportcomposer.batch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.springframework.batch.integration.partition.StepExecutionRequest;

/**
 * Java serialization for {@link StepExecutionRequest} over Kafka. The message only
 * carries step/execution ids — workers load the actual ExecutionContext from the shared
 * job repository (H2 TCP server).
 */
public final class BatchMessageSerde {

    private BatchMessageSerde() {}

    public static byte[] serialize(StepExecutionRequest request) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(request);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize StepExecutionRequest", e);
        }
    }

    public static StepExecutionRequest deserialize(byte[] bytes) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (StepExecutionRequest) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to deserialize StepExecutionRequest", e);
        }
    }
}
