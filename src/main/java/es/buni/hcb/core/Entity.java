package es.buni.hcb.core;

import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.EventBus;
import es.buni.hcb.core.events.StateChangedEvent;
import es.buni.hcb.utils.Logger;

public abstract class Entity {

    private final String id;
    private final String location;

    private final EntityRegistry registry;

    public Entity(Adapter adapter, String location, String id) {
        this.registry = adapter.getRegistry();
        this.id = location + '.' + id;
        this.location = location;
    }

    public EntityRegistry getRegistry() {
        return registry;
    }

    public EventBus getEventBus() {
        return registry.getEventBus();
    }

    public void publishEvent(EntityEvent event) {
        getEventBus().publish(event);
    }

    public void publishStateChanged(Object value) {
        publishEvent(StateChangedEvent.of(getId(), value));
    }

    public void publishStateChanged(String property, Object value) {
        publishEvent(StateChangedEvent.of(getId(), property, value));
    }

    public String getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return getId() + ", " + getClass().getSimpleName();
    }

    public void initialize() throws Exception {
        Logger.info("Initialized entity " + this);
    };

    public void shutdown() {
    }
}
