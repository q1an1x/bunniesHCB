package es.buni.hcb.core;

import es.buni.hcb.core.events.EventBus;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EntityRegistry {
    private final Map<String, Entity> entities;

    private final EventBus eventBus = new EventBus();

    public EventBus getEventBus() {
        return eventBus;
    }

    public EntityRegistry() {
        this.entities = new ConcurrentHashMap<>();
    }

    public void register(Entity entity) {
        if (entities.containsKey(entity.getId())) {
            throw new IllegalStateException("Duplicate entity id: " + entity.getId());
        }
        entities.put(entity.getId(), entity);
    }

    public void unregister(Entity entity) {
        remove(entity.getId());
    }

    public Entity get(String id) {
        return entities.get(id);
    }

    public boolean has(String id) {
        return entities.containsKey(id);
    }

    private void add(Entity entity) {
        entities.put(entity.getId(), entity);
    }

    private void remove(String id) {
        entities.remove(id);
    }

    public Collection<Entity> getAllEntities() {
        return Collections.unmodifiableCollection(entities.values());
    }

    public Collection<Entity> getEntitiesFrom(String location) {
        return entities.values().stream()
                .filter(e -> e.getLocation().equals(location))
                .toList();
    }
}
