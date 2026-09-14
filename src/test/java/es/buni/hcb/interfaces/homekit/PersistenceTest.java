package es.buni.hcb.interfaces.homekit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path directory;
    @Test void relativeNamesPathWorksAndDefaultStoreIsShared() throws Exception {
        var path = directory.resolve("names.properties");
        ConfiguredNameStore.configureDefault(path);
        assertSame(ConfiguredNameStore.getDefault(),ConfiguredNameStore.getDefault());
        try (var workers = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<?>>();
            for (int i=0;i<30;i++) { int id=i; futures.add(workers.submit(() -> { try { ConfiguredNameStore.getDefault().set("lamp"+id,"name"+id); } catch(IOException e){throw new UncheckedIOException(e);} })); }
            for(var future:futures) future.get();
        }
        var reloaded = new ConfiguredNameStore(path);
        for(int i=0;i<30;i++) assertEquals("name"+i,reloaded.get("lamp"+i));
        new ConfiguredNameStore(Path.of("unused-relative-names.properties")); // Does not create a file during reads.
    }
    @Test void failedNameSaveDoesNotUpdateMemory() throws Exception {
        Path file = directory.resolve("names"); var store = new ConfiguredNameStore(file); Files.createDirectory(file);
        assertThrows(IOException.class, () -> store.set("lamp","changed")); assertEquals("lamp",store.get("lamp"));
    }
    @Test void legacyPairingsSurviveAndKeyArraysAreNotMutableThroughGetters() throws Exception {
        Path file = directory.resolve("auth.bin"); byte[] key = new byte[32]; key[0]=12;
        var legacy = new AuthState("123-45-678","00:00:00:00:00:01",BigInteger.valueOf(111),key,"ABCD",new HashMap<>(Map.of("phone",key)));
        try(var output=new ObjectOutputStream(Files.newOutputStream(file))){output.writeObject(legacy);}
        byte[] original=Files.readAllBytes(file); var auth=new PersistentAuthInfo(file.toFile());
        assertEquals("123-45-678",auth.getPin()); assertArrayEquals(original,Files.readAllBytes(file));
        byte[] returned=auth.getPrivateKey(); returned[0]=99; assertEquals(12,auth.getPrivateKey()[0]);
        auth.createUser("tablet",new byte[32]); var reloaded=new PersistentAuthInfo(file.toFile());
        assertArrayEquals(key,reloaded.getUserPublicKey("phone")); assertNotNull(reloaded.getUserPublicKey("tablet"));
        assertEquals(legacy.mac,reloaded.getMac());
        if(Files.getFileStore(file).supportsFileAttributeView("posix")) assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(file));
    }
    @Test void corruptIdentityIsNeverReplacedWithANewPairingIdentity() throws Exception {
        Path file=directory.resolve("auth.bin"); byte[] broken={1,2,3}; Files.write(file,broken);
        assertThrows(Exception.class, () -> new PersistentAuthInfo(file.toFile())); assertArrayEquals(broken,Files.readAllBytes(file));
    }
    @Test void unrelatedSerializedObjectsAreRejected() throws Exception {
        Path file=directory.resolve("auth.bin");
        try(var output=new ObjectOutputStream(Files.newOutputStream(file))){output.writeObject(new ArrayList<>());}
        assertThrows(Exception.class, () -> new PersistentAuthInfo(file.toFile()));
    }
}
