package com.example.demo.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ApiResponse(
        int status,
        String message,
        List<String> errors,
        LocalDateTime timestamp
) {
    public static ApiResponse error(int status, String message) {
        return new ApiResponse(status, message, null, LocalDateTime.now());
    }

    public static ApiResponse error(int status, String message, List<String> errors) {
        return new ApiResponse(status, message, errors, LocalDateTime.now());
    }

    public static ApiResponse success(String message) {
        return new ApiResponse(200, message, null, LocalDateTime.now());
    }
}