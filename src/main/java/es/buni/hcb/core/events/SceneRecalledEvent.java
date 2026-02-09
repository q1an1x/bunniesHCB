package es.buni.hcb.core.events;

public record SceneRecalledEvent(String entityId, long timestamp, int sceneId)
        implements EntityEvent {
    public static SceneRecalledEvent of(String entityId, int sceneId) {
        return new SceneRecalledEvent(entityId, System.currentTimeMillis(), sceneId);
    }
}
