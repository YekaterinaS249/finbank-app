package com.finbank.dto;

public class JwtResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresInSeconds;
    private String email;

    public JwtResponse(String token, long expiresInSeconds, String email) {
        this.token = token;
        this.expiresInSeconds = expiresInSeconds;
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public String getEmail() {
        return email;
    }
}
