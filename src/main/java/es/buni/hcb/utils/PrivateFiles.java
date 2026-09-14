package es.buni.hcb.utils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;

/** Write a private replacement, then atomically replace the old file or fail without changing it. */
public final class PrivateFiles {
    private PrivateFiles() { }
    public static void writeAtomically(Path destination, byte[] bytes) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) throw new IOException("Refusing to replace a symbolic-link state file");
        Files.createDirectories(target.getParent());
        Path temp;
        boolean posix = Files.getFileStore(target.getParent()).supportsFileAttributeView("posix");
        if (posix) temp = Files.createTempFile(target.getParent(), ".hcb-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        else temp = Files.createTempFile(target.getParent(), ".hcb-", ".tmp");
        try {
            try (var channel = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
