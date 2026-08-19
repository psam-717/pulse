package com.example.demo.exception;

/** 404 with a guidance message (patient queue ticket missing, etc.). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
