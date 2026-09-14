package es.buni.hcb.adapters.broadlink;

import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import es.buni.hcb.core.EntityRegistry;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class OvenRecoveryTest {
    @Test void initialFailureStillSchedulesRecoveryWithoutSendingAnOvenCommand() throws Exception {
        var attempts = new CountDownLatch(2);
        var registry = new EntityRegistry();
        var adapter = new BroadlinkAdapter(registry) {
            @Override public boolean authenticate(String host, int port, byte[] mac, int type) throws IOException {
                attempts.countDown(); throw new IOException("simulated unavailable oven");
            }
            @Override public com.google.gson.JsonObject setDeviceState(String host, int port, byte[] mac, int type, com.google.gson.JsonObject state) {
                fail("Unavailable oven must not receive commands"); return null;
            }
        };
        var oven = new BSHOven(adapter, "test", "oven", "example.invalid", 80, new byte[6], false);
        try { oven.initialize(); assertTrue(attempts.await(3, TimeUnit.SECONDS)); }
        finally { oven.shutdown(); registry.getEventBus().close(); }
    }
}
