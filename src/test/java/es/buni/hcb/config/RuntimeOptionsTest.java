package es.buni.hcb.config;
import es.buni.hcb.adapters.knx.KnxMode;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeOptionsTest {
    @Test void defaultsAreOfflineWithExplicitFeatureOptIns() {
        var options = RuntimeOptions.parse(new String[]{},Map.of());
        assertEquals(KnxMode.OFFLINE,options.knx().mode()); assertFalse(options.oven());
        assertFalse(options.knx().automationsEnabled()); assertFalse(options.knx().timeServiceEnabled());
    }
    @Test void debugDoesNotSkipLaterArguments() {
        var options = RuntimeOptions.parse(new String[]{"--debug","--oven-homekit","--oven-host","fake.invalid","--oven-mac","00:00:00:00:00:01"},Map.of());
        assertTrue(options.debug()); assertTrue(options.ovenHomekit());
    }
    @Test void invalidOptionsFailBeforeNetwork() {
        for (String[] args : new String[][]{{"--mode","live"},{"--mode","typo"},{"--unknown"},{"--ha-host"},{"--knx-silence-seconds","0"}})
            assertThrows(IllegalArgumentException.class, () -> RuntimeOptions.parse(args,Map.of()));
    }
    @Test void tokensAreLoadedFromEnvironmentWithoutAppearingInDiagnostics() {
        var options = RuntimeOptions.parse(new String[]{"--mode","live","--knx-gateway","fake.invalid"}, Map.of("HCB_HA_HOST","fake.invalid","HCB_HA_TOKEN","unit-test-secret"));
        assertEquals("unit-test-secret", options.haToken()); assertFalse(options.toString().contains("unit-test-secret"));
    }
}
