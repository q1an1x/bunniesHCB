package es.buni.hcb.core.events;

public sealed interface EntityEvent
        permits StateChangedEvent {

    String entityId();
    long timestamp();
}
