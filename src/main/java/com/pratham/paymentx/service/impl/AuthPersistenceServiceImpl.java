package com.pratham.paymentx.service.impl;

import com.pratham.paymentx.dto.auth.GoogleAccount;
import com.pratham.paymentx.dto.auth.ParsedStudent;
import com.pratham.paymentx.entity.MerchantProfile;
import com.pratham.paymentx.entity.StudentProfile;
import com.pratham.paymentx.entity.User;
import com.pratham.paymentx.enums.Role;
import com.pratham.paymentx.repository.MerchantProfileRepository;
import com.pratham.paymentx.repository.StudentProfileRepository;
import com.pratham.paymentx.repository.UserRepository;
import com.pratham.paymentx.service.AuthPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthPersistenceServiceImpl implements AuthPersistenceService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    @Value("${student.hosted.domain:vitstudent.ac.in}")
    private String studentDomain;

    // ensures method must execute within a brand-new transaction scope if used as nested method
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID resolveAndPersistUser(GoogleAccount googleAccount) {
        String googleId = googleAccount.getGoogleId();
        String email = googleAccount.getEmail();

        // fast lookup by googleId
        Optional<UUID> byGoogleId = userRepository.findIdByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            return byGoogleId.get();
        }

        // lookup by email and attach googleId if present
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            log.info("Existing user found by email. Attaching Google ID for: {}", email);
            User user = byEmail.get();
            user.setGoogleId(googleId);
            // dirty checking will save the user
            return user.getId();
        }

        // create new user with concurrency protection
        try {
            log.info("Provisioning new user account for: {}", email);
            return buildAndSaveNewUser(googleAccount);
        } catch (DataIntegrityViolationException e) {
            // race condition occurred
            // another thread created this user after our checks
            log.warn("Concurrent insert detected for email {}. Recovering gracefully.", email);
            return userRepository.findIdByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("User must exist after DataIntegrityViolation"));
        }
    }

    private UUID buildAndSaveNewUser(GoogleAccount googleAccount) {
        User newUser = User.builder()
                .email(googleAccount.getEmail())
                .googleId(googleAccount.getGoogleId())
                .profileCompleted(false)
                .build();

        // create student account if vit student email
        // otherwise default is set to merchant
        if (studentDomain.equalsIgnoreCase(googleAccount.getHostedDomain())) {
            return createStudentUser(newUser, googleAccount.getDisplayName());
        }
        return createMerchantUser(newUser, googleAccount.getDisplayName());
    }

    private UUID createStudentUser(User newUser, String displayName) {
        // attach student role and save
        newUser.setRole(Role.STUDENT);
        newUser.setProfileActive(true);
        User savedUser = userRepository.saveAndFlush(newUser);
        // flush ensures if data integrity violation occurs then it is handled by our catch block
        // if we don't do flush, query will execute during commit, and it won't be caught by the catch block

        ParsedStudent parsedStudent = parseStudent(displayName);
        StudentProfile studentProfile = StudentProfile.builder()
                .fullName(parsedStudent.getFullName())
                .collegeRegNo(parsedStudent.getRegNumber())
                .user(savedUser)
                .build();

        studentProfileRepository.save(studentProfile);
        return savedUser.getId();
    }

    private UUID createMerchantUser(User newUser, String displayName) {
        // attach merchant role and save
        newUser.setRole(Role.MERCHANT);
        newUser.setProfileActive(false);
        User savedUser = userRepository.saveAndFlush(newUser);

        MerchantProfile merchantProfile = MerchantProfile.builder()
                .businessName(displayName != null && !displayName.isBlank() ? displayName.trim() : "Unknown Business")
                .user(savedUser)
                .build();

        merchantProfileRepository.save(merchantProfile);
        return savedUser.getId();
    }

    // parse the display name for student
    private ParsedStudent parseStudent(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName cannot be null or blank");
        }

        String trimmed = displayName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');

        if (lastSpace <= 0 || lastSpace == trimmed.length() - 1) {
            throw new IllegalArgumentException("Invalid displayName format: " + displayName);
        }

        String name = trimmed.substring(0, lastSpace);
        String regNumber = trimmed.substring(lastSpace + 1);

        return ParsedStudent.builder()
                .fullName(name)
                .regNumber(regNumber)
                .build();
    }
}
