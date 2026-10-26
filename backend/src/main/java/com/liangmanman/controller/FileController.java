package com.liangmanman.controller;

import com.liangmanman.auth.UserContext;
import com.liangmanman.common.ApiResult;
import com.liangmanman.service.FileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /** 小程序 uni.uploadFile，字段名 file；返回 { url } */
    @PostMapping("/api/files")
    public ApiResult<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        UserContext.require();
        return ApiResult.ok(Map.of("url", fileService.saveImage(file)));
    }
}
