package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// for large custom responses in a json format
record StatusResponse(
        String status,
        String message,
        String database
) {}

@RestController
public class HelloController{
    
    // returning a simple Map for easy and quick json responses
    @GetMapping("/api/hello")
    public Map<String, String> sayHello(
                @RequestParam(value = "name", defaultValue= "pulse") String name) {
            return Map.of(
                "message", "Hellos, " + name + "! Your Spring Boot backend is live and running 🚀",
                "greetedBy", "Psam"
            );
    }

    @GetMapping("/api/status")
    public StatusResponse status() {
        return new StatusResponse("up", "Backend is up and connected", "PostgreSQL");
    }
    
}
