package com.cdwater.ticketbox.auth.web;

import com.cdwater.ticketbox.auth.domain.UserProfileResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public UserProfileResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .orElse("ROLE_USER")
                .substring("ROLE_".length());
        return new UserProfileResponse(authentication.getName(), role);
    }
}
