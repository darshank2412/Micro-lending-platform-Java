package com.darshan.lending.service;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .phoneNumber("9876543210").countryCode("+91")
                .email("test@lending.com")
                .password(".4qFITYhSKOYryHuMBQOuRXpqOOoGBTJyZHmCJKYANmFz6dyT4Oy")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());
    }

    @Test
    void register_shouldCompleteRegistration() {
        UserResponse response = userService.register(testUser.getId(), buildValidRegistrationRequest("ABCDE1234F"));
        assertEquals(UserStatus.PLATFORM_ACCOUNT_CREATED, response.getStatus());
        assertEquals("Darshan Kumar", response.getFullName());
        assertNotNull(response.getPlatformAccountNumber());
        assertTrue(response.getPlatformAccountNumber().startsWith("MLP"));
    }

    @Test
    void register_shouldFailWhenNotMobileVerified() {
        testUser.setStatus(UserStatus.REGISTRATION_COMPLETE);
        userRepository.save(testUser);
        assertThrows(BusinessException.class,
                () -> userService.register(testUser.getId(), buildValidRegistrationRequest("ZZZZZ9999Z")));
    }

    @Test
    void register_shouldFailOnDuplicatePan() {
        userService.register(testUser.getId(), buildValidRegistrationRequest("ABCDE1234F"));
        User secondUser = userRepository.save(User.builder()
                .phoneNumber("9999988888").countryCode("+91")
                .email("second@lending.com").password("password")
                .role(Role.BORROWER).status(UserStatus.MOBILE_VERIFIED).build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(secondUser.getId(), buildValidRegistrationRequest("ABCDE1234F")));
        assertTrue(ex.getMessage().contains("PAN already registered"));
    }

    @Test
    void getById_shouldReturnUser() {
        UserResponse response = userService.getById(testUser.getId());
        assertEquals(testUser.getId(), response.getId());
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> userService.getById(999999L));
    }

    @Test
    void updateProfile_shouldUpdateAllFields() {
        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .fullName("Updated Name").email("updated@example.com")
                .gender(Gender.FEMALE).build();
        UserResponse response = userService.updateProfile(testUser.getId(), req);
        assertEquals("Updated Name", response.getFullName());
        assertEquals("updated@example.com", response.getEmail());
        assertEquals(Gender.FEMALE, response.getGender());
    }

    @Test
    void updateProfile_withNullFields_shouldNotOverwrite() {
        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .fullName("Only Name Changed").build();
        UserResponse response = userService.updateProfile(testUser.getId(), req);
        assertEquals("Only Name Changed", response.getFullName());
    }

    @Test
    void toResponse_withNoAddress_shouldReturnNullAddress() {
        UserResponse response = userService.getById(testUser.getId());
        assertNull(response.getAddress());
    }

    private UserRegistrationRequest buildValidRegistrationRequest(String pan) {
        return UserRegistrationRequest.builder()
                .firstName("Darshan").lastName("Kumar")
                .email("user@example.com").phoneNumber("9876543210")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .gender(Gender.MALE).role(Role.BORROWER).pan(pan)
                .incomeBracket("5-10 LPA").p2pExperience(P2pExperience.BEGINNER)
                .password("password123")
                .address(AddressDto.builder().line1("123 Main St")
                        .city("Bengaluru").state("Karnataka").pincode("560001").build())
                .build();
    }
}
