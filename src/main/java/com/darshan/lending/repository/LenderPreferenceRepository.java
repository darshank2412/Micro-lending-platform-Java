package com.darshan.lending.repository;

import com.darshan.lending.entity.LenderPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LenderPreferenceRepository extends JpaRepository<LenderPreference, Long> {

    // All preferences for a lender
    List<LenderPreference> findByLenderId(Long lenderId);

    // Specific preference for a lender and loan product
    Optional<LenderPreference> findByLenderIdAndLoanProductId(Long lenderId, Long loanProductId);

    // Find active lenders whose preferences match a loan request
    @Query("""
            SELECT lp FROM LenderPreference lp
            WHERE lp.isActive = true
            AND lp.loanProduct.id = :loanProductId
            AND lp.minLoanAmount  <= :amount
            AND lp.maxLoanAmount  >= :amount
            AND lp.minTenureMonths <= :tenure
            AND lp.maxTenureMonths >= :tenure
            """)
    List<LenderPreference> findMatchingLenders(
            @Param("loanProductId") Long loanProductId,
            @Param("amount") BigDecimal amount,
            @Param("tenure") Integer tenure);

    // All active preferences for a lender
    List<LenderPreference> findByLenderIdAndIsActiveTrue(Long lenderId);


}