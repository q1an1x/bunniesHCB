package es.buni.hcb.automation.modes;

import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.automation.PolicyKind;
import es.buni.hcb.support.FakeKnx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class HouseModeTest {
    @TempDir Path directory;

    private static final class Home implements AutoCloseable {
        final FakeKnx fake = new FakeKnx();
        final Map<String, Toggle> toggles = new HashMap<>();
        final HouseModeController modes;
        Home(Path storage) throws Exception {
            for (String room : List.of("livingroom", "bedroom.north")) {
                int main = room.equals("livingroom") ? 2 : 3;
                int sub = 1;
                for (String kind : List.of("constantlighting", "adaptivelighting", "nightlighting")) {
                    var toggle = new Toggle(fake.adapter, room, "toggle." + kind, main, 0, sub++);
                    toggles.put(toggle.getNamedId(), toggle); fake.adapter.register(toggle);
                }
            }
            fake.adapter.register(Tunable.fromConvention(fake.adapter, "livingroom", "light.main", 2, 4, 21));
            fake.adapter.register(Tunable.fromConvention(fake.adapter, "bedroom.north", "light.main", 3, 6, 1));
            fake.adapter.register(Tunable.fromConvention(fake.adapter, "kitchen", "light.main", 1, 1, 1));
            fake.adapter.configureHouseModes(); modes = fake.adapter.houseModes();
            if (storage != null) modes.setStorage(storage);
            fake.start();
        }
        void dailyPreferences() throws Exception {
            for (var toggle : toggles.values()) toggle.setSwitchState(!toggle.getIId().endsWith("nightlighting"));
            flush(); fake.writes.clear();
        }
        Toggle toggle(String name) { return toggles.get(name); }
        void select(HouseMode mode) throws Exception { modes.select(mode).get(3, TimeUnit.SECONDS); flush(); }
        void flush() { fake.adapter.getRegistry().getEventBus().barrier().join(); }
        @Override public void close() throws Exception { fake.close(); }
    }

    @Test void previewIsPureAndAllModesHaveDistinctIntent() throws Exception {
        try (var home = new Home(null)) {
            for (HouseMode mode : HouseMode.values()) assertEquals(mode, home.modes.preview(mode).mode());
            assertTrue(home.fake.writes.isEmpty());
            assertTrue(home.modes.preview(HouseMode.MOVIE).actions().stream().anyMatch(a -> a.kind() == ModeAction.Kind.SCENE && a.value() == 4));
            assertTrue(home.modes.preview(HouseMode.CLEANING).actions().stream().anyMatch(a -> a.kind() == ModeAction.Kind.BRIGHTNESS && a.value() == 90));
            assertTrue(home.modes.preview(HouseMode.AWAY).actions().stream().noneMatch(a -> a.target().contains("curtain") || a.target().contains("oven")));
        }
    }
    @Test void awaySuspendsEveryPolicyAndReturnsOnlyOwnedAutomationSettings() throws Exception {
        try (var home = new Home(null)) {
            home.dailyPreferences(); home.select(HouseMode.AWAY);
            assertTrue(home.toggles.values().stream().noneMatch(Toggle::isOn));
            for (PolicyKind kind : PolicyKind.values()) assertFalse(home.fake.adapter.permitsPolicy("kitchen", kind));
            assertTrue(home.fake.writes.contains("1/1/1=false"));
            home.fake.writes.clear(); home.select(HouseMode.HOME);
            assertTrue(home.toggle("bedroom.north.toggle.constantlighting").isOn());
            assertFalse(home.toggle("bedroom.north.toggle.nightlighting").isOn());
            assertTrue(home.fake.writes.stream().allMatch(w -> w.startsWith("2/0/") || w.startsWith("3/0/")));
        }
    }
    @Test void manualRepeatedOffIsNotOverwrittenWhenLeavingAMode() throws Exception {
        try (var home = new Home(null)) {
            home.dailyPreferences(); home.select(HouseMode.AWAY);
            var toggle = home.toggle("bedroom.north.toggle.constantlighting");
            toggle.setSwitchState(false); home.flush();
            home.select(HouseMode.HOME);
            assertFalse(toggle.isOn());
            assertTrue(home.toggle("bedroom.north.toggle.adaptivelighting").isOn());
        }
    }
    @Test void movieAffectsOnlyLivingRoomAndRepeatedOnDoesNotReplayScenes() throws Exception {
        try (var home = new Home(null)) {
            home.dailyPreferences(); home.select(HouseMode.MOVIE);
            assertTrue(home.toggle("bedroom.north.toggle.constantlighting").isOn());
            assertFalse(home.fake.adapter.permitsPolicy("livingroom", PolicyKind.ADAPTIVE_COLOR));
            assertTrue(home.fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
            assertTrue(home.fake.writes.contains("0/0/2=4"));
            home.fake.writes.clear(); home.select(HouseMode.MOVIE); assertTrue(home.fake.writes.isEmpty());
        }
    }
    @Test void sleepAndGuestHaveRoomAndPolicySpecificPriorities() throws Exception {
        try (var home = new Home(null)) {
            home.dailyPreferences(); home.select(HouseMode.SLEEP);
            assertTrue(home.toggle("bedroom.north.toggle.nightlighting").isOn());
            assertFalse(home.toggle("bedroom.north.toggle.adaptivelighting").isOn());
            assertTrue(home.fake.adapter.permitsPolicy("bedroom.north", PolicyKind.NIGHT_LIGHT));
            assertFalse(home.fake.adapter.permitsPolicy("kitchen", PolicyKind.PRESENCE));
            home.select(HouseMode.GUEST);
            assertTrue(home.fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
            assertFalse(home.fake.adapter.permitsPolicy("bathroom", PolicyKind.PRESENCE));
        }
    }
    @Test void switchingFromCleaningToMovieRestoresOtherRooms() throws Exception {
        try (var home = new Home(null)) {
            home.dailyPreferences(); home.select(HouseMode.CLEANING);
            assertTrue(home.fake.writes.contains("3/6/1=true"));
            home.fake.writes.clear(); home.select(HouseMode.MOVIE);
            assertTrue(home.toggle("bedroom.north.toggle.constantlighting").isOn());
            assertFalse(home.toggle("livingroom.toggle.constantlighting").isOn());
            assertFalse(home.fake.writes.contains("3/6/1=false"), "Mode restoration never replays lamp states");
        }
    }
    @Test void missingInitialStateDoesNotInventARestoreValue() throws Exception {
        try (var home = new Home(null)) {
            home.dailyPreferences(); var toggle = home.toggle("bedroom.north.toggle.constantlighting"); toggle.invalidateState();
            assertFalse(home.modes.preview(HouseMode.AWAY).warnings().isEmpty());
            home.select(HouseMode.AWAY); home.select(HouseMode.HOME); assertFalse(toggle.isOn());
        }
    }
    @Test void disconnectCancelsQueuedModeWorkImmediately() throws Exception {
        try (var home = new Home(null)) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            home.fake.adapter.getRegistry().getEventBus().execute(() -> {
                entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS));
                var pending = home.modes.select(HouseMode.AWAY);
                home.fake.latest.disconnect(); assertTrue(pending.isCompletedExceptionally());
                home.fake.await(home.fake.adapter::isReady);
                assertTrue(home.fake.writes.isEmpty());
            } finally { release.countDown(); }
        }
    }
    @Test void homekitAcknowledgesQueuedIntentWithoutWaitingForTheEntireLightingBatch() throws Exception {
        try (var home = new Home(null)) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            home.fake.adapter.getRegistry().getEventBus().execute(() -> {
                entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            try {
                assertTrue(entered.await(1,TimeUnit.SECONDS));
                var accessory = (ModeAccessory) home.fake.adapter.getRegistry().get("house.mode.cleaning");
                assertTrue(accessory.setSwitchState(true).isDone());
                release.countDown();
                home.fake.await(() -> home.modes.status().mode() == HouseMode.CLEANING && home.modes.status().phase() == HouseModeController.Phase.ACTIVE);
                assertTrue(accessory.getSwitchState().join());
            } finally { release.countDown(); }
        }
    }
    @Test void failedTransitionPausesAutomationAndRequiresExplicitRecovery() throws Exception {
        try (var home = new Home(null)) {
            home.dailyPreferences(); home.fake.failAfterWrites = 2;
            assertThrows(ExecutionException.class, () -> home.modes.select(HouseMode.AWAY).get(3, TimeUnit.SECONDS));
            assertEquals(HouseModeController.Phase.FAILED, home.modes.status().phase());
            assertFalse(home.fake.adapter.permitsPolicy("bedroom.north", PolicyKind.NIGHT_LIGHT));
            assertEquals(2, home.fake.writes.size(), "A partial transition must not replay or roll back physical writes");
            home.fake.failAfterWrites = Integer.MAX_VALUE; home.select(HouseMode.HOME);
            assertEquals(HouseModeController.Phase.ACTIVE, home.modes.status().phase());
        }
    }
    @Test void restartLoadsModeIntentButNeverReplaysLightingActions() throws Exception {
        Path file = directory.resolve("mode.json");
        try (var home = new Home(file)) { home.dailyPreferences(); home.select(HouseMode.AWAY); }
        try (var home = new Home(file)) {
            assertEquals(HouseMode.AWAY, home.modes.status().mode());
            assertEquals(HouseModeController.Phase.PAUSED, home.modes.status().phase());
            assertTrue(home.fake.writes.isEmpty());
            assertFalse(home.fake.adapter.permitsPolicy("kitchen", PolicyKind.PRESENCE));
            home.select(HouseMode.HOME); assertTrue(home.fake.writes.isEmpty());
        }
    }
    @Test void corruptModeCheckpointPausesPoliciesWithoutReplacingItAtStartup() throws Exception {
        Path file = directory.resolve("mode.json"); Files.writeString(file, "broken");
        try (var home = new Home(file)) {
            assertEquals(HouseModeController.Phase.FAILED, home.modes.status().phase());
            assertEquals("broken", Files.readString(file)); assertTrue(home.fake.writes.isEmpty());
            var recovery = (ModeAccessory) home.fake.adapter.getRegistry().get("house.mode.home");
            assertFalse(recovery.getSwitchState().join(), "The recovery switch must remain available");
            recovery.setSwitchState(true).join(); home.fake.await(() -> home.modes.status().phase() == HouseModeController.Phase.ACTIVE);
        }
    }
    @Test void noModeActionRunsWhenAutomationsAreDisabled() throws Exception {
        try (var fake = new FakeKnx(es.buni.hcb.adapters.knx.KnxMode.LIVE, false)) {
            fake.adapter.configureHouseModes(); fake.start();
            assertTrue(fake.adapter.houseModes().select(HouseMode.AWAY).isCompletedExceptionally());
            assertTrue(fake.writes.isEmpty());
        }
    }
}
