package com.example.demo.dto.settings;

/**
 * POST /api/uploads/images response — the stored URL, relative to the
 * /uploads/** static handler (same convention as licenseDocumentUrl).
 */
public record UploadResponse(String url) {}
