package com.example.demo.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One Aza checkout session covering one or more booking fees.
 * Confirmation is webhook-only — never flip PAID on create.
 */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, unique = true)
    private String azaSessionId;

    /** Amount sent to Aza, in pesewas (minor units). See AzaAmountConverter. */
    @Column(nullable = false)
    private Long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentTxnStatus status = PaymentTxnStatus.PENDING;

    private Long methodId;

    @Column(nullable = false)
    private String provider = "aza";

    @ElementCollection
    @CollectionTable(name = "payment_transaction_bookings",
            joinColumns = @JoinColumn(name = "transaction_id"))
    @Column(name = "booking_id")
    private List<Long> bookingIds = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    public PaymentTransaction() {}

    public Long getId() { return id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getAzaSessionId() { return azaSessionId; }
    public void setAzaSessionId(String azaSessionId) { this.azaSessionId = azaSessionId; }

    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }

    public PaymentTxnStatus getStatus() { return status; }
    public void setStatus(PaymentTxnStatus status) { this.status = status; }

    public Long getMethodId() { return methodId; }
    public void setMethodId(Long methodId) { this.methodId = methodId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public List<Long> getBookingIds() { return bookingIds; }
    public void setBookingIds(List<Long> bookingIds) { this.bookingIds = bookingIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
