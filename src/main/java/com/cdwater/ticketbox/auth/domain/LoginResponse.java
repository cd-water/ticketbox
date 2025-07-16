package com.cdwater.ticketbox.auth.domain;

public record LoginResponse(String token, String username, String role) {
}
