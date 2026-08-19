package com.example.demo.controller;

import com.example.demo.dto.AddPaymentMethodRequest;
import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.CheckoutResponse;
import com.example.demo.dto.PayRequest;
import com.example.demo.dto.PaymentHistoryEntryResponse;
import com.example.demo.dto.PaymentMethodResponse;
import com.example.demo.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Patient payment methods, Aza checkout, and history (ARCHITECTURE.md §8 P4).
 * Patient JWT subject is the patient id.
 */
@RestController
@RequestMapping("/api/patients/me")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    private static Long currentPatientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<PaymentMethodResponse>> listMethods() {
        return ResponseEntity.ok(paymentService.listMethods(currentPatientId()));
    }

    @PostMapping("/payment-methods")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PaymentMethodResponse> addMethod(@RequestBody AddPaymentMethodRequest request) {
        return ResponseEntity.ok(paymentService.addMethod(currentPatientId(), request));
    }

    @PatchMapping("/payment-methods/{id}/default")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PaymentMethodResponse> setDefault(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.setDefault(currentPatientId(), id));
    }

    @DeleteMapping("/payment-methods/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse> deleteMethod(@PathVariable Long id) {
        paymentService.deleteMethod(currentPatientId(), id);
        return ResponseEntity.ok(ApiResponse.success("Payment method removed"));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<CheckoutResponse> pay(@RequestBody PayRequest request) {
        return ResponseEntity.ok(paymentService.startCheckout(currentPatientId(), request));
    }

    @GetMapping("/payment-history")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<PaymentHistoryEntryResponse>> history() {
        return ResponseEntity.ok(paymentService.history(currentPatientId()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse> handleErrors(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, ex.getMessage()));
    }
}
