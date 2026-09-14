package es.buni.hcb.adapters.knx.entities.lighting;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.datapoint.StateDP;
import io.calimero.dptxlator.DPTXlator2ByteUnsigned;
import io.calimero.process.ProcessEvent;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithColorTemperature;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Tunable extends Dimmable implements AccessoryWithColorTemperature {
    public static final int COLOR_TEMPERATURE_MIN_KELVIN = 2702;
    public static final int COLOR_TEMPERATURE_MAX_KELVIN = 6535;

    protected final GroupAddress colorTemperatureValueAddress;
    protected final GroupAddress statusColorTemperatureAddress;

    private final StateDP statusColorTemperatureDP;
    private final StateDP colorTemperatureValueDP;

    private volatile int colorTemperature;

    private HomekitCharacteristicChangeCallback colorTemperatureCallback;

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                switchAddress,
                statusSwitchAddress,
                dimmingValueAddress,
                statusDimmingValueAddress,
                dimmingValueTimeAddress,
                colorTemperatureValueAddress,
                statusColorTemperatureAddress
        );
    }

    @Override
    public java.util.List<es.buni.hcb.adapters.knx.KnxBinding> bindings() {
        var result = new java.util.ArrayList<>(super.bindings());
        result.add(binding("colorTemperature", colorTemperatureValueAddress, "7.600", es.buni.hcb.adapters.knx.KnxBinding.Role.COMMAND));
        result.add(binding("colorTemperatureStatus", statusColorTemperatureAddress, "7.600", es.buni.hcb.adapters.knx.KnxBinding.Role.STATUS));
        return java.util.List.copyOf(result);
    }

    public int getColorTemperatureValue() {
        return colorTemperature;
    }

    public void setColorTemperatureValue(int colorTemperature) throws Exception {
        if (colorTemperature < COLOR_TEMPERATURE_MIN_KELVIN || colorTemperature > COLOR_TEMPERATURE_MAX_KELVIN)
            throw new IllegalArgumentException("Color temperature outside lamp range");
        writeColorTemperature(colorTemperature);
    }

    public static Tunable fromConvention(KNXAdapter adapter, String location, String id,
                                          int switchMainGroup, int switchMiddleGroup, int switchSubGroup)
            throws Exception {
        return new Tunable(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 3,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 1,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 4,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 7,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 2,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 5
        );
    }

    public Tunable(KNXAdapter adapter, String location, String id,
                    int switchMainGroup, int switchMiddleGroup, int switchSubGroup,
                    int statusSwitchMainGroup, int statusSwitchMiddleGroup, int statusSwitchSubGroup,
                    int dimmingValueMainGroup, int dimmingValueMiddleGroup, int dimmingValueSubGroup,
                    int statusDimmingValueMainGroup, int statusDimmingValueMiddleGroup, int statusDimmingValueSubGroup,
                    int dimmingValueTimeMainGroup, int dimmingValueTimeMiddleGroup, int dimmingValueTimeSubGroup,
                    int colorTemperatureValueMainGroup, int colorTemperatureValueMiddleGroup, int colorTemperatureValueSubGroup,
                    int statusColorTemperatureMainGroup, int statusColorTemperatureMiddleGroup, int statusColorTemperatureSubGroup)
            throws Exception {
        super(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                statusSwitchMainGroup, statusSwitchMiddleGroup, statusSwitchSubGroup,
                dimmingValueMainGroup, dimmingValueMiddleGroup, dimmingValueSubGroup,
                statusDimmingValueMainGroup, statusDimmingValueMiddleGroup, statusDimmingValueSubGroup,
                dimmingValueTimeMainGroup, dimmingValueTimeMiddleGroup, dimmingValueTimeSubGroup);

        colorTemperatureValueAddress = new GroupAddress(colorTemperatureValueMainGroup, colorTemperatureValueMiddleGroup, colorTemperatureValueSubGroup);
        statusColorTemperatureAddress = new GroupAddress(statusColorTemperatureMainGroup, statusColorTemperatureMiddleGroup, statusColorTemperatureSubGroup);

        statusColorTemperatureDP = new StateDP(
                statusColorTemperatureAddress,
                "Color Temperature",
                7, "7.600"
        );
        colorTemperatureValueDP = new StateDP(
                colorTemperatureValueAddress,
                "Color Temperature",
                7, "7.600"
        );
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        if (address.equals(statusColorTemperatureAddress)) {
            byte[] asdu = event.getASDU();

            if (asdu.length < 2) {
                return false;
            }

            DPTXlator2ByteUnsigned t =
                    new DPTXlator2ByteUnsigned(
                            DPTXlator2ByteUnsigned.DPT_ABSOLUTE_COLOR_TEMPERATURE
                    );

            t.setData(asdu);

            int newKelvin = t.getValueUnsigned();

            if (newKelvin <= 0) { forget(address); return false; }
            boolean changed = !known(address) || newKelvin != colorTemperature;
            colorTemperature = newKelvin;
            observed(address);
            return changed;
        }

        return super.updateState(address, event);
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        super.onStateUpdated(address, event);

        if (address.equals(statusColorTemperatureAddress)) {
            onColorTemperatureChanged(colorTemperature);
            publishBusState("colorTemperature", colorTemperature, event);
        }
    }

    protected void onColorTemperatureChanged(int newValue) {
        Logger.info(getNamedId() + " color temperature changed to " + newValue);

        if (colorTemperatureCallback != null) {
            colorTemperatureCallback.changed();
        }
    }

    @Override
    public void initialize() throws Exception {
        Exception failure = null;
        try { readColorTemperature(); } catch (Exception e) { failure = e; }
        try { super.initialize(); } catch (Exception e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        if (failure != null) throw failure;
    }

    private void readColorTemperature() throws Exception {
        colorTemperature = (int) adapter.bus().readNumeric(statusColorTemperatureDP);
        if (colorTemperature <= 0) throw new IllegalStateException("No valid color temperature feedback");
        observed(statusColorTemperatureAddress);
    }

    private void writeColorTemperature(int colorTemperature) throws Exception {
        adapter.bus().write(colorTemperatureValueDP, String.valueOf(colorTemperature));
    }

    @Override
    public String toString() {
        return super.toString() + ", "
                + "colorTemperature: " + colorTemperature;
    }

    @Override
    public CompletableFuture<Integer> getColorTemperature() {
        if (!known(statusColorTemperatureAddress) || colorTemperature <= 0)
            return CompletableFuture.failedFuture(new IllegalStateException("Color temperature unavailable"));
        return CompletableFuture.completedFuture(Math.max(getMinColorTemperature(),
                Math.min(getMaxColorTemperature(), convert(colorTemperature))));
    }

    @Override
    public CompletableFuture<Void> setColorTemperature(Integer value) throws Exception {
        if (value == null || value < getMinColorTemperature() || value > getMaxColorTemperature())
            throw new IllegalArgumentException("Color temperature outside HomeKit range");
        adapter.manualOverrides().hold(getLocation(), Set.of(es.buni.hcb.automation.PolicyKind.ADAPTIVE_COLOR));
        setColorTemperatureValue(convert(value));

        Logger.info("HomeKit set " + getNamedId() + " color temperature to " + colorTemperature);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeColorTemperature(HomekitCharacteristicChangeCallback callback) {
        colorTemperatureCallback = callback;
    }

    @Override
    public void unsubscribeColorTemperature() {
        colorTemperatureCallback = null;
    }

    @Override
    public int getMinColorTemperature() {
        return convert(COLOR_TEMPERATURE_MAX_KELVIN);
    }

    @Override
    public int getMaxColorTemperature() {
        return convert(COLOR_TEMPERATURE_MIN_KELVIN);
    }

    private static int convert(int value) {
        return 1000000 / value;
    }
}
