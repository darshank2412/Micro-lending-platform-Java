package com.darshan.lending.service;

import com.darshan.lending.dto.*;
import com.darshan.lending.entity.Address;
import com.darshan.lending.entity.BankAccount;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.AccountStatus;
import com.darshan.lending.entity.enums.AccountType;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.entity.enums.UserStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.BankAccountRepository;
import com.darshan.lending.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public UserResponse register(Long userId, UserRegistrationRequest req) {

        User user = findUserById(userId);

        if (user.getStatus() != UserStatus.MOBILE_VERIFIED) {
            throw new BusinessException(
                    "User must be in MOBILE_VERIFIED status to register. Current: "
                            + user.getStatus());
        }

        if (userRepository.existsByPan(req.getPan())) {
            throw new BusinessException("PAN already registered: " + req.getPan());
        }

        int age = Period.between(req.getDateOfBirth(), LocalDate.now()).getYears();
        if (age < 18) {
            throw new BusinessException(
                    "User must be at least 18 years old. Age provided: " + age);
        }

        Address address = Address.builder()
                .line1(req.getAddress().getLine1())
                .city(req.getAddress().getCity())
                .state(req.getAddress().getState())
                .pincode(req.getAddress().getPincode())
                .build();

        String fullName = req.getFirstName() + " " + req.getLastName();

        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setPhoneNumber(req.getPhoneNumber());
        user.setDateOfBirth(req.getDateOfBirth());
        user.setRole(req.getRole());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setFullName(fullName);
        user.setEmail(req.getEmail());
        user.setGender(req.getGender());
        user.setPan(req.getPan());
        user.setIncomeBracket(req.getIncomeBracket());
        user.setP2pExperience(req.getP2pExperience());
        user.setAddress(address);
        user.setStatus(UserStatus.REGISTRATION_COMPLETE);

        User saved = userRepository.save(user);
        log.info("Registration complete for userId={}", saved.getId());

        // Auto-create platform wallet
        String accountNumber = "MLP" + String.format("%08d", saved.getId());

        BankAccount wallet = BankAccount.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .user(saved)
                .build();

        bankAccountRepository.save(wallet);

        saved.setStatus(UserStatus.PLATFORM_ACCOUNT_CREATED);
        userRepository.save(saved);

        log.info("Wallet created: {} for userId={}", accountNumber, saved.getId());

        return toResponse(saved);
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    @Transactional
    public UserResponse updateProfile(Long userId, UserProfileUpdateRequest req) {

        User user = findUserById(userId);

        if (req.getFullName() != null) user.setFullName(req.getFullName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getGender() != null) user.setGender(req.getGender());

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return toResponse(findUserById(userId));
    }

    // ── Admin Management ──────────────────────────────────────────────────────

    @Transactional
    public UserResponse createAdmin(CreateAdminRequest request) {

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException("Phone number already registered");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }

        User admin = User.builder()
                .countryCode(request.getCountryCode())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.ADMIN)
                .status(UserStatus.PLATFORM_ACCOUNT_CREATED)
                .phoneVerified(true)
                .emailVerified(false)
                .build();

        admin.setPhoneNumber(request.getPhoneNumber());

        userRepository.save(admin);

        log.info("New admin created: phone={}", request.getPhoneNumber());

        return toResponse(admin);
    }

    @Transactional
    public void deleteAdmin(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Admin not found: " + userId));

        if (user.getRole() != Role.ADMIN) {
            throw new BusinessException("User is not an admin");
        }

        long adminCount = userRepository.countByRole(Role.ADMIN);

        if (adminCount <= 1) {
            throw new BusinessException(
                    "Cannot delete — at least one admin must exist at all times");
        }

        userRepository.delete(user);

        log.info("Admin deleted: userId={}", userId);
    }

    public List<UserResponse> getAllAdmins() {

        return userRepository.findAllByRole(Role.ADMIN)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public User findUserById(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + userId));
    }

    public UserResponse toResponse(User u) {

        AddressDto addressDto = null;

        if (u.getAddress() != null) {
            addressDto = AddressDto.builder()
                    .line1(u.getAddress().getLine1())
                    .city(u.getAddress().getCity())
                    .state(u.getAddress().getState())
                    .pincode(u.getAddress().getPincode())
                    .build();
        }

        String platformAccountNumber = bankAccountRepository
                .findByUserIdAndAccountType(u.getId(), AccountType.SAVINGS)
                .map(BankAccount::getAccountNumber)
                .orElse(null);

        return UserResponse.builder()
                .id(u.getId())
                .countryCode(u.getCountryCode())
                .mobileNumber(u.getPhoneNumber())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .dateOfBirth(u.getDateOfBirth())
                .gender(u.getGender())
                .role(u.getRole())
                .status(u.getStatus())
                .kycStatus(u.getKycStatus())
                .incomeBracket(u.getIncomeBracket())
                .p2pExperience(u.getP2pExperience())
//                .emailVerified(u.getEmailVerified())
//                .phoneVerified(u.getPhoneVerified())
                .address(addressDto)
                .platformAccountNumber(platformAccountNumber)
                .createdAt(u.getCreatedAt())
                .build();
    }

    @Transactional
    public UserResponse updateProfileByPhone(String phoneNumber, UserProfileUpdateRequest req) {

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with phone number: " + phoneNumber));

        if (req.getFullName() != null) user.setFullName(req.getFullName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getGender() != null) user.setGender(req.getGender());

        User savedUser = userRepository.save(user);

        log.info("Profile updated for user phone={}", phoneNumber);

        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getByPhone(String phoneNumber) {

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with phone number: " + phoneNumber));

        return toResponse(user);
    }
}