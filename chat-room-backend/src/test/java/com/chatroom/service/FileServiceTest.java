package com.chatroom.service;

import com.chatroom.dto.response.FileUploadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsPngWhenExtensionMimeAndSignatureAgree() throws Exception {
        FileService service = new FileService(temporaryDirectory.toString());
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        FileUploadResponse response = service.upload(file);

        assertThat(response.fileName()).isEqualTo("avatar.png");
        assertThat(response.fileType()).isEqualTo("IMAGE");
        assertThat(response.filePath()).matches("^/files/[0-9a-f-]+\\.png$");
        String storedName = response.filePath().substring("/files/".length());
        assertThat(service.load(storedName)).isPresent();
    }

    @Test
    void rejectsSvgEvenWhenBrowserDeclaresImageMime() throws Exception {
        FileService service = new FileService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.svg", "image/svg+xml", "<svg onload=\"alert(1)\"/>".getBytes()
        );

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("扩展名");
    }

    @Test
    void rejectsHtmlDisguisedAsPlainText() throws Exception {
        FileService service = new FileService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "<!doctype html><script>alert(1)</script>".getBytes()
        );

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("真实类型");
    }

    @Test
    void rejectsExtensionAndSignatureMismatch() throws Exception {
        FileService service = new FileService(temporaryDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not a png".getBytes()
        );

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("真实类型");
    }

    @Test
    void refusesPathTraversalAndUnknownStoredNames() throws Exception {
        FileService service = new FileService(temporaryDirectory.toString());

        assertThat(service.load("../application.yml")).isEmpty();
        assertThat(service.load("anything.svg")).isEmpty();
    }
}
