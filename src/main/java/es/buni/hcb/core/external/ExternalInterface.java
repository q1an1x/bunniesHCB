package es.buni.hcb.core.external;

import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.core.events.EventBus;

public abstract class ExternalInterface {
    protected final EntityRegistry registry;
    protected final EventBus eventBus;

    public ExternalInterface(EntityRegistry registry) {
        this.registry = registry;
        this.eventBus = registry.getEventBus();
    }

    public abstract void start() throws Exception;
    public abstract void stop() throws Exception;

    public String getName() {
        return getClass().getSimpleName();
    }
}
