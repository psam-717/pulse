package com.example.demo.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One paid-booking row for GET /api/patients/me/payment-history.
 * Appended only when an Aza webhook marks a transaction COMPLETED.
 */
@Entity
@Table(name = "payment_history")
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    private Long bookingId;
    private Long transactionId;

    @Column(nullable = false)
    private String facilityName;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private String methodLabel;

    @Column(nullable = false)
    private LocalDateTime paidDate;

    @Column(nullable = false)
    private BigDecimal amount;

    public PaymentHistory() {}

    public Long getId() { return id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getMethodLabel() { return methodLabel; }
    public void setMethodLabel(String methodLabel) { this.methodLabel = methodLabel; }

    public LocalDateTime getPaidDate() { return paidDate; }
    public void setPaidDate(LocalDateTime paidDate) { this.paidDate = paidDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
