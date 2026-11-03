package com.shiporbit.backend.rate.auth;

import java.time.Instant;
import java.util.Map;

public record TokenResult (Map<String,String> token, Instant expiresAt){}
