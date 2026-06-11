package com.swms.service;

import com.swms.config.JwtUtils;
import com.swms.config.MfaUtils;
import com.swms.config.EncryptionUtils;
import com.swms.entity.User;
import com.swms.repository.UserRepository;
import com.swms.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

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
        public String token;     // Final JWT token
        public String role;      // Real user role
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
                user.setMfaSecret(EncryptionUtils.encrypt(mfaUtils.generateSecret()));
                userRepository.save(user);
            }
            String plainSecret = EncryptionUtils.decrypt(user.getMfaSecret());
            result.mfaSecret = plainSecret;
            result.qrCodeUrl = mfaUtils.getQrCodeUrl(plainSecret, username);
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
        String plainSecret = EncryptionUtils.decrypt(user.getMfaSecret());
        boolean isCodeValid = mfaUtils.verifyCode(plainSecret, code);

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

    public User preAuthorizeWarden(String username, String mobileNumber, String hostelName, String creator, String ipAddress) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username/Warden Name is already pre-authorized or taken");
        }

        User warden = new User();
        warden.setUsername(username);
        // Set a secure temporary random string as placeholder for passwordHash since password is required
        warden.setPasswordHash(passwordEncoder.encode("PENDING_SELF_REGISTRATION_ACTIVATION_" + java.util.UUID.randomUUID().toString()));
        warden.setMobileNumber(mobileNumber);
        warden.setHostelName(hostelName);
        warden.setRole("WARDEN");
        warden.setActive(false); // Not active until self-registration completes
        warden.setMfaEnabled(false);

        User saved = userRepository.save(warden);
        auditLogService.log(creator, "PRE_AUTHORIZE_WARDEN - Pre-enrolled warden: " + username + " for hostel: " + hostelName, ipAddress);
        return saved;
    }

    @Transactional
    public void deleteWarden(Long id, String creator, String ipAddress) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User warden = userOpt.get();
            if ("WARDEN".equals(warden.getRole())) {
                if (warden.getHostelName() != null) {
                    studentRepository.deleteByHostelName(warden.getHostelName());
                }
                userRepository.delete(warden);
                auditLogService.log(creator, "DELETE_WARDEN - Permanently deleted warden: " + warden.getUsername() + " and all student records in " + warden.getHostelName(), ipAddress);
            } else {
                throw new IllegalArgumentException("Only warden accounts can be deleted");
            }
        } else {
            throw new IllegalArgumentException("Warden account not found");
        }
    }

    public LoginResult completeWardenRegistration(String username, String mobileNumber, String password, String ipAddress) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Warden account not pre-authorized by Administrator.");
        }

        User user = userOpt.get();
        if (!"WARDEN".equals(user.getRole())) {
            throw new IllegalArgumentException("Only warden accounts can perform self-registration.");
        }

        if (user.isActive() && user.isMfaEnabled()) {
            throw new IllegalArgumentException("Warden account has already been registered and activated.");
        }

        // Check matching mobile number
        if (user.getMobileNumber() == null || !user.getMobileNumber().trim().equals(mobileNumber.trim())) {
            auditLogService.log("UNKNOWN", "FAILED_SELF_REGISTRATION - Mobile number mismatch for: " + username, ipAddress);
            throw new IllegalArgumentException("Warden details (Mobile Number) do not match the pre-authorized profile.");
        }

        // Set the chosen password and activate the account
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);

        // Generate MFA secret if not present
        String plainSecret;
        if (user.getMfaSecret() == null) {
            plainSecret = mfaUtils.generateSecret();
            user.setMfaSecret(EncryptionUtils.encrypt(plainSecret));
        } else {
            plainSecret = EncryptionUtils.decrypt(user.getMfaSecret());
        }
        
        userRepository.save(user);
        auditLogService.log(username, "SELF_REGISTRATION_SUCCESS - Password set and account activated", ipAddress);

        LoginResult result = new LoginResult();
        result.success = true;
        result.role = user.getRole();
        result.tempToken = jwtUtils.generateToken(username, "PRE_MFA", user.getHostelName());
        result.mfaSetupRequired = true;
        result.mfaSecret = plainSecret;
        result.qrCodeUrl = mfaUtils.getQrCodeUrl(plainSecret, username);
        result.message = "Self-registration successful. Please complete Multi-Factor Authentication (MFA) Setup.";
        return result;
    }

    public List<User> getWardens() {
        return userRepository.findByRole("WARDEN");
    }

    public String getDecryptedMfaSecret(Long id, String adminUsername, String ipAddress) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User warden = userOpt.get();
            if (!"WARDEN".equals(warden.getRole())) {
                throw new IllegalArgumentException("MFA secrets can only be retrieved for Wardens.");
            }
            if (warden.getMfaSecret() == null) {
                throw new IllegalArgumentException("Warden has not completed MFA setup yet.");
            }
            
            auditLogService.log(adminUsername, "DECRYPT_MFA_SECRET - Admin decrypted MFA secret for warden: " + warden.getUsername(), ipAddress);
            return EncryptionUtils.decrypt(warden.getMfaSecret());
        } else {
            throw new IllegalArgumentException("Warden account not found.");
        }
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
