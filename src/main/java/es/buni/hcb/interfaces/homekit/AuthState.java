package es.buni.hcb.interfaces.homekit;

import java.math.BigInteger;
import java.util.Map;
import java.io.Serializable;
import java.util.HashMap;

public class AuthState implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String PIN;
    public final String mac;
    public final BigInteger salt;
    public final byte[] privateKey;
    public final String setupId;

    public final Map<String, byte[]> userKeyMap;

    public AuthState(String PIN, String mac, BigInteger salt, byte[] privateKey, String setupId) {
        this(PIN, mac, salt, privateKey, setupId, new HashMap<>());
    }

    public AuthState(String PIN, String mac, BigInteger salt, byte[] privateKey, String setupId,
                     Map<String, byte[]> userKeyMap) {
        this.PIN = PIN;
        this.mac = mac;
        this.salt = salt;
        this.privateKey = privateKey;
        this.setupId = setupId;
        this.userKeyMap = userKeyMap;
    }
}
