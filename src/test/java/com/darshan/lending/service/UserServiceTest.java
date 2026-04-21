package com.darshan.lending.service;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.*;
import com.darshan.lending.entity.enums.*;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.BankAccountRepository;
import com.darshan.lending.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository        userRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock PasswordEncoder       passwordEncoder;

    @InjectMocks UserService service;

    private User mobileVerifiedUser;
    private UserRegistrationRequest regRequest;

    @BeforeEach
    void setUp() {
        mobileVerifiedUser = User.builder()
                .id(1L)
                .countryCode("+91")
                .phoneNumber("9876543210")
                .status(UserStatus.MOBILE_VERIFIED)
                .kycStatus(KycStatus.PENDING)
                .role(Role.BORROWER)
                .emailVerified(false)
                .phoneVerified(true)
                .build();

        AddressDto addressDto = AddressDto.builder()
                .line1("123 Main St").city("Bengaluru")
                .state("Karnataka").pincode("560001")
                .build();

        regRequest = UserRegistrationRequest.builder()
                .firstName("Alice").lastName("Smith")
                .email("alice@example.com")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .role(Role.BORROWER)
                .password("Secret@123")
                .pan("ABCDE1234F")
                .gender(Gender.FEMALE)
                .address(addressDto)
                .build();
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: valid request → user PLATFORM_ACCOUNT_CREATED, wallet created")
    void register_happyPath() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mobileVerifiedUser));
        when(userRepository.existsByPan("ABCDE1234F")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bankAccountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bankAccountRepository.findByUserIdAndAccountType(anyLong(), any()))
                .thenReturn(Optional.empty());

        UserResponse response = service.register(1L, regRequest);

        assertThat(response).isNotNull();
        assertThat(mobileVerifiedUser.getStatus()).isEqualTo(UserStatus.PLATFORM_ACCOUNT_CREATED);
        verify(bankAccountRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("register: user not in MOBILE_VERIFIED state → BusinessException")
    void register_wrongStatus_throws() {
        mobileVerifiedUser.setStatus(UserStatus.REGISTRATION_COMPLETE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(mobileVerifiedUser));

        assertThatThrownBy(() -> service.register(1L, regRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MOBILE_VERIFIED");
    }

    @Test
    @DisplayName("register: PAN already registered → BusinessException")
    void register_panAlreadyExists_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mobileVerifiedUser));
        when(userRepository.existsByPan("ABCDE1234F")).thenReturn(true);

        assertThatThrownBy(() -> service.register(1L, regRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PAN already registered");
    }

    @Test
    @DisplayName("register: age below 18 → BusinessException")
    void register_underage_throws() {
        regRequest.setDateOfBirth(LocalDate.now().minusYears(16));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mobileVerifiedUser));
        when(userRepository.existsByPan(any())).thenReturn(false);

        assertThatThrownBy(() -> service.register(1L, regRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("18 years");
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile: updates fullName, email, gender")
    void updateProfile_updatesFields() {
        mobileVerifiedUser.setStatus(UserStatus.PLATFORM_ACCOUNT_CREATED);
        when(userRepository.findByPhoneNumber("9876543210"))
                .thenReturn(Optional.of(mobileVerifiedUser));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bankAccountRepository.findByUserIdAndAccountType(anyLong(), any()))
                .thenReturn(Optional.empty());

        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .fullName("Alice Updated")
                .email("newalice@example.com")
                .gender(Gender.FEMALE)
                .build();

        UserResponse response = service.updateProfileByPhone("9876543210", req);

        assertThat(mobileVerifiedUser.getFullName()).isEqualTo("Alice Updated");
        assertThat(mobileVerifiedUser.getEmail()).isEqualTo("newalice@example.com");
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById: existing user → UserResponse returned")
    void getById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mobileVerifiedUser));
        when(bankAccountRepository.findByUserIdAndAccountType(anyLong(), any()))
                .thenReturn(Optional.empty());

        UserResponse response = service.getById(1L);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getById: user not found → ResourceNotFoundException")
    void getById_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── createAdmin ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createAdmin: new admin → saved with ADMIN role")
    void createAdmin_happyPath() {
        CreateAdminRequest req = CreateAdminRequest.builder()
                .phoneNumber("9111111111").email("admin@lending.com")
                .password("Admin@123").fullName("Super Admin")
                .countryCode("+91").build();

        when(userRepository.existsByPhoneNumber("9111111111")).thenReturn(false);
        when(userRepository.existsByEmail("admin@lending.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(100L);
            return u;
        });
        when(bankAccountRepository.findByUserIdAndAccountType(100L, AccountType.SAVINGS))
                .thenReturn(Optional.empty());

        UserResponse response = service.createAdmin(req);

        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("createAdmin: phone already registered → BusinessException")
    void createAdmin_phoneDuplicate_throws() {
        CreateAdminRequest req = CreateAdminRequest.builder()
                .phoneNumber("9111111111").email("admin@lending.com")
                .password("Admin@123").fullName("Super Admin")
                .countryCode("+91").build();

        when(userRepository.existsByPhoneNumber("9111111111")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phone number already registered");
    }

    @Test
    @DisplayName("createAdmin: email already registered → BusinessException")
    void createAdmin_emailDuplicate_throws() {
        CreateAdminRequest req = CreateAdminRequest.builder()
                .phoneNumber("9111111111").email("admin@lending.com")
                .password("Admin@123").fullName("Super Admin")
                .countryCode("+91").build();

        when(userRepository.existsByPhoneNumber("9111111111")).thenReturn(false);
        when(userRepository.existsByEmail("admin@lending.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already registered");
    }

    // ── deleteAdmin ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAdmin: 2 admins exist → one deleted successfully")
    void deleteAdmin_happyPath() {
        User adminUser = User.builder().id(5L).role(Role.ADMIN).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(adminUser));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        service.deleteAdmin(5L);

        verify(userRepository).delete(adminUser);
    }

    @Test
    @DisplayName("deleteAdmin: only 1 admin left → BusinessException")
    void deleteAdmin_lastAdmin_throws() {
        User adminUser = User.builder().id(5L).role(Role.ADMIN).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(adminUser));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteAdmin(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one admin");
    }

    @Test
    @DisplayName("deleteAdmin: user is not an admin → BusinessException")
    void deleteAdmin_notAdmin_throws() {
        User borrowerUser = User.builder().id(5L).role(Role.BORROWER).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(borrowerUser));

        assertThatThrownBy(() -> service.deleteAdmin(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not an admin");
    }

    @Test
    @DisplayName("deleteAdmin: user not found → ResourceNotFoundException")
    void deleteAdmin_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAdmin(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAllAdmins ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllAdmins: returns list of all admins")
    void getAllAdmins_returnsList() {
        User admin1 = User.builder().id(1L).role(Role.ADMIN).fullName("Admin One").build();
        User admin2 = User.builder().id(2L).role(Role.ADMIN).fullName("Admin Two").build();
        when(userRepository.findAllByRole(Role.ADMIN)).thenReturn(List.of(admin1, admin2));
        when(bankAccountRepository.findByUserIdAndAccountType(anyLong(), any()))
                .thenReturn(Optional.empty());

        List<UserResponse> result = service.getAllAdmins();

        assertThat(result).hasSize(2);
    }

    // ── updateProfileByPhone ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfileByPhone: updates profile by phone number")
    void updateProfileByPhone_happyPath() {
        when(userRepository.findByPhoneNumber("9876543210"))
                .thenReturn(Optional.of(mobileVerifiedUser));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bankAccountRepository.findByUserIdAndAccountType(anyLong(), any()))
                .thenReturn(Optional.empty());

        UserProfileUpdateRequest req = UserProfileUpdateRequest.builder()
                .fullName("Updated Name").build();

        UserResponse response = service.updateProfileByPhone("9876543210", req);

        assertThat(mobileVerifiedUser.getFullName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("updateProfileByPhone: phone not found → ResourceNotFoundException")
    void updateProfileByPhone_notFound_throws() {
        when(userRepository.findByPhoneNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfileByPhone("0000000000",
                UserProfileUpdateRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("phone number");
    }

    // ── getByPhone ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByPhone: returns user by phone number")
    void getByPhone_happyPath() {
        when(userRepository.findByPhoneNumber("9876543210"))
                .thenReturn(Optional.of(mobileVerifiedUser));
        when(bankAccountRepository.findByUserIdAndAccountType(anyLong(), any()))
                .thenReturn(Optional.empty());

        UserResponse response = service.getByPhone("9876543210");

        assertThat(response.getMobileNumber()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("getByPhone: phone not found → ResourceNotFoundException")
    void getByPhone_notFound_throws() {
        when(userRepository.findByPhoneNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByPhone("0000000000"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}