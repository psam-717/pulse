package com.example.demo.dto;

import java.util.List;

public record WorkingHoursRequest(
        List<WorkingHoursEntry> entries
) {}