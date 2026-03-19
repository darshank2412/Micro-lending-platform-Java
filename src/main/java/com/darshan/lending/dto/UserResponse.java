package com.darshan.lending.dto;

import com.darshan.lending.entity.enums.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserResponse {
    private Long id;
    private String countryCode;
    private String mobileNumber;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private LocalDate dateOfBirth;
    private Gender gender;
    private Role role;
    private UserStatus status;
    private KycStatus kycStatus;
    private String incomeBracket;
    private P2pExperience p2pExperience;
    private AddressDto address;
    private String platformAccountNumber;
    private LocalDateTime createdAt;
}