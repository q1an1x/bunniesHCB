package es.buni.hcb.adapters.knx.entities.lighting;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.datapoint.StateDP;
import io.calimero.dptxlator.DPTXlator2ByteUnsigned;
import io.calimero.process.ProcessEvent;

import java.util.Set;

public class Tunable extends Dimmable {

    protected final GroupAddress colorTemperatureValueAddress;
    protected final GroupAddress statusColorTemperatureAddress;

    private final StateDP colorTemperatureDP;

    private volatile int colorTemperature;

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

    public int getColorTemperature() {
        return colorTemperature;
    }

    public void setColorTemperature(int colorTemperature) throws Exception {
        this.colorTemperature = colorTemperature;
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

        colorTemperatureDP =
                new StateDP(
                        statusColorTemperatureAddress,
                        "Color Temperature",
                        7, "7.600"
                );
        colorTemperatureDP.addUpdatingAddress(colorTemperatureValueAddress);
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        if (address.equals(statusColorTemperatureAddress) || address.equals(colorTemperatureValueAddress)) {
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

            if (newKelvin != colorTemperature) {
                colorTemperature = newKelvin;
                return true;
            }
        }

        return super.updateState(address, event);
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        super.onStateUpdated(address, event);

        if (address.equals(statusColorTemperatureAddress) || address.equals(colorTemperatureValueAddress)) {
            onColorTemperatureChanged(colorTemperature);
            publishStateChanged("colorTemperature", colorTemperature);
        }
    }

    protected void onColorTemperatureChanged(int newValue) {
        Logger.info(getNamedId() + " color temperature changed to " + newValue);
    }

    @Override
    public void initialize() throws Exception {
        readColorTemperature();

        super.initialize();
    }

    private void readColorTemperature() throws Exception {

        colorTemperature = (int) adapter.communicator().readNumeric(colorTemperatureDP);
    }

    private void writeColorTemperature(int colorTemperature) throws Exception {
        adapter.communicator().write(colorTemperatureDP, String.valueOf(colorTemperature));
    }

    @Override
    public String toString() {
        return super.toString() + ", "
                + "colorTemperature: " + colorTemperature;
    }

}
