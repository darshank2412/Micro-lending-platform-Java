package com.darshan.lending.service;

import com.darshan.lending.dto.LoanProductRequest;
import com.darshan.lending.dto.LoanProductResponse;
import com.darshan.lending.entity.LoanProduct;
import com.darshan.lending.entity.enums.ProductStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.LoanProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanProductService {

    private final LoanProductRepository repo;

    @Transactional
    public LoanProductResponse create(LoanProductRequest req) {
        validateRanges(req);
        if (repo.existsByName(req.getName())) {
            throw new BusinessException("Loan product already exists with name: " + req.getName());
        }
        LoanProduct product = LoanProduct.builder()
                .name(req.getName())
                .minAmount(req.getMinAmount())
                .maxAmount(req.getMaxAmount())
                .minInterest(req.getMinInterest())
                .maxInterest(req.getMaxInterest())
                .minTenure(req.getMinTenure())
                .maxTenure(req.getMaxTenure())
                .status(ProductStatus.ACTIVE)
                .build();
        log.info("Loan product created: {}", req.getName());
        return toResponse(repo.save(product));
    }

    @Transactional(readOnly = true)
    public List<LoanProductResponse> getAll() {
        return repo.findByStatus(ProductStatus.ACTIVE)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LoanProductResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public LoanProductResponse update(Long id, LoanProductRequest req) {
        validateRanges(req);
        LoanProduct product = findById(id);
        product.setName(req.getName());
        product.setMinAmount(req.getMinAmount());
        product.setMaxAmount(req.getMaxAmount());
        product.setMinInterest(req.getMinInterest());
        product.setMaxInterest(req.getMaxInterest());
        product.setMinTenure(req.getMinTenure());
        product.setMaxTenure(req.getMaxTenure());
        log.info("Loan product updated: id={}", id);
        return toResponse(repo.save(product));
    }

    @Transactional
    public void delete(Long id) {
        LoanProduct product = findById(id);
        product.setStatus(ProductStatus.INACTIVE);
        repo.save(product);
        log.info("Loan product deactivated: id={}", id);
    }

    public LoanProduct findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product not found: " + id));
    }

    private void validateRanges(LoanProductRequest req) {
        if (req.getMinAmount().compareTo(req.getMaxAmount()) >= 0) {
            throw new BusinessException("minAmount must be less than maxAmount");
        }
        if (req.getMinInterest().compareTo(req.getMaxInterest()) >= 0) {
            throw new BusinessException("minInterest must be less than maxInterest");
        }
        if (req.getMinTenure() >= req.getMaxTenure()) {
            throw new BusinessException("minTenure must be less than maxTenure");
        }
    }

    private LoanProductResponse toResponse(LoanProduct p) {
        return LoanProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .minAmount(p.getMinAmount())
                .maxAmount(p.getMaxAmount())
                .minInterest(p.getMinInterest())
                .maxInterest(p.getMaxInterest())
                .minTenure(p.getMinTenure())
                .maxTenure(p.getMaxTenure())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .build();
    }
}