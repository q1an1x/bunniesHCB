package es.buni.hcb.interfaces.homekit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.Properties;

public class ConfiguredNameStore {

    private final Path file;
    private final Properties properties = new Properties();

    public static ConfiguredNameStore getDefault() {
        try {
            return getDefaultInternal();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize ConfiguredNameStore", e);
        }
    }

    public static ConfiguredNameStore getDefaultInternal() throws IOException {
        return new ConfiguredNameStore(Paths.get("configured-names.properties"));
    }

    public ConfiguredNameStore(Path file) throws IOException {
        this.file = Objects.requireNonNull(file);

        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            }
        } else {
            Files.createDirectories(file.getParent());
            Files.createFile(file);
        }
    }

    public synchronized String get(String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return key;
        }

        return value;
    }

    public synchronized void set(String key, String name) throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);

        properties.setProperty(key, name);
        persist();
    }

    private void persist() throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        try (OutputStream out = Files.newOutputStream(
                tmp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            properties.store(out, "ConfiguredNameStore");
        }

        Files.move(
                tmp,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }
}
