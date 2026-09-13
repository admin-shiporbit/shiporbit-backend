package com.shiporbit.backend.rate.auth;

import java.time.Instant;

public record TokenResult (String token,Instant expiresAt){}
