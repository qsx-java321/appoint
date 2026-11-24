package com.qsx.appoint.user.avatar;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 头像格式、大小和尺寸校验，服务端不信任客户端文件名和 MIME。 */
class AvatarImageValidatorTest {

    @Test
    void acceptsPngAndJpegByMagicBytes() throws Exception {
        AvatarImageValidator validator = validator(2 * 1024 * 1024, 4096);

        ValidatedAvatar png = validator.validate(file("avatar.txt", "text/plain", image("png")));
        ValidatedAvatar jpeg = validator.validate(file("avatar", "application/octet-stream", image("jpg")));

        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.extension()).isEqualTo("png");
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.extension()).isEqualTo("jpg");
    }

    @Test
    void acceptsWebpByContainerHeader() {
        byte[] webp = new byte[20];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
        System.arraycopy("VP8 ".getBytes(StandardCharsets.US_ASCII), 0, webp, 12, 4);

        ValidatedAvatar result = validator(2 * 1024 * 1024, 4096)
                .validate(file("avatar.webp", "image/webp", webp));

        assertThat(result.contentType()).isEqualTo("image/webp");
        assertThat(result.extension()).isEqualTo("webp");
    }

    @Test
    void rejectsEmptyNonImageAndSvg() {
        AvatarImageValidator validator = validator(2 * 1024 * 1024, 4096);

        assertInvalid(() -> validator.validate(file("empty.png", "image/png", new byte[0])));
        assertInvalid(() -> validator.validate(file("note.png", "image/png", "hello".getBytes())));
        assertInvalid(() -> validator.validate(file("icon.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes())));
    }

    @Test
    void rejectsOversizedFileAndImageDimensions() throws Exception {
        byte[] png = image("png");
        assertInvalid(() -> validator(png.length - 1L, 4096)
                .validate(file("big.png", "image/png", png)));

        byte[] wide = image("png", 20, 1);
        assertInvalid(() -> validator(2 * 1024 * 1024, 10)
                .validate(file("wide.png", "image/png", wide)));
    }

    @Test
    void rejectsTruncatedImage() {
        byte[] truncated = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        assertInvalid(() -> validator(2 * 1024 * 1024, 4096)
                .validate(file("broken.png", "image/png", truncated)));
    }

    private AvatarImageValidator validator(long maxBytes, int maxDimension) {
        AvatarProperties properties = new AvatarProperties();
        properties.setMaxBytes(maxBytes);
        properties.setMaxDimension(maxDimension);
        return new AvatarImageValidator(properties);
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private byte[] image(String format) throws Exception {
        return image(format, 2, 2);
    }

    private byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AVATAR_INVALID);
    }
}
