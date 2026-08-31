package com.shiporbit.backend.dto;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.io.Decoders;
import com.shiporbit.backend.security.ShipOrbitUserPrincipal;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    //----------------------------
    //1- GET SIGNING KEYS
    //----------------------------
    private SecretKey getSigningKey(){
        byte[] keybytes = Decoders.BASE64.decode(secret);

        return Keys.hmacShaKeyFor(keybytes);
    }

    //----------------------------
    //2- GENERATE TOKEN
    //----------------------------
    public String generateToken(ShipOrbitUserPrincipal userDetails){
        Instant now = Instant.now();
        return Jwts
                .builder()
                .subject(userDetails.userId().toString())
                .claim("email", userDetails.email())
                .claim("roles",
                        userDetails.getAuthorities()
                                .stream()
                                .map(GrantedAuthority::getAuthority)
                                .toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(getSigningKey())
                .compact();
    }

    public long getExpiration() {
        return expiration;
    }
    //----------------------------
    //3- EXTRACT user ID and email from JWT
    //----------------------------

    public UUID extractUserId(String token){
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractEmail(String token){
        return extractAllClaims(token).get("email", String.class);
    }
    //----------------------------
    //4- EXTRACT ALL CLAIMS
    //----------------------------
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey()).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ------------------------------------------------
    // 5. Check if token is valid
    // ------------------------------------------------

    public boolean isTokenValid(
            String token,
            ShipOrbitUserPrincipal userDetails) {
        UUID userId = extractUserId(token);
        String email = extractEmail(token);

        return userId.equals(userDetails.userId())
                && email.equalsIgnoreCase(userDetails.email())
                && userDetails.isEnabled()
                && !isTokenExpired(token);
    }


    // ------------------------------------------------
    // 6. Check expiration
    // ------------------------------------------------

    private boolean isTokenExpired(String token) {

        Date expiration =
                extractAllClaims(token)
                        .getExpiration();

        return expiration.before(new java.util.Date());
    }
}
