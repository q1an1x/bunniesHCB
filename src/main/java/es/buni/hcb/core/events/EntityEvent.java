package es.buni.hcb.core.events;

public sealed interface EntityEvent
        permits StateChangedEvent, SceneRecalledEvent {

    String entityId();
    long timestamp();
}
