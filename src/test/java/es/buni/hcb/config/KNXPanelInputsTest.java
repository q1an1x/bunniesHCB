package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.automation.PolicyKind;
import es.buni.hcb.support.FakeKnx;
import io.calimero.GroupAddress;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class KNXPanelInputsTest {
    @Test void relativeColorProtectsAllActualTargetRoomsWithoutRelayingACommand() throws Exception {
        try (var fake = new FakeKnx()) {
            KNXPanelInputs.registerAll(fake.adapter); fake.start();
            fake.latest.receive("2/0/12", 0x80, (byte)9);
            fake.await(() -> !fake.adapter.permitsPolicy("kitchen", PolicyKind.PRESENCE));
            assertFalse(fake.adapter.permitsPolicy("entry", PolicyKind.ADAPTIVE_COLOR));
            assertFalse(fake.adapter.permitsPolicy("livingroom", PolicyKind.ADAPTIVE_COLOR));
            assertTrue(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.ADAPTIVE_COLOR));
            assertTrue(fake.writes.isEmpty()); assertTrue(fake.reads.isEmpty());
            var bindings = fake.adapter.bindings();
            assertEquals(11, bindings.size());
            assertTrue(bindings.stream().noneMatch(b -> b.writable() || b.readable()));
        }
    }

    @Test void bedroomDimStepHoldsBrightnessAndReleaseDoesNotExtendTheDeadline() throws Exception {
        try (var fake = new FakeKnx()) {
            KNXPanelInputs.registerAll(fake.adapter); fake.start();
            var holds = fake.adapter.manualOverrides();
            holds.observe(fake.event("3/0/11", 0x80, (byte)1));
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
            assertTrue(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.ADAPTIVE_COLOR));
            assertTrue(fake.adapter.permitsPolicy("bedroom.south", PolicyKind.CONSTANT_LIGHT));
            long revision = holds.revision("bedroom.north");
            fake.clock.advance(Duration.ofMinutes(29));
            for (byte value : new byte[]{0, 8, 16, (byte)255}) holds.observe(fake.event("3/0/11", 0x80, value));
            holds.observe(fake.event("3/0/11", 0x80, (byte)0, (byte)9));
            holds.observe(fake.event("3/0/11", 0x40, (byte)9));
            assertEquals(revision, holds.revision("bedroom.north"));
            fake.clock.advance(Duration.ofMinutes(1));
            assertTrue(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
        }
    }

    @Test void localEchoAndUnknownRelativeAddressCannotClaimManualOwnership() throws Exception {
        try (var fake = new FakeKnx()) {
            KNXPanelInputs.registerAll(fake.adapter); fake.start();
            var echo = new io.calimero.process.ProcessEvent(fake.latest.pc, new io.calimero.IndividualAddress("1.1.240"),
                    new GroupAddress("2/0/21"), 0x80, new byte[]{9}, true);
            fake.adapter.manualOverrides().observe(echo);
            fake.adapter.manualOverrides().observe(fake.event("2/0/13", 0x80, (byte)9));
            assertTrue(fake.adapter.manualOverrides().active().isEmpty());
        }
    }

    @Test void nativeAllOffHoldsSoftwarePoliciesButDoesNotInferAwayOrReplayLighting() throws Exception {
        try (var fake = new FakeKnx()) {
            fake.adapter.register(Tunable.fromConvention(fake.adapter, "bedroom.north", "light.main", 3, 6, 1));
            fake.adapter.register(Tunable.fromConvention(fake.adapter, "kitchen", "light.main", 1, 1, 1));
            KNXPanelInputs.registerAll(fake.adapter); fake.adapter.configureHouseModes(); fake.start();
            var holds = fake.adapter.manualOverrides();
            holds.observe(fake.event("0/0/1", 0x40, (byte)0));
            holds.observe(fake.event("0/0/1", 0x80, (byte)1));
            holds.observe(fake.event("0/0/1", 0x80, (byte)2));
            assertTrue(holds.active().isEmpty());
            holds.observe(fake.event("0/0/1", 0x80, (byte)0));
            assertFalse(fake.adapter.permitsPolicy("bedroom.north", PolicyKind.CONSTANT_LIGHT));
            assertFalse(fake.adapter.permitsPolicy("kitchen", PolicyKind.PRESENCE));
            assertEquals(es.buni.hcb.automation.modes.HouseMode.HOME, fake.adapter.houseModes().status().mode());
            assertTrue(fake.writes.isEmpty());
            fake.clock.advance(Duration.ofMinutes(30));
            assertTrue(fake.adapter.permitsPolicy("kitchen", PolicyKind.PRESENCE));
        }
    }
}
