package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.StateChangedEvent;
import io.calimero.GroupAddress;
import io.calimero.datapoint.StateDP;
import java.time.Duration;
import java.time.LocalTime;

public final class AdaptiveLightingPolicy extends ManagedLightingPolicy {
    private static final int MIN = Tunable.COLOR_TEMPERATURE_MIN_KELVIN;
    private static final int MAX = Tunable.COLOR_TEMPERATURE_MAX_KELVIN;
    private static final int[][] TIMELINE = {{0, MIN}, {7*3600, MIN}, {9*3600, 4000}, {11*3600, 5500},
            {13*3600, MAX}, {16*3600, MAX}, {17*3600, 5000}, {19*3600, 3500}, {21*3600, MIN}, {24*3600, MIN}};
    private final Toggle enabled;
    private final StateDP target;
    private int lastKelvin = -1;
    private long lastGeneration = -1;

    public AdaptiveLightingPolicy(String name, KNXAdapter adapter, Toggle enabled, int main, int middle, int sub) {
        super(name, adapter);
        this.enabled = enabled;
        var address = new GroupAddress(main, middle, sub);
        target = new StateDP(address, "Color temperature", 7, "7.600");
        adapter.declareCommand(name, "colorTemperature", address, "7.600");
    }
    @Override protected Duration interval() { return Duration.ofSeconds(30); }
    @Override protected void handle(EntityEvent event) throws Exception {
        if (event instanceof StateChangedEvent state && state.entityId().equals(enabled.getNamedId())) evaluate();
    }
    @Override protected void evaluate() throws Exception {
        if (!enabled.isOn()) { lastKelvin = -1; return; }
        if (lastGeneration != adapter.generation()) { lastKelvin = -1; lastGeneration = adapter.generation(); }
        int kelvin = calculateKelvin(LocalTime.now(adapter.clock()));
        if (lastKelvin == -1 || Math.abs(kelvin - lastKelvin) >= 50) {
            adapter.bus().write(target, Integer.toString(kelvin));
            lastKelvin = kelvin;
        }
    }
    static int calculateKelvin(LocalTime time) {
        int second = time.toSecondOfDay();
        for (int i = 1; i < TIMELINE.length; i++) {
            if (second <= TIMELINE[i][0]) {
                int[] previous = TIMELINE[i-1], next = TIMELINE[i];
                double fraction = (second - previous[0]) / (double) (next[0] - previous[0]);
                return (int) (previous[1] + (next[1] - previous[1]) * (1 - Math.cos(fraction * Math.PI)) / 2);
            }
        }
        return MIN;
    }
}
