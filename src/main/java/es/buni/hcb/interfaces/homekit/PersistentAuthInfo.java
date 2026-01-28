package es.buni.hcb.interfaces.homekit;

import es.buni.hcb.utils.Logger;
import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.HomekitServer;
import io.github.hapjava.server.impl.crypto.HAPSetupCodeUtils;

import java.io.*;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.util.function.Consumer;

public class PersistentAuthInfo implements HomekitAuthInfo {

    private final File storageFile;
    private AuthState state;

    private Consumer<AuthState> onChangeCallback;

    public PersistentAuthInfo(File storageFile) throws Exception {
        this.storageFile = storageFile;
        loadOrCreate();
    }

    private void loadOrCreate() throws Exception {
        if (storageFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(storageFile))) {
                state = (AuthState) ois.readObject();
                Logger.info("Loaded persisted HomeKit auth state.");
            }
        } else {
            state = generateNewState();
            saveState();
            Logger.info("Generated new HomeKit auth state. PIN: " + state.PIN);
        }
    }

    private AuthState generateNewState() throws InvalidAlgorithmParameterException {
        String pin = generatePin();
        String mac = HomekitServer.generateMac();
        BigInteger salt = HomekitServer.generateSalt();
        byte[] privateKey = HomekitServer.generateKey();
        String setupId = HAPSetupCodeUtils.generateSetupId();
        return new AuthState(pin, mac, salt, privateKey, setupId);
    }

    private static String generatePin() {
        SecureRandom r = new SecureRandom();
        int a = r.nextInt(900) + 100;
        int b = r.nextInt(90) + 10;
        int c = r.nextInt(900) + 100;
        return a + "-" + b + "-" + c;
    }

    public synchronized void saveState() {
        try {
            File tmp = new File(storageFile.getAbsolutePath() + ".tmp");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmp))) {
                oos.writeObject(state);
                oos.flush();
            }
            tmp.renameTo(storageFile);

            if (onChangeCallback != null) {
                onChangeCallback.accept(state);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void onChange(Consumer<AuthState> callback) {
        this.onChangeCallback = callback;
        callback.accept(state);
    }

    @Override
    public String getPin() {
        return state.PIN;
    }

    @Override
    public String getMac() {
        return state.mac;
    }

    @Override
    public BigInteger getSalt() {
        return state.salt;
    }

    @Override
    public byte[] getPrivateKey() {
        return state.privateKey;
    }

    @Override
    public String getSetupId() {
        return state.setupId;
    }

    @Override
    public void createUser(String username, byte[] publicKey) {
        if (!state.userKeyMap.containsKey(username)) {
            state.userKeyMap.put(username, publicKey);
            saveState();
            Logger.info("Added HomeKit pairing for " + username);
        }
    }

    @Override
    public void removeUser(String username) {
        if (state.userKeyMap.remove(username) != null) {
            saveState();
            Logger.info("Removed HomeKit pairing for " + username);
        }
    }

    @Override
    public byte[] getUserPublicKey(String username) {
        return state.userKeyMap.get(username);
    }

    @Override
    public boolean hasUser() {
        return !state.userKeyMap.isEmpty();
    }
}
