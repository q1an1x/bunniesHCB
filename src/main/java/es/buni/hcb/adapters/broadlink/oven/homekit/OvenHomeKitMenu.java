package es.buni.hcb.adapters.broadlink.oven.homekit;

import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import es.buni.hcb.adapters.broadlink.oven.OvenMode;
import io.github.hapjava.accessories.TelevisionAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.television.RemoteKeyEnum;
import io.github.hapjava.characteristics.impl.television.SleepDiscoveryModeEnum;
import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.InputSourceService;
import io.github.hapjava.services.impl.TelevisionService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OvenHomeKitMenu extends OvenHomeKitComponent implements
        TelevisionAccessory {
    protected HomekitCharacteristicChangeCallback ovenStateCallback;
    protected HomekitCharacteristicChangeCallback ovenModeCallback;

    public OvenHomeKitMenu(BSHOven oven) {
        super(oven, "menu");
    }

    @Override
    public CompletableFuture<Boolean> isActive() {
        return CompletableFuture.completedFuture(
                ! oven.getState().isStandingBy()
        );
    }

    @Override
    public CompletableFuture<Void> setActive(boolean state) throws Exception {
        if (state) {
            oven.on();
        } else {
            oven.off();
        }
        return CompletableFuture.completedFuture(null);
    }

    public HomekitCharacteristicChangeCallback getOvenStateCallback() {
        return ovenStateCallback;
    }

    @Override
    public void subscribeActive(HomekitCharacteristicChangeCallback callback) {
        ovenStateCallback = callback;
    }

    @Override
    public void unsubscribeActive() {
        ovenStateCallback = null;
    }

    @Override
    public CompletableFuture<Integer> getActiveIdentifier() {
        int mode = 1;
        if (oven.getMode() != null) {
            if (oven.getMode() != OvenMode.NONE) {
                mode =  oven.getMode().ordinal();
            }
        }

        return CompletableFuture.completedFuture(
                mode
        );
    }

    @Override
    public CompletableFuture<Void> setActiveIdentifier(Integer identifier) throws Exception {
        oven.setMode(OvenMode.values()[identifier]);
        return CompletableFuture.completedFuture(null);
    }

    public HomekitCharacteristicChangeCallback getOvenModeCallback() {
        return ovenModeCallback;
    }

    @Override
    public void subscribeActiveIdentifier(HomekitCharacteristicChangeCallback callback) {
        ovenModeCallback = callback;
    }

    @Override
    public void unsubscribeActiveIdentifier() {
        ovenModeCallback = null;
    }

    @Override
    public CompletableFuture<String> getConfiguredName() {
        return CompletableFuture.completedFuture(
                getStoredConfiguredName()
        );
    }

    @Override
    public CompletableFuture<Void> setConfiguredName(String name) throws Exception {
        setStoredConfiguredName(name);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeConfiguredName(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeConfiguredName() {
    }

    @Override
    public CompletableFuture<Void> setRemoteKey(RemoteKeyEnum key) throws Exception {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<SleepDiscoveryModeEnum> getSleepDiscoveryMode() {
        return CompletableFuture.completedFuture(SleepDiscoveryModeEnum.ALWAYS_DISCOVERABLE);
    }

    @Override
    public void subscribeSleepDiscoveryMode(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeSleepDiscoveryMode() {
    }

    @Override
    public Collection<Service> getServices() {
        List<Service> services = new ArrayList<>();

        TelevisionService televisionService = new TelevisionService(this);
        services.add(televisionService);

        for (OvenMode mode : OvenMode.values()) {
            if (mode == OvenMode.NONE) continue;

            InputSourceService inputSourceService =
                    new InputSourceService(new OvenHomeKitMenuItem(oven, mode));

            televisionService.addLinkedService(inputSourceService);
        }
        return services;
    }

}
