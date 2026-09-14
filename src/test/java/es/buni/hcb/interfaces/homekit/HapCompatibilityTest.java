package es.buni.hcb.interfaces.homekit;

import io.github.hapjava.server.impl.HomekitServer;
import io.github.hapjava.server.impl.crypto.*;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Dependency compatibility without opening a HomeKit listener or using real identities. */
class HapCompatibilityTest {
    @TempDir Path directory;

    @Test void identityGenerationAndSigningRemainCompatible() throws Exception {
        var auth = new PersistentAuthInfo(directory.resolve("auth.bin").toFile());
        byte[] key = auth.getPrivateKey();
        var signer = new EdsaSigner(key);
        var verifier = new EdsaVerifier(signer.getPublicKey());
        byte[] message = "synthetic pairing material".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.sign(message);
        assertTrue(verifier.verify(message, signature));
        message[0] ^= 1;
        assertFalse(verifier.verify(message, signature));
        assertArrayEquals(key, new PersistentAuthInfo(directory.resolve("auth.bin").toFile()).getPrivateKey());
    }

    @Test void hapEncryptionAuthenticatesBothPayloadAndAdditionalData() throws Exception {
        byte[] key = HomekitServer.generateKey(), nonce = new byte[8], aad = {12, 0};
        byte[] message = "test payload".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = new ChachaEncoder(key, nonce).encodeCiphertext(message, aad);
        byte[] ciphertext = Arrays.copyOf(encrypted, message.length);
        byte[] tag = Arrays.copyOfRange(encrypted, message.length, encrypted.length);
        assertArrayEquals(message, new ChachaDecoder(key, nonce).decodeCiphertext(tag, aad, ciphertext));
        ciphertext[0] ^= 1;
        assertThrows(java.io.IOException.class, () -> new ChachaDecoder(key, nonce).decodeCiphertext(tag, aad, ciphertext));
        ciphertext[0] ^= 1; aad[0] ^= 1;
        assertThrows(java.io.IOException.class, () -> new ChachaDecoder(key, nonce).decodeCiphertext(tag, aad, ciphertext));
    }

    @Test void nettyDecodesAFragmentedHomekitHttpRequest() {
        var channel = new EmbeddedChannel(new HttpServerCodec(), new HttpObjectAggregator(4096));
        try {
            assertFalse(channel.writeInbound(Unpooled.copiedBuffer("GET /accessories HTTP/1.1\r\nHost: test\r\n", StandardCharsets.US_ASCII)));
            assertTrue(channel.writeInbound(Unpooled.copiedBuffer("\r\n", StandardCharsets.US_ASCII)));
            FullHttpRequest request = channel.readInbound();
            try { assertEquals("/accessories", request.uri()); assertTrue(request.decoderResult().isSuccess()); }
            finally { request.release(); }
        } finally { channel.finishAndReleaseAll(); }
    }
}
