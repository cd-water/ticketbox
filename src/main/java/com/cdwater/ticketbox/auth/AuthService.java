package com.cdwater.ticketbox.auth;

import com.cdwater.ticketbox.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record LoginResult(String token, UserAccount user) {
    }

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserAccount register(String username, String password) {
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username already exists");
        }
        try {
            return users.saveAndFlush(new UserAccount(username, passwordEncoder.encode(password), Role.USER));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username already exists");
        }
    }

    @Transactional(readOnly = true)
    public LoginResult login(String username, String password) {
        UserAccount user = users.findByUsername(username)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password"));
        return new LoginResult(jwtService.issue(user), user);
    }
}
