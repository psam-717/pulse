package com.example.demo.model;

import jakarta.persistence.*;

/**
 * Patient-saved payment display metadata (ARCHITECTURE.md §8 P4).
 * Never stores a raw card/MoMo number, PIN, or CVV. Aza has no
 * stored-instrument API — this row only labels the Payments screen.
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

    public String getGatewayToken() { return gatewayToken; }
    public void setGatewayToken(String gatewayToken) { this.gatewayToken = gatewayToken; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
}
