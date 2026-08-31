package com.chatroom.controller;

import com.chatroom.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
public class FileDownloadController {

    private final FileService fileService;

    public FileDownloadController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/files/{storedName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String storedName) {
        return fileService.load(storedName)
                .map(storedFile -> {
                    ContentDisposition disposition = (storedFile.inline()
                            ? ContentDisposition.inline()
                            : ContentDisposition.attachment())
                            .filename(storedName, StandardCharsets.UTF_8)
                            .build();

                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(storedFile.contentType()))
                            .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                            .header("X-Content-Type-Options", "nosniff")
                            .header("Content-Security-Policy", "sandbox")
                            .header("Cross-Origin-Resource-Policy", "same-site")
                            .body(storedFile.resource());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
