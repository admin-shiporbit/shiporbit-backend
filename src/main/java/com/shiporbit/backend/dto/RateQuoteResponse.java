package com.shiporbit.backend.dto;

import com.shiporbit.backend.rate.dto.RateBreakdown;

public record RateQuoteResponse(
        String partnerCode,
        String partnerName,
        RateBreakdown breakdown
) {
}
