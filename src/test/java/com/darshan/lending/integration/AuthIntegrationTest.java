package com.darshan.lending.integration;

import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.KycStatus;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.*;

class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("User saves and retrieves correctly from DB")
    void user_savesAndRetrieves() {
        User user = userRepository.save(User.builder()
                .fullName("Test User")
                .phoneNumber("9900000001")
                .countryCode("+91")
                .password("password123")
                .role(Role.BORROWER)
                .kycStatus(KycStatus.PENDING)
                .emailVerified(false)
                .phoneVerified(true)
                .build());

        User found = userRepository.findById(user.getId()).orElseThrow();

        assertThat(found.getFullName()).isEqualTo("Test User");
        assertThat(found.getRole()).isEqualTo(Role.BORROWER);
        assertThat(found.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    @DisplayName("Lender user saves with correct role")
    void lenderUser_savesCorrectly() {
        User lender = userRepository.save(User.builder()
                .fullName("Bob Lender")
                .phoneNumber("9900000002")
                .countryCode("+91")
                .password("pass")
                .role(Role.LENDER)
                .kycStatus(KycStatus.VERIFIED)
                .emailVerified(false)
                .phoneVerified(true)
                .build());

        assertThat(lender.getId()).isNotNull();
        assertThat(lender.getRole()).isEqualTo(Role.LENDER);
    }

    @Test
    @DisplayName("Find user by mobile number returns correct user")
    void findByMobileNumber_returnsUser() {
        userRepository.save(User.builder()
                .fullName("Mobile Test")
                .phoneNumber("9900000003")
                .countryCode("+91")
                .password("pass")
                .role(Role.BORROWER)
                .kycStatus(KycStatus.PENDING)
                .emailVerified(false)
                .phoneVerified(true)
                .build());

        User found = userRepository.findByPhoneNumber("9900000003").orElseThrow();

        assertThat(found.getFullName()).isEqualTo("Mobile Test");
    }
}