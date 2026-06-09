package com.swms.service;

import com.swms.config.JwtUtils;
import com.swms.config.MfaUtils;
import com.swms.entity.User;
import com.swms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MfaUtils mfaUtils;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuditLogService auditLogService;

    // Track failed attempts in memory
    private final Map<String, Integer> failedAttemptsMap = new ConcurrentHashMap<>();

    public static class LoginResult {
        public boolean success;
        public String message;
        public boolean mfaRequired;
        public boolean mfaSetupRequired;
        public String mfaSecret;
        public String qrCodeUrl;
        public String tempToken; // Token indicating successful password check
        public String token;     // Final JWT token (if MFA is bypassed/not enabled yet - though MFA is required in SWMS)
        public String role;      // Real user role (e.g. SUPER_ADMIN, WARDEN)
    }

    public LoginResult loginStep1(String username, String password, String ipAddress) {
        LoginResult result = new LoginResult();
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            auditLogService.log("UNKNOWN", "FAILED_LOGIN - Username not found: " + username, ipAddress);
            result.success = false;
            result.message = "Invalid username or password";
            return result;
        }

        User user = userOpt.get();

        if (!user.isActive()) {
            auditLogService.log(username, "FAILED_LOGIN - Attempt on locked account", ipAddress);
            result.success = false;
            result.message = "Account is locked. Contact Administrator.";
            return result;
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int attempts = failedAttemptsMap.merge(username, 1, Integer::sum);
            auditLogService.log(username, "FAILED_LOGIN - Invalid password (attempt " + attempts + ")", ipAddress);
            
            if (attempts >= 5) {
                user.setActive(false);
                userRepository.save(user);
                failedAttemptsMap.remove(username);
                auditLogService.log(username, "ACCOUNT_LOCKOUT - 5 consecutive failed attempts", ipAddress);
                result.message = "Account is locked due to too many failed attempts. Contact Administrator.";
            } else {
                result.message = "Invalid username or password";
            }
            
            result.success = false;
            return result;
        }

        // Reset failed attempts upon success
        failedAttemptsMap.remove(username);
        auditLogService.log(username, "LOGIN_STEP1_SUCCESS", ipAddress);

        result.success = true;
        result.role = user.getRole();
        result.tempToken = jwtUtils.generateToken(username, "PRE_MFA", user.getHostelName());

        if (!user.isMfaEnabled()) {
            result.mfaSetupRequired = true;
            if (user.getMfaSecret() == null) {
                user.setMfaSecret(mfaUtils.generateSecret());
                userRepository.save(user);
            }
            result.mfaSecret = user.getMfaSecret();
            result.qrCodeUrl = mfaUtils.getQrCodeUrl(user.getMfaSecret(), username);
            result.message = "MFA Setup required";
        } else {
            result.mfaRequired = true;
            result.message = "MFA Verification required";
        }

        return result;
    }

    public LoginResult loginStep2(String tempToken, String code, String ipAddress) {
        LoginResult result = new LoginResult();
        
        if (!jwtUtils.validateToken(tempToken)) {
            result.success = false;
            result.message = "Session expired. Please log in again.";
            return result;
        }

        String username = jwtUtils.getUsernameFromToken(tempToken);
        String preMfaRole = jwtUtils.getRoleFromToken(tempToken);

        if (!"PRE_MFA".equals(preMfaRole)) {
            result.success = false;
            result.message = "Invalid session state.";
            return result;
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            result.success = false;
            result.message = "User not found or inactive.";
            return result;
        }

        User user = userOpt.get();
        boolean isCodeValid = mfaUtils.verifyCode(user.getMfaSecret(), code);

        if (!isCodeValid) {
            auditLogService.log(username, "FAILED_MFA - Invalid authentication code", ipAddress);
            result.success = false;
            result.message = "Invalid Authenticator code";
            return result;
        }

        // Enable MFA if it was first time
        if (!user.isMfaEnabled()) {
            user.setMfaEnabled(true);
            userRepository.save(user);
            auditLogService.log(username, "MFA_ENABLED - First time setup complete", ipAddress);
        }

        auditLogService.log(username, "LOGIN_SUCCESS - Complete authentication", ipAddress);

        result.success = true;
        result.token = jwtUtils.generateToken(user.getUsername(), user.getRole(), user.getHostelName());
        result.message = "Login successful";
        return result;
    }

    public User registerWarden(String username, String password, String mobileNumber, String hostelName, String creator, String ipAddress) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken");
        }

        User warden = new User();
        warden.setUsername(username);
        warden.setPasswordHash(passwordEncoder.encode(password));
        warden.setMobileNumber(mobileNumber);
        warden.setHostelName(hostelName);
        warden.setRole("WARDEN");
        warden.setActive(true);
        warden.setMfaEnabled(false);

        User saved = userRepository.save(warden);
        auditLogService.log(creator, "REGISTER_WARDEN - Created warden: " + username + " for hostel: " + hostelName, ipAddress);
        return saved;
    }

    public List<User> getWardens() {
        return userRepository.findByRole("WARDEN");
    }

    public void resetWardenAccount(Long id, String creator, String ipAddress) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User warden = userOpt.get();
            warden.setMfaEnabled(false);
            warden.setMfaSecret(null);
            warden.setActive(true);
            userRepository.save(warden);
            auditLogService.log(creator, "RESET_WARDEN_ACCOUNT - Reset and unlocked warden: " + warden.getUsername(), ipAddress);
        }
    }

    public void toggleWardenStatus(Long id, String creator, String ipAddress) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User warden = userOpt.get();
            warden.setActive(!warden.isActive());
            userRepository.save(warden);
            String state = warden.isActive() ? "ACTIVATED" : "DEACTIVATED";
            auditLogService.log(creator, "TOGGLE_WARDEN_STATUS - Set warden: " + warden.getUsername() + " status to " + state, ipAddress);
        }
    }
}
