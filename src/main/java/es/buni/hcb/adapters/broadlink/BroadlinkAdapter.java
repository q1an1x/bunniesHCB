package es.buni.hcb.adapters.broadlink;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.utils.Debug;
import es.buni.hcb.utils.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class BroadlinkAdapter extends Adapter {

    private static final String INIT_KEY = "097628343fe99e23765c1513accf8b02";
    private static final String INIT_VECT = "562e17996d093d28ddb3ba695a2e6f58";

    private static final int DEFAULT_TIMEOUT = 2000;
    private static final int RETRY_DELAY_MS = 200;
    private static final int MAX_RETRIES = 2;
    private static final int RATE_LIMIT_MS = 1000;

    private final Gson gson = new Gson();

    private SecretKeySpec aesKey;
    private IvParameterSpec iv;
    private int deviceId = 0;
    private int count;

    private final ReentrantLock queueLock = new ReentrantLock(true);
    private long lastRequestTime = 0;

    public BroadlinkAdapter(EntityRegistry registry) {
        super("broadlink", registry);
        this.count = 0x8000 + new Random().nextInt(0x8000);
        this.iv = new IvParameterSpec(hexToBytes(INIT_VECT));
        updateAesKey(hexToBytes(INIT_KEY));
    }

    private void updateAesKey(byte[] key) {
        this.aesKey = new SecretKeySpec(key, "AES");
    }

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private byte[] encrypt(byte[] payload) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
            return cipher.doFinal(payload);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private byte[] decrypt(byte[] payload) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
            return cipher.doFinal(payload);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public boolean authenticate(String host, int port, byte[] mac, int devtype) throws IOException {
        deviceId = 0;
        updateAesKey(hexToBytes(INIT_KEY));

        byte[] packet = new byte[0x50];
        Arrays.fill(packet, 0x04, 0x14, (byte) 0x31);
        packet[0x1E] = 0x01;
        packet[0x2D] = 0x01;
        System.arraycopy("Test 1".getBytes(StandardCharsets.UTF_8), 0, packet, 0x30, 6);

        byte[] response = sendPacket(host, port, mac, devtype, 0x65, packet);
        checkError(response);

        byte[] payload = decrypt(Arrays.copyOfRange(response, 0x38, response.length));

        deviceId = ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] newKey = Arrays.copyOfRange(payload, 0x04, 0x14);
        updateAesKey(newKey);

        Logger.info("Broadlink authenticated, device ID: " + deviceId);
        return true;
    }

    public byte[] sendPacket(String host, int port, byte[] mac, int devtype, int packetType, byte[] payload) throws IOException {
        queueLock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            long timeSinceLast = currentTime - lastRequestTime;

            if (timeSinceLast < RATE_LIMIT_MS) {
                try {
                    long sleepTime = RATE_LIMIT_MS - timeSinceLast;
                    if (Debug.ENABLED) {
                        Logger.info("Broadlink rate limit: sleeping for " + sleepTime + "ms");
                    }
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for rate limit", ie);
                }
            }

            byte[] pk = constructPacket(mac, devtype, packetType, payload);
            InetAddress address = InetAddress.getByName(host);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(DEFAULT_TIMEOUT);

                int attempt = 0;
                while (attempt < MAX_RETRIES) {
                    try {
                        if (Debug.ENABLED && attempt > 0) {
                            Logger.info("Retry attempt " + attempt + " for " + host);
                        }

                        DatagramPacket sendPacket = new DatagramPacket(pk, pk.length, address, port);
                        socket.send(sendPacket);

                        byte[] receiveBuffer = new byte[2048];
                        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                        socket.receive(receivePacket);

                        return Arrays.copyOf(receiveBuffer, receivePacket.getLength());

                    } catch (Exception e) {
                        attempt++;
                        if (attempt >= MAX_RETRIES) {
                            throw e;
                        }

                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted during retry delay", ie);
                        }
                    }
                }
            }
            throw new IOException("Failed to send packet after " + MAX_RETRIES + " attempts");

        } finally {
            lastRequestTime = System.currentTimeMillis();
            queueLock.unlock();
        }
    }

    private byte[] constructPacket(byte[] mac, int devtype, int packetType, byte[] payload) {
        count = ((count + 1) | 0x8000) & 0xFFFF;

        byte[] packet = new byte[0x38];
        System.arraycopy(hexToBytes("5aa5aa555aa5aa55"), 0, packet, 0x00, 8);

        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0x24, (short) devtype)
                .putShort(0x26, (short) packetType)
                .putShort(0x28, (short) count)
                .putInt(0x30, deviceId);

        byte[] macReversed = new byte[6];
        for (int i = 0; i < 6; i++) {
            macReversed[i] = mac[5 - i];
        }
        System.arraycopy(macReversed, 0, packet, 0x2A, 6);

        int payloadChecksum = 0xBEAF;
        for (byte b : payload) {
            payloadChecksum += (b & 0xFF);
        }
        payloadChecksum &= 0xFFFF;
        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putShort(0x34, (short) payloadChecksum);

        int padding = (16 - payload.length % 16) % 16;
        byte[] paddedPayload = Arrays.copyOf(payload, payload.length + padding);
        byte[] encryptedPayload = encrypt(paddedPayload);

        byte[] pk = Arrays.copyOf(packet, packet.length + encryptedPayload.length);
        System.arraycopy(encryptedPayload, 0, pk, packet.length, encryptedPayload.length);

        int checksum = 0xBEAF;
        for (int i = 0; i < pk.length; i++) {
            if (i < 0x20 || i >= 0x22) {
                checksum += (pk[i] & 0xFF);
            }
        }
        checksum &= 0xFFFF;
        ByteBuffer.wrap(pk).order(ByteOrder.LITTLE_ENDIAN).putShort(0x20, (short) checksum);

        return pk;
    }

    private void checkError(byte[] response) {
        if (response.length < 0x24) {
            throw new RuntimeException("Response too short");
        }

        int errorCode = ByteBuffer.wrap(response, 0x22, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        if (errorCode != 0) {
            throw new RuntimeException("Device error: " + errorCode);
        }
    }

    public JsonObject getDeviceState(String host, int port, byte[] mac, int devtype) throws IOException {
        byte[] payload = encodePayload(1, new JsonObject());
        byte[] response = sendPacket(host, port, mac, devtype, 0x6A, payload);
        checkError(response);
        return decodePayload(response);
    }

    public JsonObject setDeviceState(String host, int port, byte[] mac, int devtype, JsonObject state) throws IOException {
        if (Debug.ENABLED) {
            Logger.info("Oven: sending " + state);
        }

        byte[] payload = encodePayload(2, state);
        byte[] response = sendPacket(host, port, mac, devtype, 0x6A, payload);
        checkError(response);
        return decodePayload(response);
    }

    private byte[] encodePayload(int flag, JsonObject state) {
        String json = gson.toJson(state);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        int length = 12 + jsonBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(14 + jsonBytes.length).order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort((short) length);
        buffer.putShort((short) 0xA5A5);
        buffer.putShort((short) 0x5A5A);
        buffer.putShort((short) 0x0000);
        buffer.put((byte) flag);
        buffer.put((byte) 0x0B);
        buffer.putInt(jsonBytes.length);
        buffer.put(jsonBytes);

        byte[] packet = buffer.array();
        int checksum = 0xBEAF;
        for (int i = 2; i < packet.length; i++) {
            checksum += (packet[i] & 0xFF);
        }
        checksum &= 0xFFFF;

        ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).putShort(0x06, (short) checksum);
        return packet;
    }

    private JsonObject decodePayload(byte[] response) {
        byte[] decrypted = decrypt(Arrays.copyOfRange(response, 0x38, response.length));
        int jsonLength = ByteBuffer.wrap(decrypted, 0x0A, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        String json = new String(decrypted, 0x0E, jsonLength, StandardCharsets.UTF_8);

        if (Debug.ENABLED) {
            Logger.info("Received " + json);
        }

        return gson.fromJson(json, JsonObject.class);
    }
}