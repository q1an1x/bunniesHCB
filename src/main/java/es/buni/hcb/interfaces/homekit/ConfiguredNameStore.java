package es.buni.hcb.interfaces.homekit;

import es.buni.hcb.utils.PrivateFiles;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ConfiguredNameStore {
    private static ConfiguredNameStore defaultStore;
    private static Path defaultPath = Path.of("configured-names.properties");
    private final Path file;
    private Properties properties = new Properties();

    public static synchronized void configureDefault(Path path) {
        defaultPath = path.toAbsolutePath().normalize();
        defaultStore = null;
    }
    public static synchronized ConfiguredNameStore getDefault() {
        try { return getDefaultInternal(); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static synchronized ConfiguredNameStore getDefaultInternal() throws IOException {
        if (defaultStore == null) defaultStore = new ConfiguredNameStore(defaultPath);
        return defaultStore;
    }
    public ConfiguredNameStore(Path file) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        if (Files.exists(this.file)) try (var in = Files.newInputStream(this.file)) { properties.load(in); }
    }
    public synchronized String get(String key) { return properties.getProperty(key, key); }
    public synchronized void set(String key, String name) throws IOException {
        Objects.requireNonNull(key); Objects.requireNonNull(name);
        Properties next = new Properties(); next.putAll(properties); next.setProperty(key, name);
        var bytes = new ByteArrayOutputStream();
        next.store(bytes, "Configured names");
        PrivateFiles.writeAtomically(file, bytes.toByteArray());
        properties = next;
    }
}
