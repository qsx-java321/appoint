package com.qsx.appoint.localenv;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/** 显式重置原子更新当前环境的私密配置。 */
final class LocalEnvironmentFiles {
    private LocalEnvironmentFiles() {}

    static void rotateJwt(Path file, String secret) {
        try {
            List<String> lines = Files.readAllLines(file);
            lines.removeIf(line -> line.startsWith("JWT_SECRET="));
            lines.add("JWT_SECRET=" + secret);
            Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".jwt-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try {
                Files.write(temporary, lines);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception e) { throw new IllegalStateException("环境密钥轮换失败"); }
    }
}
