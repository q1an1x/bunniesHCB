package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Parse and validate all options before any network or device operation. */
public record RuntimeOptions(KnxSettings knx, String localAddress, Path stateDir, Path inventory,
                             String haHost, String haToken, Path haTokenFile, boolean homekit,
                             boolean oven, boolean ovenHomekit, String ovenHost, String ovenMac,
                             ZoneId zone, long observeSeconds, Duration manualHold,
                             es.buni.hcb.automation.modes.HouseMode explainMode, boolean debug, boolean help) {
    public static RuntimeOptions parse(String[] args, Map<String, String> environment) {
        var values = new HashMap<String, String>();
        var flags = new HashSet<String>();
        var flagNames = Set.of("help", "debug", "knx-nat", "disable-homekit", "enable-oven", "oven-homekit",
                "enable-automations", "enable-time-service");
        var valueNames = Set.of("mode", "knx-gateway", "knx-port", "local-address", "state-dir", "inventory",
                "ha-host", "ha-token", "ha-token-file", "oven-host", "oven-mac", "timezone", "observe-seconds",
                "knx-silence-seconds", "manual-hold-minutes", "explain-mode");
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Expected an option name");
            String option = args[i].substring(2);
            if (flagNames.contains(option)) flags.add(option);
            else if (valueNames.contains(option)) {
                if (i + 1 >= args.length || args[i+1].startsWith("--")) throw new IllegalArgumentException("Missing value for --" + option);
                if (values.putIfAbsent(option, args[++i]) != null) throw new IllegalArgumentException("Duplicate --" + option);
            } else throw new IllegalArgumentException("Unknown option: --" + option);
        }
        KnxMode mode;
        try { mode = KnxMode.valueOf(values.getOrDefault("mode", "offline").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("--mode must be offline, observe, or live"); }
        var settings = new KnxSettings(mode, values.get("knx-gateway"), integer(values, "knx-port", 3671), flags.contains("knx-nat"),
                Duration.ofSeconds(2), Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofSeconds(60),
                Duration.ofSeconds(integer(values, "knx-silence-seconds", 45)),
                flags.contains("enable-automations"), flags.contains("enable-time-service"));
        String host = values.getOrDefault("ha-host", environment.get("HCB_HA_HOST"));
        String token = values.getOrDefault("ha-token", environment.get("HCB_HA_TOKEN"));
        Path tokenFile = path(values.getOrDefault("ha-token-file", environment.get("HCB_HA_TOKEN_FILE")));
        if (token != null && tokenFile != null) throw new IllegalArgumentException("Use one HA token source");
        if (mode == KnxMode.LIVE && ((host == null) != (token == null && tokenFile == null)))
            throw new IllegalArgumentException("Configure both HA host and token, or neither");
        boolean oven = flags.contains("enable-oven") || flags.contains("oven-homekit");
        String ovenHost = values.get("oven-host"), ovenMac = values.get("oven-mac");
        if (oven && (ovenHost == null || ovenMac == null || !ovenMac.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")))
            throw new IllegalArgumentException("Oven requires --oven-host and a valid --oven-mac");
        long seconds = integer(values, "observe-seconds", 0);
        if (seconds < 0 || (seconds > 0 && mode != KnxMode.OBSERVE)) throw new IllegalArgumentException("--observe-seconds requires observe mode");
        Path inventory = path(values.get("inventory"));
        if (inventory != null && mode != KnxMode.OFFLINE) throw new IllegalArgumentException("--inventory requires offline mode");
        int holdMinutes = integer(values, "manual-hold-minutes", 30);
        if (holdMinutes < 1 || holdMinutes > 1440) throw new IllegalArgumentException("Manual hold must be 1..1440 minutes");
        var explain = values.containsKey("explain-mode") ? es.buni.hcb.automation.modes.HouseMode.parse(values.get("explain-mode")) : null;
        if (explain != null && mode != KnxMode.OFFLINE) throw new IllegalArgumentException("--explain-mode requires offline mode");
        return new RuntimeOptions(settings, values.get("local-address"), Path.of(values.getOrDefault("state-dir", ".")),
                inventory, host, token, tokenFile, !flags.contains("disable-homekit"), oven, flags.contains("oven-homekit"),
                ovenHost, ovenMac, ZoneId.of(values.getOrDefault("timezone", "Asia/Shanghai")), seconds, Duration.ofMinutes(holdMinutes), explain,
                flags.contains("debug"), flags.contains("help"));
    }
    private static int integer(Map<String, String> values, String key, int fallback) {
        try { return Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer for --" + key); }
    }
    private static Path path(String value) { return value == null ? null : Path.of(value); }
    public String resolvedHaToken() throws java.io.IOException {
        String token = haTokenFile == null ? haToken : Files.readString(haTokenFile).strip();
        if (token != null && token.isBlank()) throw new IllegalArgumentException("HA token is empty");
        return token;
    }
    @Override public String toString() { return "RuntimeOptions[mode=" + knx.mode() + "]"; }
}
