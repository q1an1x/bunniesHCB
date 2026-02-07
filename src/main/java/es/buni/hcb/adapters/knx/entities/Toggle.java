package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;
import io.github.hapjava.accessories.SwitchAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Toggle extends KNXEntity implements SwitchAccessory {
    private final GroupAddress address;
    private volatile boolean isOn;

    private HomekitCharacteristicChangeCallback subscribeCallback;
    private Runnable onToggleListener;

    public Toggle(KNXAdapter adapter, String location, String id,
                  int mainGroup, int middleGroup, int subGroup) {
        super(adapter, location, id);
        this.address = new GroupAddress(mainGroup, middleGroup, subGroup);
    }

    public boolean isOn() {
        return isOn;
    }

    public void setOnToggleListener(Runnable listener) {
        this.onToggleListener = listener;
    }

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(address);
    }

    @Override
    public void initialize() throws Exception {
        try {
            isOn = adapter.communicator().readBool(address);
        } catch (Exception e) {
            isOn = false;
        }
        super.initialize();
    }

    @Override
    protected boolean updateState(GroupAddress addr, ProcessEvent event) throws Exception {
        boolean newState = ProcessListener.asBool(event);
        if (isOn != newState) {
            isOn = newState;
            return true;
        }
        return false;
    }

    @Override
    protected void onStateUpdated(GroupAddress addr, ProcessEvent event) {
        Logger.info("Toggle " + getNamedId() + " set to " + isOn);

        if (subscribeCallback != null) {
            subscribeCallback.changed();
        }
        if (onToggleListener != null) {
            onToggleListener.run();
        }
    }

    @Override
    public CompletableFuture<Boolean> getSwitchState() {
        return CompletableFuture.completedFuture(isOn);
    }

    @Override
    public CompletableFuture<Void> setSwitchState(boolean state) throws Exception {
        if (this.isOn != state) {
            this.isOn = state;
            adapter.communicator().write(address, state);

            if (onToggleListener != null) {
                onToggleListener.run();
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeSwitchState(HomekitCharacteristicChangeCallback callback) {
        this.subscribeCallback = callback;
    }

    @Override
    public void unsubscribeSwitchState() {
        this.subscribeCallback = null;
    }
}