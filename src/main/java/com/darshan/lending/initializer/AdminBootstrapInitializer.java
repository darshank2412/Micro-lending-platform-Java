package com.darshan.lending.initializer;

import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.KycStatus;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.entity.enums.UserStatus;
import com.darshan.lending.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // 🔥 must NOT be static

    @Override
    @Transactional
    public void run(String... args) {   // 🔥 NOT static

        if (!userRepository.existsByRole(Role.ADMIN)) {

            User admin = User.builder()
                    .phoneNumber("9999999999")
                    .countryCode("+91")
                    .fullName("System Admin")
                    .email("admin@lending.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(Role.ADMIN)
                    .status(UserStatus.PLATFORM_ACCOUNT_CREATED)
                    .kycStatus(KycStatus.VERIFIED)
                    .build();

            userRepository.save(admin);

            log.info("✅ Admin bootstrapped");
        } else {
            log.info("ℹ️ Admin already exists");
        }
    }
}