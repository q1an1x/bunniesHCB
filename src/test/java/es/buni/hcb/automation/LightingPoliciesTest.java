package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.entities.*;
import es.buni.hcb.adapters.knx.entities.lighting.*;
import es.buni.hcb.adapters.knx.entities.sensor.*;
import es.buni.hcb.core.events.*;
import es.buni.hcb.support.FakeKnx;
import io.calimero.GroupAddress;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class LightingPoliciesTest {
    private static void flush(FakeKnx fake) { fake.adapter.getRegistry().getEventBus().barrier().join(); }
    private static void state(FakeKnx fake, KNXEntity entity, String ga, byte value) throws Exception {
        entity.handleBusUpdate(new GroupAddress(ga), fake.event(ga,0x80,value)); flush(fake);
    }
    @Test void nightModeTurnsOffAtNineEvenAfterMorningLockout() throws Exception {
        try (var fake = new FakeKnx()) {
            var toggle = new Toggle(fake.adapter,"test","night",1,0,1);
            var sensor = new OccupancySensor(fake.adapter,"test","presence",1,0,2);
            var policy = new NightLightingPolicy("night",fake.adapter,toggle,sensor,new GroupAddress("0/0/2"),10,new GroupAddress("1/0/31"));
            fake.adapter.register(toggle); fake.adapter.register(sensor); fake.adapter.registerService(policy); fake.start();
            fake.clock.set(Instant.parse("2026-09-13T20:59:00Z"));
            toggle.setSwitchState(true); flush(fake); fake.writes.clear();
            fake.clock.advance(Duration.ofMinutes(1)); policy.update(); assertEquals(1,fake.writes.size());
            fake.latest.disconnect(); fake.await(fake.adapter::isReady);
            // Hydration can confirm ON again, but it is not a new manual enable action.
            toggle.handleBusUpdate(new GroupAddress("1/0/1"), fake.event("1/0/1",0x40,(byte)1)); flush(fake);
            fake.writes.clear(); state(fake,sensor,"1/0/2",(byte)1);
            assertTrue(fake.writes.isEmpty(), "Reconnect must preserve a morning lockout");
            fake.clock.advance(Duration.ofHours(4)); policy.update(); flush(fake); assertFalse(toggle.isOn());
        }
    }
    @Test void unknownOrResponseOnlyOccupancyCannotTriggerANightScene() throws Exception {
        try (var fake = new FakeKnx()) {
            var toggle = new Toggle(fake.adapter,"test","night",1,0,1);
            var sensor = new OccupancySensor(fake.adapter,"test","presence",1,0,2);
            var policy = new NightLightingPolicy("night",fake.adapter,toggle,sensor,new GroupAddress("0/0/2"),10,new GroupAddress("1/0/31"));
            fake.adapter.register(toggle); fake.adapter.register(sensor); fake.adapter.registerService(policy); fake.start();
            toggle.setSwitchState(true); flush(fake); fake.writes.clear();
            sensor.handleBusUpdate(new GroupAddress("1/0/2"),fake.event("1/0/2",0x40,(byte)1)); flush(fake);
            assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void turningNightOffThenOnStartsOneNewScene() throws Exception {
        try (var fake = new FakeKnx()) {
            var toggle = new Toggle(fake.adapter,"test","night",1,0,1);
            var sensor = new OccupancySensor(fake.adapter,"test","presence",1,0,2);
            var policy = new NightLightingPolicy("night",fake.adapter,toggle,sensor,new GroupAddress("0/0/2"),10,new GroupAddress("1/0/31"));
            fake.adapter.register(toggle); fake.adapter.register(sensor); fake.adapter.registerService(policy); fake.start();
            state(fake,sensor,"1/0/2",(byte)1); toggle.setSwitchState(true); flush(fake);
            toggle.setSwitchState(false); flush(fake); fake.writes.clear(); toggle.setSwitchState(true); flush(fake);
            assertEquals(1,fake.writes.stream().filter(w -> w.equals("0/0/2=10")).count());
        }
    }
    @Test void constantLightingWaitsForRealPresenceAndValidLux() throws Exception {
        try (var fake = new FakeKnx()) {
            var enabled = new Toggle(fake.adapter,"test","auto",1,0,1);
            var night = new Toggle(fake.adapter,"test","night",1,0,3);
            var sensor = new OccupancySensor(fake.adapter,"test","presence",1,0,2);
            var lux = new IlluminanceSensor(fake.adapter,"test","lux",1,0,4);
            var light = Dimmable.fromConvention(fake.adapter,"test","light",1,1,1);
            var policy = new ConstantLightingPolicy("constant",fake.adapter,enabled,night,lux,sensor,
                    new GroupAddress("1/0/21"),new GroupAddress("1/0/31"),light,200,1);
            for (var entity : new KNXEntity[]{enabled,night,sensor,lux,light}) fake.adapter.register(entity);
            fake.adapter.registerService(policy); fake.start(); enabled.setSwitchState(true); flush(fake); fake.writes.clear();
            policy.update(); assertTrue(fake.writes.isEmpty());
            lux.invalidateState(); state(fake,sensor,"1/0/2",(byte)1); policy.update(); assertTrue(fake.writes.isEmpty());
            lux.initialize(); policy.update(); assertTrue(fake.writes.contains("1/0/21=10"));
            state(fake,sensor,"1/0/2",(byte)0); fake.writes.clear();
            fake.clock.advance(Duration.ofSeconds(300)); policy.update(); assertTrue(fake.writes.contains("1/0/21=20"));
            fake.clock.advance(Duration.ofSeconds(60)); policy.update(); assertTrue(fake.writes.contains("1/0/31=false"));
        }
    }
    @Test void kitchenAutomationDoesNotTakeOwnershipOfAManuallyLitSink() throws Exception {
        try (var fake = new FakeKnx()) {
            var sensor = new OccupancySensor(fake.adapter,"test","presence",1,0,1);
            var main = Light.fromConvention(fake.adapter,"test","main",1,1,1);
            var sink = Light.fromConvention(fake.adapter,"test","sink",1,2,1);
            var policy = new SimpleLightingControlPolicy("sink",fake.adapter,sensor,main,sink);
            for (var entity : new KNXEntity[]{sensor,main,sink}) fake.adapter.register(entity);
            fake.adapter.registerService(policy); fake.start();
            state(fake,sink,"1/2/2",(byte)1); state(fake,sensor,"1/0/1",(byte)1); state(fake,sensor,"1/0/1",(byte)0);
            assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void repeatedStartDoesNotDuplicateSubscriptionsAndStopRemovesThem() throws Exception {
        try (var fake = new FakeKnx()) {
            var toggle = new Toggle(fake.adapter,"test","adaptive",1,0,1);
            var policy = new AdaptiveLightingPolicy("adaptive",fake.adapter,toggle,1,0,22);
            fake.adapter.register(toggle); fake.adapter.registerService(policy); fake.start();
            int count = fake.adapter.getRegistry().getEventBus().listenerCount(); policy.start(); assertEquals(count,fake.adapter.getRegistry().getEventBus().listenerCount());
            policy.stop(); assertEquals(count-1,fake.adapter.getRegistry().getEventBus().listenerCount());
            toggle.setSwitchState(true); flush(fake); fake.writes.clear(); policy.update(); assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void adaptiveCurveStaysWithinLampRangeAcrossTheDay() {
        for (int second = 0; second < 86400; second += 17) {
            int kelvin = AdaptiveLightingPolicy.calculateKelvin(LocalTime.ofSecondOfDay(second));
            assertTrue(kelvin >= Tunable.COLOR_TEMPERATURE_MIN_KELVIN && kelvin <= Tunable.COLOR_TEMPERATURE_MAX_KELVIN);
        }
        assertEquals(Tunable.COLOR_TEMPERATURE_MIN_KELVIN, AdaptiveLightingPolicy.calculateKelvin(LocalTime.of(23,59,59)));
    }
}
