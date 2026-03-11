package com.darshan.lending.repository;

import com.darshan.lending.entity.BankAccount;
import com.darshan.lending.entity.enums.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByUserId(Long userId);
    Optional<BankAccount> findByUserIdAndAccountType(Long userId, AccountType accountType);
    Optional<BankAccount> findByAccountNumber(String accountNumber);

}