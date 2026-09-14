package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.entities.lighting.*;
import es.buni.hcb.adapters.knx.entities.sensor.*;
import es.buni.hcb.support.FakeKnx;
import io.calimero.GroupAddress;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EntityStateTest {
    @Test void curtainTimeoutDoesNotFabricateArrivalAtTarget() throws Exception {
        try (var fake = new FakeKnx()) {
            var curtain = Curtain.fromConvention(fake.adapter,"test","curtain",1,2,1);
            fake.adapter.register(curtain); fake.start();
            assertEquals(0,curtain.getCurrentPosition().join());
            curtain.setTargetPosition(100).join();
            assertEquals(0,curtain.getCurrentPosition().join());
            curtain.movementTimedOut();
            assertTrue(curtain.getCurrentPosition().isCompletedExceptionally());
            assertThrows(IllegalArgumentException.class, () -> curtain.setPosition(101));
            assertNotNull(curtain.setHoldPosition(true));
        }
    }

    @Test void unknownColorFailsCleanlyAndFailedWriteDoesNotInventBrightness() throws Exception {
        try (var fake = new FakeKnx()) {
            var light = Tunable.fromConvention(fake.adapter, "test", "light", 1, 1, 1);
            assertTrue(light.getColorTemperature().isCompletedExceptionally());
            fake.adapter.register(light); fake.start();
            assertEquals(250, light.getColorTemperature().join());
            fake.failWrites = true;
            assertThrows(Exception.class, () -> light.setBrightnessValue(80));
            assertEquals(0, light.getBrightnessValue());
            assertThrows(Exception.class, light::on); assertFalse(light.isOn());
        }
    }
    @Test void partialInitializationStillReadsSwitchAndBrightness() throws Exception {
        try (var fake = new FakeKnx()) {
            fake.failColorRead = true;
            var light = Tunable.fromConvention(fake.adapter, "test", "light", 1, 1, 1); fake.adapter.register(light); fake.start();
            assertTrue(light.hasSwitchState()); assertTrue(light.hasBrightnessState());
            assertTrue(light.getColorTemperature().isCompletedExceptionally());
        }
    }
    @Test void immediateFeedbackIsKeptAndBrightnessDoesNotPublishPowerChanges() throws Exception {
        try (var fake = new FakeKnx()) {
            var light = Tunable.fromConvention(fake.adapter, "test", "light", 1, 1, 1); fake.adapter.register(light); fake.start();
            var powerChanges = new AtomicInteger(); light.subscribeLightbulbPowerState(powerChanges::incrementAndGet);
            light.setBrightness(80).join();
            light.handleBusUpdate(new GroupAddress("1/1/5"), fake.event("1/1/5", 0x80, (byte)102));
            assertEquals(40, light.getBrightnessValue()); assertEquals(0, powerChanges.get());
            light.handleBusUpdate(new GroupAddress("1/1/1"), fake.event("1/1/1", 0x80, (byte)1));
            assertFalse(light.isOn());
            light.handleBusUpdate(new GroupAddress("1/1/4"), fake.event("1/1/4", 0x80, (byte)1));
            assertTrue(light.isOn()); assertEquals(1, powerChanges.get());
        }
    }
    @Test void invalidControlRangesNeverReachTheTransport() throws Exception {
        try (var fake = new FakeKnx()) {
            var light = Tunable.fromConvention(fake.adapter, "test", "light", 1, 1, 1); fake.adapter.register(light); fake.start();
            assertThrows(IllegalArgumentException.class, () -> light.setBrightnessValue(101));
            assertThrows(IllegalArgumentException.class, () -> light.setColorTemperature(0));
            assertThrows(IllegalArgumentException.class, () -> light.setColorTemperatureValue(0));
            assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void illuminanceIsInitializedAndReadOnly() throws Exception {
        try (var fake = new FakeKnx()) {
            var sensor = new IlluminanceSensor(fake.adapter,"test","lux",1,1,10,3); fake.adapter.register(sensor); fake.start();
            assertTrue(sensor.isStateKnown()); assertEquals(360, sensor.getIlluminance());
            assertTrue(fake.reads.stream().anyMatch(r -> r.startsWith("readFloat:")));
            assertThrows(UnsupportedOperationException.class, () -> sensor.setIlluminance(0));
        }
    }
    @Test void readResponsesCannotPressButtonsOrRecallScenes() throws Exception {
        try (var fake = new FakeKnx()) {
            var presses = new AtomicInteger();
            var button = new Button(fake.adapter,"test","button",1,1,10) { protected void onButtonPressed(){presses.incrementAndGet();} };
            var scenes = new SceneController(fake.adapter,0,0,2);
            fake.adapter.register(button); fake.adapter.register(scenes); fake.start();
            var recalls = new AtomicInteger(); fake.adapter.getRegistry().getEventBus().subscribe(e -> recalls.incrementAndGet());
            var ga = new GroupAddress("1/1/10");
            button.handleBusUpdate(ga, fake.event(ga.toString(),0x40,(byte)1)); assertEquals(0,presses.get());
            button.handleBusUpdate(ga, fake.event(ga.toString(),0x80,(byte)0)); assertEquals(1,presses.get());
            for (int value : new int[]{0x80,0x40}) scenes.handleBusUpdate(new GroupAddress("0/0/2"),fake.event("0/0/2",0x80,(byte)value));
            scenes.handleBusUpdate(new GroupAddress("0/0/2"),fake.event("0/0/2",0x40,(byte)1));
            assertEquals(0,recalls.get());
            scenes.handleBusUpdate(new GroupAddress("0/0/2"),fake.event("0/0/2",0x80,(byte)1)); fake.adapter.getRegistry().getEventBus().barrier().join(); assertEquals(1,recalls.get());
        }
    }
}
