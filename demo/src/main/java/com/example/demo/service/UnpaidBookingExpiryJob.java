package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Server-side pay-by enforcement (ARCHITECTURE.md §8 P3).
 * The mobile store must not expire bookings locally — a closed or
 * clock-wrong client cannot be trusted. Runs every minute.
 */
@Component
public class UnpaidBookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(UnpaidBookingExpiryJob.class);

    private final BookingService bookingService;

    public UnpaidBookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedRate = 60_000)
    public void expireOverdue() {
        int n = bookingService.expireOverdueUnpaidBookings();
        if (n > 0) {
            log.info("Expired {} unpaid booking(s) past payByDeadline and released their slots", n);
        }
    }
}
