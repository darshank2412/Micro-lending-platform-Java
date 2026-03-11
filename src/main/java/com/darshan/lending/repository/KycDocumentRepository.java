package com.darshan.lending.repository;

import com.darshan.lending.entity.KycDocument;
import com.darshan.lending.entity.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    List<KycDocument> findByUserId(Long userId);
    Optional<KycDocument> findByUserIdAndDocumentType(Long userId, DocumentType documentType);
    boolean existsByUserIdAndDocumentType(Long userId, DocumentType documentType);
}
