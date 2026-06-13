package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploaded license documents as static resources.
 * Maps /uploads/** to the file system upload directory.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorageService;

    public WebConfig(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /uploads/** → file:./uploads/ on the filesystem
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + fileStorageService.getUploadDir() + "/");
    }
}