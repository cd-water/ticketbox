package com.cdwater.ticketbox.storage;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {

    private final StorageService storageService;

    public AdminFileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    public UploadResponse upload(@RequestPart("file") MultipartFile file) {
        return new UploadResponse(storageService.upload(file));
    }
}
