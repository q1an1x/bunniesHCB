package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import java.util.Set;
import java.util.concurrent.*;

public final class DelayedOffButton extends Button {
    private final Set<GroupAddress> targets;
    private final int delayMinutes;
    private ScheduledFuture<?> pending;

    public DelayedOffButton(KNXAdapter adapter, String location, String id, int main, int middle, int sub, Set<GroupAddress> targets) {
        this(adapter, location, id, main, middle, sub, targets, 30);
    }
    public DelayedOffButton(KNXAdapter adapter, String location, String id, int main, int middle, int sub,
                            Set<GroupAddress> targets, int delayMinutes) {
        super(adapter, location, id, main, middle, sub);
        if (delayMinutes < 0) throw new IllegalArgumentException("Delay must not be negative");
        this.targets = Set.copyOf(targets); this.delayMinutes = delayMinutes;
        for (GroupAddress target : targets) adapter.declareCommand(getNamedId(), "off", target, "1.001");
    }
    @Override protected synchronized void onButtonPressed() {
        if (pending != null) pending.cancel(false);
        long epoch = adapter.generation();
        pending = adapter.scheduler().schedule(() -> {
            if (!adapter.isAutomationReady() || epoch != adapter.generation()) return;
            for (GroupAddress address : targets.stream().sorted().toList()) {
                try {
                    var toggle = adapter.entities().stream().filter(e -> e instanceof Toggle t && t.groupAddresses().contains(address))
                            .map(e -> (Toggle)e).findFirst();
                    if (toggle.isPresent()) toggle.get().setSwitchState(false);
                    else adapter.bus().write(address, false);
                } catch (Exception e) { Logger.error("Delayed off failed for " + address, e); }
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }
    @Override public synchronized void shutdown() { if (pending != null) pending.cancel(false); }
}
