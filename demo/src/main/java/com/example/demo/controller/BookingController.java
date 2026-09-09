package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.BookingRequest;
import com.example.demo.dto.BookingResponse;
import com.example.demo.dto.BookingSummaryResponse;
import com.example.demo.dto.CancelBookingRequest;
import com.example.demo.dto.CheckoutResponse;
import com.example.demo.dto.MobileBookingRequest;
import com.example.demo.dto.PaymentUpdateRequest;
import com.example.demo.dto.RescheduleRequest;
import com.example.demo.dto.RescheduleSurchargePayRequest;
import com.example.demo.dto.UpdatePayByDeadlineRequest;
import com.example.demo.service.BookingService;
import com.example.demo.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    public BookingController(BookingService bookingService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'SUPER_ADMIN')")
    public ResponseEntity<BookingResponse> createBooking(@RequestBody BookingRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = (Long) auth.getPrincipal();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");
        return ResponseEntity.ok(bookingService.createBooking(request, authenticatedUserId, role));
    }

    @PostMapping("/mobile")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<BookingSummaryResponse> createMobileBooking(
            @RequestBody MobileBookingRequest request) {
        return ResponseEntity.ok(bookingService.createMobileBooking(currentUserId(), request));
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<BookingSummaryResponse> reschedule(
            @PathVariable Long id,
            @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(bookingService.reschedule(id, currentUserId(), request));
    }

    /**
     * Pay the GH¢20 earlier-reschedule surcharge for one booking. Hit this
     * when PATCH /{id}/reschedule returns 402 EARLIER_RESCHEDULE_SURCHARGE_REQUIRED,
     * then retry the same reschedule request. Body: {@code {methodId}}.
     */
    @PostMapping("/{id}/reschedule/surcharge")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<CheckoutResponse> payRescheduleSurcharge(
            @PathVariable Long id,
            @RequestBody(required = false) RescheduleSurchargePayRequest request) {
        Long methodId = request != null ? request.methodId() : null;
        return ResponseEntity.ok(
                paymentService.startSurchargeCheckout(currentUserId(), id, methodId));
    }

    @PatchMapping("/{id}/pay-by-deadline")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<BookingSummaryResponse> setPayByDeadline(
            @PathVariable Long id,
            @RequestBody UpdatePayByDeadlineRequest request) {
        if (request == null || request.payByDeadline() == null || request.payByDeadline().isBlank()) {
            throw new IllegalArgumentException(
                    "payByDeadline must be an ISO datetime, e.g. 2026-08-20T09:00:00.");
        }
        try {
            return ResponseEntity.ok(bookingService.setPayByDeadline(
                    id, LocalDateTime.parse(request.payByDeadline().trim())));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "payByDeadline must be an ISO datetime, e.g. 2026-08-20T09:00:00.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingResponse> getBookingSummary(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = (Long) auth.getPrincipal();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");
        return ResponseEntity.ok(bookingService.getBookingSummary(id, authenticatedUserId, role));
    }

    @PatchMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('PATIENT', 'SUPER_ADMIN')")
    public ResponseEntity<BookingResponse> updatePayment(
            @PathVariable Long id,
            @RequestBody PaymentUpdateRequest request) {
        return ResponseEntity.ok(bookingService.updatePaymentStatus(id, request.paymentStatus()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) CancelBookingRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = (Long) auth.getPrincipal();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");
        String reason = request != null ? request.reason() : null;
        bookingService.cancelBooking(id, authenticatedUserId, role, reason);
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled successfully"));
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}