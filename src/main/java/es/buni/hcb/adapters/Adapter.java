package es.buni.hcb.adapters;

import es.buni.hcb.core.Entity;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.utils.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Adapter {

    protected final EntityRegistry registry;

    private final Map<String, Entity> entities;
    private final String name;

    public Adapter(String name, EntityRegistry registry) {
        this.name = name;
        this.registry = registry;
        this.entities = new ConcurrentHashMap<>();
    }

    public EntityRegistry getRegistry() {
        return registry;
    }

    public String getName() {
        return name;
    }

    public Collection<Entity> entities() {
        return Collections.unmodifiableCollection(entities.values());
    }

    public void register(Entity entity) {
        if (entities.containsKey(entity.getNamedId())) {
            throw new IllegalStateException("Duplicate entity id: " + entity.getNamedId());
        }

        entities.put(entity.getNamedId(), entity);
        registry.register(entity);
    }

    public void unregister(Entity entity) {
        entities.remove(entity.getNamedId());
        registry.unregister(entity);
    }

    public void start() throws Exception {
        for (Entity entity : entities.values()) {
            try {
                entity.initialize();
            } catch (Exception e) {
                Logger.error("Entity initialization failed: " + entity.getNamedId(), e);
            }
        }
    }

    public void stop() throws Exception {
        for (Entity entity : entities.values()) {
            try {
                entity.shutdown();
                unregister(entity);
            } catch (Exception e) {
                Logger.error("Entity shutdown failed: " + entity.getNamedId(), e);
            }
        }
    }
}
