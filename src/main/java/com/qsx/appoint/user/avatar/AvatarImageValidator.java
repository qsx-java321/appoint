package com.qsx.appoint.user.avatar;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 只接受 JPEG、PNG 和 WebP，使用文件头而不是客户端声明的 MIME 类型判定格式。
 * JPEG/PNG 同时读取图片头部尺寸，避免小文件解压成超大图片。
 */
@Component
@RequiredArgsConstructor
public class AvatarImageValidator {
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_SIGNATURE = {0x57, 0x45, 0x42, 0x50};

    private final AvatarProperties properties;

    public ValidatedAvatar validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > properties.getMaxBytes()) {
            throw invalid();
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw invalid();
        }
        if (content.length == 0 || content.length > properties.getMaxBytes()) {
            throw invalid();
        }
        ImageFormat format = detect(content);
        if (format == null) {
            throw invalid();
        }
        checkDimensions(content, format);
        return new ValidatedAvatar(content, format.contentType, format.extension);
    }

    private ImageFormat detect(byte[] content) {
        if (startsWith(content, JPEG_SIGNATURE)) {
            return ImageFormat.JPEG;
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return ImageFormat.PNG;
        }
        if (content.length >= 12
                && startsWith(content, RIFF_SIGNATURE)
                && startsWith(content, 8, WEBP_SIGNATURE)) {
            return ImageFormat.WEBP;
        }
        return null;
    }

    private void checkDimensions(byte[] content, ImageFormat format) {
        // JDK 自带 ImageIO 没有 WebP reader，WebP 只做文件头和大小校验。
        if (format == ImageFormat.WEBP) {
            return;
        }
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > properties.getMaxDimension() || height > properties.getMaxDimension()) {
                    throw invalid();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalid();
        }
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        return startsWith(content, 0, prefix);
    }

    private boolean startsWith(byte[] content, int offset, byte[] prefix) {
        if (content.length < offset + prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (content[offset + index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.AVATAR_INVALID);
    }

    private enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }
}
