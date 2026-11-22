package com.qsx.appoint.localenv;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEnvironmentFilesTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void rotatesOnlyTheSelectedEnvironmentsJwtSecret(String name) throws Exception {
        Path selected = directory.resolve(".env." + name);
        Path other = directory.resolve(name.equals("dev") ? ".env.test" : ".env.dev");
        Files.writeString(selected, "# local settings\nJWT_SECRET=old-fixture\nMYSQL_USER=fixture\n");
        Files.writeString(other, "JWT_SECRET=other-fixture\n");

        LocalEnvironmentFiles.rotateJwt(selected, "new-fixture");

        assertThat(Files.readAllLines(selected)).containsExactly("# local settings", "MYSQL_USER=fixture", "JWT_SECRET=new-fixture");
        assertThat(Files.readAllLines(other)).containsExactly("JWT_SECRET=other-fixture");
        assertThat(Files.getPosixFilePermissions(selected)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
}
