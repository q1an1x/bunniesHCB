package es.buni.hcb.interfaces.homekit;

import es.buni.hcb.utils.Logger;
import es.buni.hcb.utils.PrivateFiles;
import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.HomekitServer;
import io.github.hapjava.server.impl.crypto.HAPSetupCodeUtils;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Consumer;

/** Retains the original AuthState serialization format and all existing pairing identities. */
public final class PersistentAuthInfo implements HomekitAuthInfo {
    private final Path storage;
    private AuthState state;
    private Consumer<AuthState> callback;

    public PersistentAuthInfo(File file) throws Exception {
        storage = file.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(storage)) throw new IOException("Symbolic-link auth state is not supported");
        if (Files.exists(storage)) {
            if (Files.size(storage) > 1_048_576) throw new IOException("Auth state is too large");
            try (var input = new ObjectInputStream(Files.newInputStream(storage))) {
                input.setObjectInputFilter(PersistentAuthInfo::filter);
                Object loaded = input.readObject();
                if (!(loaded instanceof AuthState auth)) throw new IOException("Invalid auth state type");
                validate(auth);
                state = copy(auth);
            }
            Logger.info("Loaded existing HomeKit identity and pairings");
        } else {
            var random = new SecureRandom();
            String pin = (random.nextInt(900) + 100) + "-" + (random.nextInt(90) + 10) + "-" + (random.nextInt(900) + 100);
            state = new AuthState(pin, HomekitServer.generateMac(), HomekitServer.generateSalt(),
                    HomekitServer.generateKey(), HAPSetupCodeUtils.generateSetupId());
            saveState();
            Logger.info("Created private HomeKit identity file; pairing code omitted from logs");
        }
    }
    private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 20 || info.references() > 4096 || info.streamBytes() > 1_048_576 || info.arrayLength() > 4096)
            return ObjectInputFilter.Status.REJECTED;
        Class<?> c = info.serialClass();
        if (c == null) return ObjectInputFilter.Status.UNDECIDED;
        if (c == AuthState.class || c == HashMap.class || c == String.class || c == BigInteger.class || c == Number.class
                || c == byte[].class || (c.isArray() && c.getComponentType() == Map.Entry.class)) return ObjectInputFilter.Status.ALLOWED;
        return ObjectInputFilter.Status.REJECTED;
    }
    private static void validate(AuthState value) throws IOException {
        if (value.PIN == null || value.mac == null || value.salt == null || value.privateKey == null
                || value.privateKey.length == 0 || value.setupId == null || value.userKeyMap == null)
            throw new IOException("Incomplete HomeKit identity; restore the backup, do not re-pair");
        for (var entry : value.userKeyMap.entrySet()) if (entry.getKey() == null || entry.getValue() == null)
            throw new IOException("Invalid HomeKit pairing");
    }
    private static AuthState copy(AuthState value) {
        Map<String, byte[]> keys = new HashMap<>();
        value.userKeyMap.forEach((key, bytes) -> keys.put(key, bytes.clone()));
        return new AuthState(value.PIN, value.mac, value.salt, value.privateKey.clone(), value.setupId, keys);
    }
    private void persist(AuthState next) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new ObjectOutputStream(bytes)) { output.writeObject(next); }
            PrivateFiles.writeAtomically(storage, bytes.toByteArray());
        } catch (IOException e) { throw new UncheckedIOException("Could not persist HomeKit pairing", e); }
        state = next;
        if (callback != null) callback.accept(copy(state));
    }
    public synchronized void saveState() { persist(copy(state)); }
    public synchronized void onChange(Consumer<AuthState> value) { callback = value; callback.accept(copy(state)); }
    @Override public synchronized String getPin() { return state.PIN; }
    @Override public synchronized String getMac() { return state.mac; }
    @Override public synchronized BigInteger getSalt() { return state.salt; }
    @Override public synchronized byte[] getPrivateKey() { return state.privateKey.clone(); }
    @Override public synchronized String getSetupId() { return state.setupId; }
    @Override public synchronized void createUser(String name, byte[] publicKey) {
        if (state.userKeyMap.containsKey(name)) return;
        AuthState next = copy(state); next.userKeyMap.put(Objects.requireNonNull(name), publicKey.clone()); persist(next);
    }
    @Override public synchronized void removeUser(String name) {
        if (!state.userKeyMap.containsKey(name)) return;
        AuthState next = copy(state); next.userKeyMap.remove(name); persist(next);
    }
    @Override public synchronized byte[] getUserPublicKey(String name) {
        byte[] key = state.userKeyMap.get(name); return key == null ? null : key.clone();
    }
    @Override public synchronized boolean hasUser() { return !state.userKeyMap.isEmpty(); }
}
