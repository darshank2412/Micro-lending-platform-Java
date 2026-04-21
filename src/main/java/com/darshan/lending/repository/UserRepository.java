package com.darshan.lending.repository;

import com.darshan.lending.entity.User;
import com.darshan.lending.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByPan(String pan);
    boolean existsByRole(Role role);
    long countByRole(Role role);                   // ✅ NEW
    List<User> findAllByRole(Role role);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    Optional<User> findByResetToken(String resetToken);

}
