package es.buni.hcb.core;

import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.EventBus;
import es.buni.hcb.core.events.StateChangedEvent;
import es.buni.hcb.interfaces.homekit.ConfiguredNameStore;
import es.buni.hcb.interfaces.homekit.HomeKitInterface;
import es.buni.hcb.utils.Logger;
import es.buni.hcb.utils.Utils;
import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public abstract class Entity implements HomekitAccessory {

    private final String id;
    private final String iId;
    private final String location;

    private final EntityRegistry registry;

    protected HomekitCharacteristicChangeCallback subscribeCallback;

    public Entity(Adapter adapter, String location, String id) {
        this(adapter.getRegistry(), location, id);
    }

    public Entity(EntityRegistry registry, String location, String id) {
        this.registry = registry;
        this.iId = id;
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
        publishEvent(StateChangedEvent.of(getNamedId(), value));
    }

    public void publishStateChanged(String property, Object value) {
        publishEvent(StateChangedEvent.of(getNamedId(), property, value));
    }

    public String getNamedId() {
        return id;
    }

    public String getIId() {
        return iId;
    }

    public String getLocation() {
        return location;
    }

    public String getType() {
        return getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return getNamedId() + ", " + getType();
    }

    public void initialize() throws Exception {
        Logger.info("Initialized entity " + this);
    };

    public void shutdown() {
    }

    public String getStoredConfiguredName() {
        return ConfiguredNameStore.getDefault().get(getNamedId());
    }

    public void setStoredConfiguredName(String name) throws IOException {
        ConfiguredNameStore.getDefault().set(getNamedId(), name);
    }

    public boolean isHomeKitAccessory() {
        return getPrimaryService() != null;
    }

    // --- Homekit Accessory ---

    @Override
    public int getId() {
        return getNamedId().hashCode() & 0x7FFFFFFF;
    }

    @Override
    public CompletableFuture<String> getName() {
        return getSerialNumber();
    }

    @Override
    public void identify() {
        Logger.warn("HomeKit: identifying " + getNamedId());
    }

    @Override
    public CompletableFuture<String> getSerialNumber() {
        return CompletableFuture.completedFuture(getNamedId());
    }

    @Override
    public CompletableFuture<String> getModel() {
        return CompletableFuture.completedFuture(getType());
    }

    @Override
    public CompletableFuture<String> getManufacturer() {
        return CompletableFuture.completedFuture(HomeKitInterface.HOMEKIT_ENTITY_MANUFACTURER);
    }

    @Override
    public CompletableFuture<String> getFirmwareRevision() {
        return CompletableFuture.completedFuture(Utils.BUILD_DATE);
    }
}
