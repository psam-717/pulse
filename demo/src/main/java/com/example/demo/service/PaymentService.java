package com.example.demo.service;

import com.example.demo.dto.AddPaymentMethodRequest;
import com.example.demo.dto.CheckoutResponse;
import com.example.demo.dto.PayRequest;
import com.example.demo.dto.PaymentHistoryEntryResponse;
import com.example.demo.dto.PaymentMethodResponse;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.Booking;
import com.example.demo.model.BookingStatus;
import com.example.demo.model.PaymentHistory;
import com.example.demo.model.PaymentMethod;
import com.example.demo.model.PaymentNetwork;
import com.example.demo.model.PaymentStatus;
import com.example.demo.model.PaymentTransaction;
import com.example.demo.model.PaymentTxnStatus;
import com.example.demo.payment.AzaAmountConverter;
import com.example.demo.payment.CheckoutSession;
import com.example.demo.payment.PaymentGateway;
import com.example.demo.repository.BookingRepository;
import com.example.demo.repository.PaymentHistoryRepository;
import com.example.demo.repository.PaymentMethodRepository;
import com.example.demo.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Patient payment methods + Aza hosted checkout (ARCHITECTURE.md §8 P4 / G8).
 * Bookings flip to PAID only from {@link #completeSession(String)}.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final Map<PaymentNetwork, String> NETWORK_LABEL = Map.of(
            PaymentNetwork.mtn_momo, "MTN MoMo",
            PaymentNetwork.telecel_cash, "Telecel Cash",
            PaymentNetwork.card, "Card"
    );

    private final PaymentMethodRepository methodRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentHistoryRepository historyRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentMethodRepository methodRepository,
                          PaymentTransactionRepository transactionRepository,
                          PaymentHistoryRepository historyRepository,
                          BookingRepository bookingRepository,
                          PaymentGateway paymentGateway) {
        this.methodRepository = methodRepository;
        this.transactionRepository = transactionRepository;
        this.historyRepository = historyRepository;
        this.bookingRepository = bookingRepository;
        this.paymentGateway = paymentGateway;
    }

    public List<PaymentMethodResponse> listMethods(Long patientId) {
        return methodRepository.findByPatientIdOrderByIdAsc(patientId).stream()
                .map(PaymentService::toMethod)
                .toList();
    }

    @Transactional
    public PaymentMethodResponse addMethod(Long patientId, AddPaymentMethodRequest req) {
        if (req == null || req.network() == null || req.network().isBlank()) {
            throw new IllegalArgumentException(
                    "network is required. Use one of: mtn_momo, telecel_cash, card.");
        }
        PaymentNetwork network;
        try {
            network = PaymentNetwork.valueOf(req.network().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "network must be one of: mtn_momo, telecel_cash, card.");
        }
        String last4 = sanitizeLast4(req.last4());
        String label = req.label() == null || req.label().isBlank()
                ? NETWORK_LABEL.get(network) + " •••• " + last4
                : req.label().trim();

        PaymentMethod m = new PaymentMethod();
        m.setPatientId(patientId);
        m.setNetwork(network);
        m.setLast4(last4);
        m.setLabel(label);
        m.setGatewayToken(null);
        boolean first = methodRepository.countByPatientId(patientId) == 0;
        m.setDefault(first);
        return toMethod(methodRepository.save(m));
    }

    @Transactional
    public PaymentMethodResponse setDefault(Long patientId, Long methodId) {
        PaymentMethod target = methodRepository.findByIdAndPatientId(methodId, patientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment method not found. Use GET /api/patients/me/payment-methods."));
        for (PaymentMethod m : methodRepository.findByPatientIdOrderByIdAsc(patientId)) {
            m.setDefault(m.getId().equals(methodId));
            methodRepository.save(m);
        }
        target.setDefault(true);
        return toMethod(methodRepository.save(target));
    }

    @Transactional
    public void deleteMethod(Long patientId, Long methodId) {
        PaymentMethod target = methodRepository.findByIdAndPatientId(methodId, patientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment method not found. Use GET /api/patients/me/payment-methods."));
        boolean wasDefault = target.isDefault();
        methodRepository.delete(target);
        if (wasDefault) {
            List<PaymentMethod> rest = methodRepository.findByPatientIdOrderByIdAsc(patientId);
            if (!rest.isEmpty()) {
                rest.get(0).setDefault(true);
                methodRepository.save(rest.get(0));
            }
        }
    }

    @Transactional
    public CheckoutResponse startCheckout(Long patientId, PayRequest req) {
        if (req == null || req.bookingIds() == null || req.bookingIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "bookingIds is required. Send the outstanding booking ids to pay.");
        }
        if (req.methodId() == null) {
            throw new IllegalArgumentException(
                    "methodId is required. Add a payment method first.");
        }
        PaymentMethod method = methodRepository.findByIdAndPatientId(req.methodId(), patientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment method not found. Use GET /api/patients/me/payment-methods."));

        LinkedHashSet<Long> ids = new LinkedHashSet<>(req.bookingIds());
        List<Booking> bookings = bookingRepository.findByIdInAndPatient_Id(ids, patientId);
        if (bookings.size() != ids.size()) {
            throw new IllegalArgumentException(
                    "One or more bookings were not found on your account.");
        }
        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.CANCELLED) {
                throw new ConflictException("Booking " + b.getId() + " is cancelled and cannot be paid.");
            }
            if (Boolean.TRUE.equals(b.getCheckedIn())) {
                throw new ConflictException("Booking " + b.getId() + " is already checked in.");
            }
            if (b.getPaymentStatus() == PaymentStatus.PAID) {
                throw new ConflictException("Booking " + b.getId() + " is already paid.");
            }
        }

        BigDecimal total = bookings.stream()
                .map(Booking::getAmountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Nothing to charge — booking fees total 0.");
        }
        long azaAmount = AzaAmountConverter.toAzaAmount(total);
        CheckoutSession session = paymentGateway.createSession(azaAmount, "GHS");

        PaymentTransaction tx = new PaymentTransaction();
        tx.setPatientId(patientId);
        tx.setAzaSessionId(session.sessionId());
        // Amount as sent to Aza (their `amount` field is GHS major units —
        // see AzaAmountConverter; bug-triage BE-5).
        tx.setAmountMinor(azaAmount);
        tx.setStatus(PaymentTxnStatus.PENDING);
        tx.setMethodId(method.getId());
        tx.setProvider("aza");
        tx.setBookingIds(new ArrayList<>(ids));
        tx.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        return new CheckoutResponse(session.checkoutUrl(), session.sessionId());
    }

    /**
     * Webhook confirmation. Idempotent: a second COMPLETED delivery is a no-op.
     * @return true if this call newly completed the transaction
     */
    @Transactional
    public boolean completeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "sessionId is required. Send the Aza checkout session id (cs_...).");
        }
        PaymentTransaction tx = transactionRepository.findByAzaSessionId(sessionId.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown Aza session. Create a checkout via POST /api/patients/me/payments first."));
        if (tx.getStatus() == PaymentTxnStatus.COMPLETED) {
            log.info("Ignoring duplicate Aza webhook for session {}", sessionId);
            return false;
        }

        PaymentMethod method = tx.getMethodId() == null ? null
                : methodRepository.findById(tx.getMethodId()).orElse(null);
        String methodLabel = method != null ? method.getLabel() : "Aza";
        LocalDateTime paidAt = LocalDateTime.now();

        for (Long bookingId : tx.getBookingIds()) {
            Booking b = bookingRepository.findById(bookingId).orElse(null);
            if (b == null) continue;
            b.setPaymentStatus(PaymentStatus.PAID);
            b.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(b);

            PaymentHistory h = new PaymentHistory();
            h.setPatientId(tx.getPatientId());
            h.setBookingId(b.getId());
            h.setTransactionId(tx.getId());
            h.setFacilityName(b.getHospital() != null ? b.getHospital().getName() : "");
            h.setDepartment(b.getDepartment() != null ? b.getDepartment().getName() : "");
            h.setMethodLabel(methodLabel);
            h.setPaidDate(paidAt);
            h.setAmount(b.getAmountDue());
            historyRepository.save(h);
        }

        tx.setStatus(PaymentTxnStatus.COMPLETED);
        tx.setCompletedAt(paidAt);
        transactionRepository.save(tx);
        log.info("Aza session {} completed — {} booking(s) marked PAID", sessionId, tx.getBookingIds().size());
        return true;
    }

    public List<PaymentHistoryEntryResponse> history(Long patientId) {
        return historyRepository.findByPatientIdOrderByPaidDateDesc(patientId).stream()
                .map(h -> new PaymentHistoryEntryResponse(
                        String.valueOf(h.getId()),
                        h.getFacilityName(),
                        h.getDepartment(),
                        h.getMethodLabel(),
                        h.getPaidDate() != null ? h.getPaidDate().toString() : null,
                        h.getAmount()))
                .toList();
    }

    private static String sanitizeLast4(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "last4 is required (2–4 digits the patient typed to recognise the method).");
        }
        String digits = raw.trim().replaceAll("\\D", "");
        if (digits.length() < 2 || digits.length() > 4) {
            throw new IllegalArgumentException(
                    "last4 must be 2–4 digits. Do not send a full card or MoMo number.");
        }
        return digits;
    }

    private static PaymentMethodResponse toMethod(PaymentMethod m) {
        return new PaymentMethodResponse(
                String.valueOf(m.getId()),
                m.getNetwork().name(),
                m.getLabel(),
                m.getLast4(),
                m.getGatewayToken(),
                m.isDefault());
    }
}
