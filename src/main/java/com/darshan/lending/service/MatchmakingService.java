
        package com.darshan.lending.service;

import com.darshan.lending.dto.LoanOfferResponse;
import com.darshan.lending.entity.LenderPreference;
import com.darshan.lending.entity.LoanOffer;
import com.darshan.lending.entity.LoanRequest;
import com.darshan.lending.entity.enums.LoanOfferStatus;
import com.darshan.lending.entity.enums.LoanRequestStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.LenderPreferenceRepository;
import com.darshan.lending.repository.LoanOfferRepository;
import com.darshan.lending.repository.LoanRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchmakingService {

    private final LoanRequestRepository      loanRequestRepository;
    private final LenderPreferenceRepository lenderPreferenceRepository;
    private final LoanOfferRepository        loanOfferRepository;
    private final LoanOfferService           loanOfferService;

    @Transactional
    public List<LoanOfferResponse> matchLoanRequest(Long requestId, int maxOffers) {

        LoanRequest request = loanRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Loan request not found: " + requestId));

        if (request.getStatus() != LoanRequestStatus.PENDING) {
            throw new BusinessException(
                    "Only PENDING requests can be matched. Current: " + request.getStatus());
        }

        BigDecimal requestAmount = request.getAmount();
        int        requestTenure = request.getTenureMonths();
        Long       loanProductId = request.getLoanProduct().getId();

        // Use the existing JPQL query that filters by product, amount and tenure
        List<LenderPreference> candidates = lenderPreferenceRepository
                .findMatchingLenders(loanProductId, requestAmount, requestTenure);

        if (candidates.isEmpty()) {
            log.warn("No matching lenders found for request id={}", requestId);
        }

        // Deduplicate by lender — keep preference with lowest max interest rate
        List<LenderPreference> ranked = candidates.stream()
                .collect(Collectors.toMap(
                        p -> p.getLender().getId(),
                        p -> p,
                        (a, b) -> a.getMaxInterestRate()
                                .compareTo(b.getMaxInterestRate()) <= 0 ? a : b
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(LenderPreference::getMaxInterestRate))
                .limit(maxOffers)
                .collect(Collectors.toList());

        List<LoanOffer> offers = new ArrayList<>();
        int rank = 1;

        for (LenderPreference pref : ranked) {
            if (loanOfferRepository.existsByLoanRequestIdAndLenderId(
                    requestId, pref.getLender().getId())) {
                log.debug("Offer already exists for lender={} request={} — skipping",
                        pref.getLender().getId(), requestId);
                continue;
            }

            LoanOffer offer = LoanOffer.builder()
                    .loanRequest(request)
                    .lender(pref.getLender())
                    .offeredInterestRate(pref.getMaxInterestRate())
                    .loanAmount(requestAmount)
                    .status(LoanOfferStatus.PENDING)
                    .matchRank(rank++)
                    .build();

            offers.add(loanOfferRepository.save(offer));
        }

        request.setStatus(LoanRequestStatus.MATCHED);
        loanRequestRepository.save(request);

        log.info("Matched request id={} -> {} offers created", requestId, offers.size());

        return offers.stream()
                .map(loanOfferService::toResponse)
                .collect(Collectors.toList());
    }
}
