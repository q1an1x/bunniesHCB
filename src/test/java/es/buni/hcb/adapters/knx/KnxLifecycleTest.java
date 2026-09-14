package es.buni.hcb.adapters.knx;

import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.core.*;
import es.buni.hcb.support.FakeKnx;
import io.calimero.GroupAddress;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class KnxLifecycleTest {
    @Test void configurationAndDefaultModeNeverOpenATransport() throws Exception {
        var calls = new AtomicInteger();
        var adapter = new KNXAdapter(new EntityRegistry(), KnxSettings.defaults(KnxMode.OFFLINE, null),
                (receive, close) -> { calls.incrementAndGet(); throw new AssertionError("Network use"); }, Clock.systemUTC());
        try {
            adapter.configure(); int entities = adapter.entities().size(); adapter.configure(); adapter.start();
            assertEquals(72, entities); assertEquals(entities, adapter.entities().size());
            assertEquals(0, calls.get()); assertEquals(0, adapter.getRegistry().getEventBus().listenerCount());
            assertEquals(244, adapter.bindings().size());
        } finally { adapter.stop(); }
    }
    @Test void reconnectRetainsEntitiesAndStartsServicesOnlyOnce() throws Exception {
        try (var fake = new FakeKnx()) {
            var light = Tunable.fromConvention(fake.adapter, "test", "light", 1, 1, 1);
            fake.adapter.register(light);
            var starts = new AtomicInteger(); var stops = new AtomicInteger();
            fake.adapter.registerService(new Lifecycle() { public void start(){starts.incrementAndGet();} public void stop(){stops.incrementAndGet();} });
            fake.start(); int id = light.getId(); var first = fake.latest;
            first.disconnect(); assertFalse(light.isStateKnown());
            fake.await(() -> fake.connections.get() >= 2 && fake.adapter.isReady());
            assertSame(light, fake.adapter.getRegistry().get("test.light")); assertEquals(id, light.getId());
            assertFalse(first.open); assertEquals(1, starts.get()); assertTrue(light.isStateKnown());
            fake.adapter.stop(); fake.adapter.stop(); assertEquals(1, stops.get());
            fake.adapter.reconnect("late close callback"); assertEquals(KNXAdapter.ConnectionState.STOPPED, fake.adapter.connectionState());
        }
    }
    @Test void initialFailureAndRepeatedFailuresRecover() throws Exception {
        try (var fake = new FakeKnx()) {
            fake.failConnections = 3; fake.start();
            fake.await(fake.adapter::isReady); assertEquals(4, fake.connections.get());
        }
    }
    @Test void observeReceivesButNeverReadsOrWritesGroups() throws Exception {
        try (var fake = new FakeKnx(KnxMode.OBSERVE, true)) {
            var light = Tunable.fromConvention(fake.adapter, "test", "light", 1, 1, 1); fake.adapter.register(light);
            fake.start(); assertTrue(fake.reads.isEmpty());
            assertThrows(IllegalStateException.class, () -> light.on());
            assertThrows(IllegalStateException.class, () -> fake.adapter.bus().readBool(new GroupAddress("1/1/4")));
            fake.latest.receive("1/1/4", 0x80, (byte)1); fake.await(light::isOn);
            assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void unknownCommandAddressesAndDisconnectedCommandsAreRejected() throws Exception {
        try (var fake = new FakeKnx()) {
            fake.adapter.declareCommand("test", "switch", new GroupAddress("1/1/1"), "1.001");
            fake.start(); assertThrows(IllegalArgumentException.class, () -> fake.adapter.bus().write(new GroupAddress("31/7/255"), true));
            fake.adapter.stop(); assertThrows(IllegalStateException.class, () -> fake.adapter.bus().write(new GroupAddress("1/1/1"), true));
            assertTrue(fake.writes.isEmpty());
        }
    }
    @Test void eventAlreadyInProgressCannotWriteIntoAReplacementConnection() throws Exception {
        try (var fake = new FakeKnx()) {
            var address = new GroupAddress("1/1/1");
            fake.adapter.declareCommand("test", "switch", address, "1.001");
            fake.start();
            long old = fake.adapter.generation();
            var started = new java.util.concurrent.CountDownLatch(1);
            var resume = new java.util.concurrent.CountDownLatch(1);
            var rejected = new java.util.concurrent.CompletableFuture<Boolean>();
            var worker = new Thread(() -> fake.adapter.runInSession(old, () -> {
                started.countDown();
                try {
                    resume.await();
                    fake.adapter.bus().write(address, true);
                    rejected.complete(false);
                } catch (IllegalStateException expected) { rejected.complete(true); }
                catch (Exception failure) { rejected.completeExceptionally(failure); }
            }));
            worker.start();
            try {
                assertTrue(started.await(1, java.util.concurrent.TimeUnit.SECONDS));
                fake.latest.disconnect();
                fake.await(() -> fake.adapter.isReady() && fake.adapter.generation() != old);
                resume.countDown();
                assertTrue(rejected.get(1, java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(fake.writes.isEmpty());
            } finally { resume.countDown(); worker.join(1000); }
        }
    }

}
