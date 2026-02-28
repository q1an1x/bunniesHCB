package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.utils.Debug;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.datapoint.StateDP;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AdaptiveLightingPolicy implements LightingPolicy {

    private static final int K_MIN = Tunable.COLOR_TEMPERATURE_MIN_KELVIN;
    private static final int K_MAX = Tunable.COLOR_TEMPERATURE_MAX_KELVIN;

    private static final int K_THRESHOLD = 50;

    private final String name;
    private final KNXAdapter adapter;
    private final Toggle enabledToggle;
    private final GroupAddress targetGroup;
    private final StateDP targetDP;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final List<Keyframe> timeline = new ArrayList<>();

    private int lastSentKelvin = -1;

    public AdaptiveLightingPolicy(String name, KNXAdapter adapter, Toggle enabledToggle,
    int mainGroup, int middleGroup, int subGroup) {
        this.name = name;
        this.adapter = adapter;
        this.enabledToggle = enabledToggle;
        this.targetGroup = new GroupAddress(mainGroup, middleGroup, subGroup);
        this.targetDP = new StateDP(
                targetGroup,
                "Color Temperature",
                7, "7.600"
        );

        // Deep Night
        addKeyframe(0,  0,  K_MIN);
        addKeyframe(7,  0,  K_MIN);

        // Waking Up
        addKeyframe(9,  0,  4000);

        // Rise
        addKeyframe(11,  0,  5500);
        addKeyframe(13, 0,  K_MAX);

        // Sustained Daylight
        addKeyframe(16, 0,  K_MAX);

        addKeyframe(17,  0, 5000);
        addKeyframe(19,  0,  3500);

        // Bedtime Prep
        addKeyframe(21, 0,  K_MIN);

        // Wrap around
        addKeyframe(23, 59, K_MIN);
    }

    @Override
    public void start() {
        scheduler.scheduleAtFixedRate(this::update, 0, 30, TimeUnit.SECONDS);
        enabledToggle.setOnToggleListener(this::update);
    }

    @Override
    public void update() {
        if (!enabledToggle.isOn()) {
            lastSentKelvin = -1;
            return;
        }

        try {
            LocalTime now = LocalTime.now();
            int targetK = calculateKelvin(now);

            if (lastSentKelvin == -1 || Math.abs(targetK - lastSentKelvin) >= K_THRESHOLD) {
                if(targetK > K_MAX) targetK = K_MAX;
                if(targetK < K_MIN) targetK = K_MIN;

                if (Debug.ENABLED) {
                    Logger.info(name + " adapted to " + targetK + " K");
                }
                adapter.communicator().write(targetDP, String.valueOf(targetK));

                lastSentKelvin = targetK;
            }

        } catch (Exception e) {
            Logger.error("[" + name + "] Error: " + e.getMessage());
        }
    }

    private int calculateKelvin(LocalTime time) {
        int currentSeconds = time.toSecondOfDay();

        Keyframe prev = timeline.get(0);
        Keyframe next = timeline.get(timeline.size() - 1);

        for (int i = 0; i < timeline.size() - 1; i++) {
            if (currentSeconds >= timeline.get(i).totalSeconds &&
                    currentSeconds <= timeline.get(i+1).totalSeconds) {
                prev = timeline.get(i);
                next = timeline.get(i+1);
                break;
            }
        }

        double totalRange = next.totalSeconds - prev.totalSeconds;
        if (totalRange == 0) return prev.kelvin;

        double elapsed = currentSeconds - prev.totalSeconds;
        double linearProgress = elapsed / totalRange;

        return (int) (prev.kelvin + (next.kelvin - prev.kelvin)
                * (1 - Math.cos(linearProgress * Math.PI)) / 2);
    }

    private void addKeyframe(int hour, int minute, int kelvin) {
        addKeyframe(hour, minute, 0, kelvin);
    }

    private void addKeyframe(int hour, int minute, int second, int kelvin) {
        timeline.add(new Keyframe(hour, minute, second, kelvin));
        Collections.sort(timeline);
    }

    private static class Keyframe implements Comparable<Keyframe> {
        int totalSeconds;
        int kelvin;

        Keyframe(int hour, int minute, int second, int kelvin) {
            this.totalSeconds = (hour * 3600) + (minute * 60) + second;
            this.kelvin = kelvin;
        }

        @Override
        public int compareTo(Keyframe o) {
            return Integer.compare(this.totalSeconds, o.totalSeconds);
        }
    }
}