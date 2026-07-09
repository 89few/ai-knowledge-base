package org.aiknowledgebase.controller;

import org.aiknowledgebase.dto.UploadedFileResponse;
import org.aiknowledgebase.entity.UserAccount;
import org.aiknowledgebase.service.AuthService;
import org.aiknowledgebase.service.FileUploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final AuthService authService;

    public FileUploadController(FileUploadService fileUploadService,
                                AuthService authService) {
        this.fileUploadService = fileUploadService;
        this.authService = authService;
    }

    /**
     * 上传文档或图片，并自动解析、切分、向量化。
     */
    @PostMapping("/upload")
    public UploadedFileResponse upload(@RequestParam(defaultValue = "1") Long knowledgeBaseId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        UserAccount user = authService.resolveUser(authorizationHeader);
        return fileUploadService.upload(user.getId(), knowledgeBaseId, file);
    }
}