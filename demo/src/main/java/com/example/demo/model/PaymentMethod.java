package com.example.demo.model;

import jakarta.persistence.*;

/**
 * Patient-saved payment method (ARCHITECTURE.md §8 P4).
 *
 * <p>Mobile-money wallets store {@code accountNumber} — the wallet phone
 * number the patient entered (a phone number, not a secret; shown in full).
 * Cards never store a PAN/PIN/CVV — only {@code last4} as a display aid.
 * Aza hosted checkout owns the charging rails; this row labels the method
 * and (for wallets) identifies where the patient pays from.
 */
@Entity
@Table(name = "patient_payment_methods")
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentNetwork network;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, length = 4)
    private String last4;

    /** Full mobile-money wallet number (0XXXXXXXXX / +233XXXXXXXXX); null for cards. */
    @Column(length = 20)
    private String accountNumber;

    /** Unused for charging. Kept so the mobile PaymentMethod shape is intact. */
    private String gatewayToken;

    @Column(nullable = false)
    private boolean isDefault = false;

    public PaymentMethod() {}

    public Long getId() { return id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public PaymentNetwork getNetwork() { return network; }
    public void setNetwork(PaymentNetwork network) { this.network = network; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getGatewayToken() { return gatewayToken; }
    public void setGatewayToken(String gatewayToken) { this.gatewayToken = gatewayToken; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
}
