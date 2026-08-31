package com.chatroom.service;

import com.chatroom.dto.response.FileUploadResponse;
import com.chatroom.exception.BusinessException;
import com.chatroom.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int INSPECTION_BYTES = 8192;
    private static final Pattern STORED_FILE_NAME = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\\.(jpg|png|gif|webp|pdf|doc|docx|txt|zip|rar|7z)$"
    );

    private final Path uploadPath;

    public FileService(@Value("${file.upload-dir:uploads}") String uploadDir) throws IOException {
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
    }

    public FileUploadResponse upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "文件大小不能超过 50MB");
        }

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        AllowedFileType allowedType = AllowedFileType.forExtension(extension)
                .orElseThrow(() -> BusinessException.badRequest("不支持的文件扩展名"));

        String declaredContentType = Optional.ofNullable(file.getContentType())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse("");
        if (!allowedType.acceptsContentType(declaredContentType)) {
            throw BusinessException.badRequest("文件扩展名与声明类型不匹配");
        }

        byte[] inspectionBytes;
        try (InputStream input = file.getInputStream()) {
            inspectionBytes = input.readNBytes(INSPECTION_BYTES);
        }
        if (!allowedType.matchesSignature(inspectionBytes)) {
            throw BusinessException.badRequest("文件真实类型与扩展名不匹配");
        }

        String storedName = UUID.randomUUID() + "." + allowedType.canonicalExtension;
        Path target = uploadPath.resolve(storedName).normalize();
        if (!target.getParent().equals(uploadPath)) {
            throw BusinessException.badRequest("非法文件路径");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target);
        }

        return new FileUploadResponse(
                originalName,
                "/files/" + storedName,
                allowedType.image ? "IMAGE" : "FILE"
        );
    }

    public Optional<StoredFileResource> load(String storedName) {
        if (storedName == null || !STORED_FILE_NAME.matcher(storedName).matches()) {
            return Optional.empty();
        }

        Path candidate = uploadPath.resolve(storedName).normalize();
        if (!candidate.getParent().equals(uploadPath) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }

        String extension = extensionOf(storedName);
        return AllowedFileType.forExtension(extension)
                .map(type -> new StoredFileResource(
                        new FileSystemResource(candidate),
                        type.contentType,
                        type.image
                ));
    }

    private String sanitizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw BusinessException.badRequest("文件名不能为空");
        }

        String normalized = originalName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n\\p{Cntrl}]", "")
                .trim();
        if (fileName.isBlank() || fileName.length() > 255) {
            throw BusinessException.badRequest("文件名不合法");
        }
        return fileName;
    }

    private static String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFileResource(Resource resource, String contentType, boolean inline) {
    }

    private enum AllowedFileType {
        JPEG("jpg", "image/jpeg", true, Set.of("jpg", "jpeg"), Set.of("image/jpeg")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return startsWith(bytes, 0xFF, 0xD8, 0xFF);
            }
        },
        PNG("png", "image/png", true, Set.of("png"), Set.of("image/png")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            }
        },
        GIF("gif", "image/gif", true, Set.of("gif"), Set.of("image/gif")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return startsWithAscii(bytes, "GIF87a") || startsWithAscii(bytes, "GIF89a");
            }
        },
        WEBP("webp", "image/webp", true, Set.of("webp"), Set.of("image/webp")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return bytes.length >= 12
                        && startsWithAscii(bytes, "RIFF")
                        && asciiAt(bytes, 8, "WEBP");
            }
        },
        PDF("pdf", "application/pdf", false, Set.of("pdf"), Set.of("application/pdf")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return startsWithAscii(bytes, "%PDF-");
            }
        },
        DOC("doc", "application/msword", false, Set.of("doc"), Set.of("application/msword")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            }
        },
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false,
                Set.of("docx"), Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return isZip(bytes);
            }
        },
        TEXT("txt", "text/plain", false, Set.of("txt"), Set.of("text/plain")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                if (bytes.length == 0 || containsNullByte(bytes)) {
                    return false;
                }
                String prefix = new String(bytes, StandardCharsets.UTF_8)
                        .stripLeading()
                        .toLowerCase(Locale.ROOT);
                return !prefix.startsWith("<!doctype")
                        && !prefix.startsWith("<html")
                        && !prefix.startsWith("<svg")
                        && !prefix.startsWith("<?xml");
            }
        },
        ZIP("zip", "application/zip", false, Set.of("zip"),
                Set.of("application/zip", "application/x-zip-compressed", "application/octet-stream")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return isZip(bytes);
            }
        },
        RAR("rar", "application/vnd.rar", false, Set.of("rar"),
                Set.of("application/vnd.rar", "application/x-rar-compressed", "application/octet-stream")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return startsWith(bytes, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00)
                        || startsWith(bytes, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00);
            }
        },
        SEVEN_ZIP("7z", "application/x-7z-compressed", false, Set.of("7z"),
                Set.of("application/x-7z-compressed", "application/octet-stream")) {
            @Override
            boolean matchesSignature(byte[] bytes) {
                return startsWith(bytes, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C);
            }
        };

        private final String canonicalExtension;
        private final String contentType;
        private final boolean image;
        private final Set<String> extensions;
        private final Set<String> contentTypes;

        AllowedFileType(String canonicalExtension,
                        String contentType,
                        boolean image,
                        Set<String> extensions,
                        Set<String> contentTypes) {
            this.canonicalExtension = canonicalExtension;
            this.contentType = contentType;
            this.image = image;
            this.extensions = extensions;
            this.contentTypes = contentTypes;
        }

        abstract boolean matchesSignature(byte[] bytes);

        boolean acceptsContentType(String declaredContentType) {
            return contentTypes.contains(declaredContentType);
        }

        static Optional<AllowedFileType> forExtension(String extension) {
            return Arrays.stream(values())
                    .filter(type -> type.extensions.contains(extension))
                    .findFirst();
        }

        static boolean startsWith(byte[] bytes, int... signature) {
            if (bytes.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if ((bytes[i] & 0xFF) != signature[i]) {
                    return false;
                }
            }
            return true;
        }

        static boolean startsWithAscii(byte[] bytes, String signature) {
            return asciiAt(bytes, 0, signature);
        }

        static boolean asciiAt(byte[] bytes, int offset, String signature) {
            byte[] expected = signature.getBytes(StandardCharsets.US_ASCII);
            if (bytes.length < offset + expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (bytes[offset + i] != expected[i]) {
                    return false;
                }
            }
            return true;
        }

        static boolean isZip(byte[] bytes) {
            return startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)
                    || startsWith(bytes, 0x50, 0x4B, 0x05, 0x06)
                    || startsWith(bytes, 0x50, 0x4B, 0x07, 0x08);
        }

        static boolean containsNullByte(byte[] bytes) {
            for (byte value : bytes) {
                if (value == 0) {
                    return true;
                }
            }
            return false;
        }
    }
}
