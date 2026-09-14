package es.buni.hcb.adapters.knx;

import io.calimero.process.ProcessEvent;
import java.util.function.Consumer;

@FunctionalInterface
public interface KnxConnectionFactory {
    KnxConnection connect(Consumer<ProcessEvent> receiver, Runnable disconnected) throws Exception;
}
