package es.buni.hcb.core.events;

public record StateChangedEvent(
        String entityId,
        String property,
        Object value,
        long timestamp,
        Origin origin
) implements EntityEvent {

    public enum Origin { LOCAL, AUTOMATION, MODE, BUS_WRITE, BUS_RESPONSE, BUS_ECHO }

    public static StateChangedEvent fromBus(String id, String property, Object value, boolean response) {
        return new StateChangedEvent(id, property, value, System.currentTimeMillis(),
                response ? Origin.BUS_RESPONSE : Origin.BUS_WRITE);
    }

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
                System.currentTimeMillis(),
                Origin.LOCAL
        );
    }
}
