package es.buni.hcb.core.events;

public record StateChangedEvent(
        String entityId,
        String property,
        Object value,
        long timestamp
) implements EntityEvent {

    public static StateChangedEvent of(
            String entityId,
            Object value
    ) {
        return of(
                entityId,
                "state",
                value
        );
    }

    public static StateChangedEvent of(
            String entityId,
            String property,
            Object value
    ) {
        return new StateChangedEvent(
                entityId,
                property,
                value,
                System.currentTimeMillis()
        );
    }
}
