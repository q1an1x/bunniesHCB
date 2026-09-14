package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.support.FakeKnx;
import io.calimero.*;
import io.calimero.process.ProcessEvent;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ManualOverridesTest {
    @Test void manualPowerAndBrightnessHoldOnlyTheAffectedRoomUntilExpiry() throws Exception {
        try (var fake = new FakeKnx()) {
            var lamp = Tunable.fromConvention(fake.adapter, "bedroom.north", "light.main", 3, 6, 1);
            fake.adapter.register(lamp); fake.start(); lamp.setBrightness(40);
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.NIGHT_LIGHT));
            assertTrue(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.ADAPTIVE_COLOR));
            assertTrue(fake.adapter.permitsPolicy("bedroom.south", PolicyKind.CONSTANT_LIGHT));
            fake.clock.advance(Duration.ofMinutes(29)); assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
            lamp.setLightbulbPowerState(true); fake.clock.advance(Duration.ofMinutes(2));
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT), "A new manual command extends the hold");
            fake.clock.advance(Duration.ofMinutes(28)); assertTrue(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
        }
    }
    @Test void physicalCommandPausesAutomationButFeedbackResponsesAndEchoesDoNot() throws Exception {
        try (var fake = new FakeKnx()) {
            var lamp = Tunable.fromConvention(fake.adapter, "livingroom", "light.main", 2, 4, 21);
            fake.adapter.register(lamp); fake.start();
            fake.latest.receive("2/4/24", 0x80, (byte)1); fake.await(lamp::isOn);
            assertTrue(fake.adapter.manualOverrides().active().isEmpty());
            fake.adapter.manualOverrides().observe(fake.event("2/4/21",0x40,(byte)1));
            var echo = new ProcessEvent(fake.latest.pc, new IndividualAddress("1.1.240"), new GroupAddress("2/4/21"),0x80,new byte[]{1},true);
            fake.adapter.manualOverrides().observe(echo);
            assertTrue(fake.adapter.manualOverrides().active().isEmpty());
            fake.latest.receive("2/4/21",0x80,(byte)1);
            fake.await(() -> !fake.adapter.permitsPolicy("livingroom", PolicyKind.CONSTANT_LIGHT));
            assertFalse(fake.adapter.manualOverrides().active().isEmpty());
        }
    }
    @Test void colorHoldBlocksColorChangingScenesButLeavesBrightnessControlAvailable() throws Exception {
        try (var fake = new FakeKnx()) {
            var lamp = Tunable.fromConvention(fake.adapter,"bedroom.north","light.main",3,6,1);
            var enabled = new Toggle(fake.adapter,"bedroom.north","toggle.adaptivelighting",3,0,2);
            fake.adapter.register(lamp); fake.adapter.register(enabled); fake.start(); lamp.setColorTemperature(300);
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.ADAPTIVE_COLOR));
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.NIGHT_LIGHT));
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.PRESENCE));
            assertTrue(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
            enabled.setSwitchState(true); assertTrue(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.ADAPTIVE_COLOR));
        }
    }
    @Test void adaptivePolicyReassertsItsTargetWhenManualHoldExpires() throws Exception {
        try (var fake = new FakeKnx()) {
            var enabled = new Toggle(fake.adapter,"bedroom.north","toggle.adaptivelighting",3,0,2);
            var policy = new AdaptiveLightingPolicy("adaptive",fake.adapter,enabled,3,0,22);
            fake.adapter.register(enabled); fake.adapter.registerService(policy); fake.start();
            enabled.setSwitchState(true); fake.adapter.getRegistry().getEventBus().barrier().join(); fake.writes.clear();
            fake.adapter.manualOverrides().duration(Duration.ofSeconds(1));
            fake.adapter.manualOverrides().hold("bedroom.north", Set.of(PolicyKind.ADAPTIVE_COLOR));
            policy.update(); assertTrue(fake.writes.isEmpty());
            fake.clock.advance(Duration.ofSeconds(1)); policy.update();
            assertEquals(1, fake.writes.size());
        }
    }
    @Test void policyGroupCommandsHaveTheSameManualPriorityAsIndividualLamps() throws Exception {
        try (var fake = new FakeKnx()) {
            var enabled = new Toggle(fake.adapter,"bedroom.north","toggle.adaptivelighting",3,0,2);
            new AdaptiveLightingPolicy("adaptive",fake.adapter,enabled,3,0,22);
            fake.adapter.register(enabled); fake.start();
            fake.adapter.manualOverrides().observe(fake.event("3/0/22",0x80,(byte)0x0f,(byte)0xa0));
            assertFalse(fake.adapter.permitsPolicy("bedroom.north",PolicyKind.ADAPTIVE_COLOR));
        }
    }
}
