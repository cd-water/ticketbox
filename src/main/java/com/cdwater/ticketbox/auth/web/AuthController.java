package com.cdwater.ticketbox.auth.web;

import com.cdwater.ticketbox.auth.domain.AuthService;
import com.cdwater.ticketbox.auth.domain.LoginResponse;
import com.cdwater.ticketbox.auth.domain.UserAccount;
import com.cdwater.ticketbox.auth.domain.UserProfileResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 6, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfileResponse register(@Valid @RequestBody RegisterRequest request) {
        UserAccount user = authService.register(request.username().trim(), request.password());
        return new UserProfileResponse(user.getUsername(), user.getRole().name());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username().trim(), request.password());
        return new LoginResponse(
                result.token(),
                result.user().getUsername(),
                result.user().getRole().name());
    }
}
