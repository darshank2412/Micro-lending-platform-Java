package com.darshan.lending.service;

import com.darshan.lending.dto.LenderPreferenceDto;
import com.darshan.lending.dto.LenderPreferenceResponse;
import com.darshan.lending.entity.LenderPreference;
import com.darshan.lending.entity.LoanProduct;
import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.ProductStatus;
import com.darshan.lending.entity.enums.Role;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.LenderPreferenceRepository;
import com.darshan.lending.repository.LoanProductRepository;
import com.darshan.lending.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LenderPreferenceService {

    private final LenderPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final LoanProductRepository loanProductRepository;

    // ── LENDER: Save or update preference for a specific loan product ─────────
    @Transactional
    public LenderPreferenceResponse savePreference(Long lenderId, LenderPreferenceDto dto) {

        User lender = findUser(lenderId);

        if (lender.getRole() != Role.LENDER) {
            throw new BusinessException("Only LENDER users can set lending preferences");
        }

        LoanProduct loanProduct = loanProductRepository.findById(dto.getLoanProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan product not found: " + dto.getLoanProductId()));

        if (loanProduct.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("Loan product is not active");
        }

        // Validate ranges
        if (dto.getMinInterestRate().compareTo(dto.getMaxInterestRate()) >= 0) {
            throw new BusinessException("Min interest rate must be less than max interest rate");
        }
        if (dto.getMinTenureMonths() >= dto.getMaxTenureMonths()) {
            throw new BusinessException("Min tenure must be less than max tenure");
        }
        if (dto.getMinLoanAmount().compareTo(dto.getMaxLoanAmount()) >= 0) {
            throw new BusinessException("Min loan amount must be less than max loan amount");
        }

        // Validate against loan product limits
        if (dto.getMinLoanAmount().compareTo(loanProduct.getMinAmount()) < 0) {
            throw new BusinessException("Min loan amount cannot be less than product minimum: "
                    + loanProduct.getMinAmount());
        }
        if (dto.getMaxLoanAmount().compareTo(loanProduct.getMaxAmount()) > 0) {
            throw new BusinessException("Max loan amount cannot exceed product maximum: "
                    + loanProduct.getMaxAmount());
        }
        if (dto.getMinTenureMonths() < loanProduct.getMinTenure()) {
            throw new BusinessException("Min tenure cannot be less than product minimum: "
                    + loanProduct.getMinTenure());
        }
        if (dto.getMaxTenureMonths() > loanProduct.getMaxTenure()) {
            throw new BusinessException("Max tenure cannot exceed product maximum: "
                    + loanProduct.getMaxTenure());
        }

        // Upsert — update if exists for this lender+product, create if not
        LenderPreference preference = preferenceRepository
                .findByLenderIdAndLoanProductId(lenderId, dto.getLoanProductId())
                .orElse(LenderPreference.builder()
                        .lender(lender)
                        .loanProduct(loanProduct)
                        .build());

        preference.setMinInterestRate(dto.getMinInterestRate());
        preference.setMaxInterestRate(dto.getMaxInterestRate());
        preference.setMinTenureMonths(dto.getMinTenureMonths());
        preference.setMaxTenureMonths(dto.getMaxTenureMonths());
        preference.setMinLoanAmount(dto.getMinLoanAmount());
        preference.setMaxLoanAmount(dto.getMaxLoanAmount());
        preference.setRiskAppetite(dto.getRiskAppetite());
        preference.setIsActive(true);

        LenderPreference saved = preferenceRepository.save(preference);
        log.info("Lender preference saved for lenderId={} loanProductId={}",
                lenderId, dto.getLoanProductId());
        return toResponse(saved);
    }

    // ── LENDER: Get all my preferences (one per loan product) ─────────────────
    @Transactional(readOnly = true)
    public List<LenderPreferenceResponse> getMyPreferences(Long lenderId) {
        User lender = findUser(lenderId);
        if (lender.getRole() != Role.LENDER) {
            throw new BusinessException("Only LENDER users can view lending preferences");
        }
        return preferenceRepository.findByLenderId(lenderId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── LENDER: Deactivate preference for a specific loan product ─────────────
    @Transactional
    public LenderPreferenceResponse deactivate(Long lenderId, Long loanProductId) {
        LenderPreference pref = preferenceRepository
                .findByLenderIdAndLoanProductId(lenderId, loanProductId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No preference found for lender: " + lenderId
                                + " and loan product: " + loanProductId));
        pref.setIsActive(false);
        log.info("Lender preference deactivated for lenderId={} loanProductId={}",
                lenderId, loanProductId);
        return toResponse(preferenceRepository.save(pref));
    }

    // ── ADMIN: Get all active preferences ────────────────────────────────────
    @Transactional(readOnly = true)
    public List<LenderPreferenceResponse> getAllActive() {
        return preferenceRepository.findAll()
                .stream()
                .filter(LenderPreference::getIsActive)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public LenderPreferenceResponse toResponse(LenderPreference p) {
        return LenderPreferenceResponse.builder()
                .id(p.getId())
                .lenderId(p.getLender().getId())
                .lenderName(p.getLender().getFullName())
                .loanProductId(p.getLoanProduct().getId())
                .loanProductName(p.getLoanProduct().getName())
                .minInterestRate(p.getMinInterestRate())
                .maxInterestRate(p.getMaxInterestRate())
                .minTenureMonths(p.getMinTenureMonths())
                .maxTenureMonths(p.getMaxTenureMonths())
                .minLoanAmount(p.getMinLoanAmount())
                .maxLoanAmount(p.getMaxLoanAmount())
                .riskAppetite(p.getRiskAppetite())
                .isActive(p.getIsActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}