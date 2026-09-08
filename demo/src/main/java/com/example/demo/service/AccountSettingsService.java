package com.example.demo.service;

import com.example.demo.dto.AccountRequestResponse;
import com.example.demo.dto.ActiveSessionResponse;
import com.example.demo.dto.TwoFactorStatusResponse;
import com.example.demo.dto.UpdatePreferencesRequest;
import com.example.demo.dto.UserPreferencesResponse;
import com.example.demo.model.StaffAccountRequest;
import com.example.demo.model.StaffMember;
import com.example.demo.model.StaffPreference;
import com.example.demo.model.StaffSession;
import com.example.demo.repository.StaffAccountRequestRepository;
import com.example.demo.repository.StaffMemberRepository;
import com.example.demo.repository.StaffPreferenceRepository;
import com.example.demo.repository.StaffSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Personal account settings shared by all staff roles (BACKEND_SPEC §6.7
 * rows 8-16): sessions, 2FA, preferences, danger-zone account request.
 */
@Service
public class AccountSettingsService {

    private static final Set<String> REQUEST_TYPES = Set.of("deactivate", "delete");
    private static final Pattern BROWSER = Pattern.compile(
            "(?i)(Chrome|Firefox|Safari|Edg|OPR|PostmanRuntime|curl)/?([\\d.]+)?");
    private static final Pattern MOBILE = Pattern.compile("(?i)(Mobile|Android|iPhone|iPod)");
    private static final Pattern TABLET = Pattern.compile("(?i)(iPad|Tablet)");

    private final StaffSessionRepository sessionRepository;
    private final StaffMemberRepository staffRepository;
    private final StaffPreferenceRepository preferenceRepository;
    private final StaffAccountRequestRepository accountRequestRepository;

    public AccountSettingsService(StaffSessionRepository sessionRepository,
                                  StaffMemberRepository staffRepository,
                                  StaffPreferenceRepository preferenceRepository,
                                  StaffAccountRequestRepository accountRequestRepository) {
        this.sessionRepository = sessionRepository;
        this.staffRepository = staffRepository;
        this.preferenceRepository = preferenceRepository;
        this.accountRequestRepository = accountRequestRepository;
    }

    /** Called on successful verify-otp; returns the session id to embed as the JWT "sid". */
    @Transactional
    public String registerSession(Long staffId, String userAgent) {
        String sid = UUID.randomUUID().toString();
        ClientInfo info = ClientInfo.parse(userAgent);
        sessionRepository.save(new StaffSession(sid, staffId, info.device(),
                info.browser(), info.location(), truncate(userAgent, 400)));
        return sid;
    }

    // ==================== Sessions ====================

    @Transactional
    public List<ActiveSessionResponse> listSessions(Long staffId, String currentSid) {
        if (currentSid != null) {
            sessionRepository.findByStaffIdAndId(staffId, currentSid)
                    .ifPresent(s -> {
                        s.setLastActive(Instant.now());
                        sessionRepository.save(s);
                    });
        }
        return sessionRepository.findTop50ByStaffIdOrderByLastActiveDesc(staffId).stream()
                .map(s -> ActiveSessionResponse.from(s, currentSid))
                .toList();
    }

    @Transactional
    public void revokeSession(Long staffId, String sid, String currentSid) {
        if (sid.equals(currentSid)) {
            throw new IllegalArgumentException("Cannot sign out the current session here");
        }
        StaffSession session = sessionRepository.findByStaffIdAndId(staffId, sid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        sessionRepository.delete(session);
    }

    /** Sign out every other session; the current one stays (mirrors the web mock). */
    @Transactional
    public void revokeAllSessions(Long staffId, String currentSid) {
        sessionRepository.findTop50ByStaffIdOrderByLastActiveDesc(staffId).stream()
                .filter(s -> !s.getId().equals(currentSid))
                .forEach(sessionRepository::delete);
    }

    // ==================== 2FA ====================

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse twoFactor(Long staffId) {
        StaffMember staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));
        return new TwoFactorStatusResponse(staff.isTwoFactorEnabled());
    }

    @Transactional
    public TwoFactorStatusResponse setTwoFactor(Long staffId, boolean enabled) {
        StaffMember staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));
        staff.setTwoFactorEnabled(enabled);
        staffRepository.save(staff);
        return new TwoFactorStatusResponse(enabled);
    }

    // ==================== Preferences ====================

    @Transactional(readOnly = true)
    public UserPreferencesResponse preferences(Long staffId) {
        return preferenceRepository.findByStaffId(staffId)
                .map(UserPreferencesResponse::from)
                .orElseGet(UserPreferencesResponse::defaults);
    }

    @Transactional
    public UserPreferencesResponse updatePreferences(Long staffId, UpdatePreferencesRequest req) {
        StaffPreference prefs = preferenceRepository.findByStaffId(staffId)
                .orElseGet(() -> new StaffPreference(staffId));
        if (req.language() != null) prefs.setLanguage(req.language());
        if (req.timezone() != null) prefs.setTimezone(req.timezone());
        if (req.dateLocale() != null) prefs.setDateLocale(req.dateLocale());
        preferenceRepository.save(prefs);
        return UserPreferencesResponse.from(prefs);
    }

    // ==================== Danger zone ====================

    @Transactional(readOnly = true)
    public AccountRequestResponse accountRequest(Long staffId) {
        return accountRequestRepository.findByStaffIdAndStatus(staffId, "pending")
                .map(AccountRequestResponse::from)
                .orElseGet(AccountRequestResponse::none);
    }

    @Transactional
    public AccountRequestResponse submitAccountRequest(Long staffId, String type,
                                                       String transferOwnershipTo) {
        if (!REQUEST_TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be one of: deactivate, delete");
        }
        accountRequestRepository.findByStaffId(staffId)
                .ifPresent(accountRequestRepository::delete);
        StaffAccountRequest request = accountRequestRepository.save(
                new StaffAccountRequest(staffId, type, transferOwnershipTo));
        return AccountRequestResponse.from(request);
    }

    // ==================== Client meta ====================

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ClientInfo(String device, String browser, String location) {
        static ClientInfo parse(String userAgent) {
            if (userAgent == null || userAgent.isBlank()) {
                return new ClientInfo("Unknown device", "Browser", "Remote");
            }
            String device = TABLET.matcher(userAgent).find() ? "Tablet"
                    : MOBILE.matcher(userAgent).find() ? "Mobile" : "Desktop";
            Matcher m = BROWSER.matcher(userAgent);
            String browser = "Browser";
            if (m.find()) {
                String name = switch (m.group(1).toLowerCase()) {
                    case "chrome" -> "Chrome";
                    case "firefox" -> "Firefox";
                    case "safari" -> "Safari";
                    case "edg" -> "Edge";
                    case "opr" -> "Opera";
                    case "postmanruntime" -> "Postman";
                    default -> m.group(1);
                };
                browser = m.group(2) != null ? name + " " + m.group(2) : name;
            }
            return new ClientInfo(device, browser, "Remote");
        }
    }
}
