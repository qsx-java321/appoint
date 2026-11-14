package com.qsx.appoint.localenv;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/** 不记录秘密；test显式重置原子轮换密钥，旧文件不进入版本库。 */
final class LocalEnvironmentFiles {
    private LocalEnvironmentFiles() {}
    static void rotateTestJwt(String secret) {
        Path file = Path.of(".env.test");
        try {
            List<String> lines = Files.readAllLines(file);
            lines.removeIf(line -> line.startsWith("JWT_SECRET=")); lines.add("JWT_SECRET=" + secret);
            Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".test-jwt-", ".tmp");
            try {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
                Files.write(temporary, lines); Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception e) { throw new IllegalStateException("test密钥轮换失败"); }
    }
}
