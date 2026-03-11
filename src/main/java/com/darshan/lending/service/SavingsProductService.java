package com.darshan.lending.service;

import com.darshan.lending.dto.SavingsProductRequest;
import com.darshan.lending.dto.SavingsProductResponse;
import com.darshan.lending.entity.SavingsProduct;
import com.darshan.lending.entity.enums.ProductStatus;
import com.darshan.lending.exception.BusinessException;
import com.darshan.lending.exception.ResourceNotFoundException;
import com.darshan.lending.repository.SavingsProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsProductService {

    private final SavingsProductRepository repo;

    @Transactional
    public SavingsProductResponse create(SavingsProductRequest req) {
        if (req.getMinBalance().compareTo(req.getMaxBalance()) >= 0) {
            throw new BusinessException("minBalance must be less than maxBalance");
        }
        if (req.getInterestRate().compareTo(new java.math.BigDecimal("20.00")) > 0) {
            throw new BusinessException("Interest rate cannot exceed 20%");
        }
        if (repo.existsByName(req.getName())) {
            throw new BusinessException("Savings product already exists with name: " + req.getName());
        }
        SavingsProduct product = SavingsProduct.builder()
                .name(req.getName())
                .minBalance(req.getMinBalance())
                .maxBalance(req.getMaxBalance())
                .interestRate(req.getInterestRate())
                .status(ProductStatus.ACTIVE)
                .build();
        log.info("Savings product created: {}", req.getName());
        return toResponse(repo.save(product));
    }

    @Transactional(readOnly = true)
    public List<SavingsProductResponse> getAll() {
        return repo.findByStatus(ProductStatus.ACTIVE)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SavingsProductResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional
    public SavingsProductResponse update(Long id, SavingsProductRequest req) {
        if (req.getInterestRate().compareTo(new java.math.BigDecimal("20.00")) > 0) {
            throw new BusinessException("Interest rate cannot exceed 20%");
        }
        SavingsProduct product = findById(id);
        product.setName(req.getName());
        product.setMinBalance(req.getMinBalance());
        product.setMaxBalance(req.getMaxBalance());
        product.setInterestRate(req.getInterestRate());
        log.info("Savings product updated: id={}", id);
        return toResponse(repo.save(product));
    }

    @Transactional
    public void deactivate(Long id) {
        SavingsProduct product = findById(id);
        product.setStatus(ProductStatus.INACTIVE);
        repo.save(product);
        log.info("Savings product deactivated: id={}", id);
    }

    public SavingsProduct findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Savings product not found: " + id));
    }

    private SavingsProductResponse toResponse(SavingsProduct p) {
        return SavingsProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .minBalance(p.getMinBalance())
                .maxBalance(p.getMaxBalance())
                .interestRate(p.getInterestRate())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .build();
    }
}