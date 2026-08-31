package com.chatroom.controller;

import com.chatroom.dto.ApiResponse;
import com.chatroom.dto.response.FileUploadResponse;
import com.chatroom.service.FileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(fileService.upload(file));
    }
}
