package com.darshan.lending.security;

import com.darshan.lending.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        // ✅ Return the User entity directly — it already implements UserDetails
        return userRepository.findByPhoneNumber(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username));
    }
}