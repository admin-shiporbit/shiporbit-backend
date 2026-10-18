package com.shiporbit.backend.rate.core;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One end of a hyperlocal (same-city) shipment. Needed by Shree Maruti's hyperlocal serviceability API. */
public record HyperlocalAddress(
        @Size(max = 500) String address,
        @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String pincode,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
) {
    public boolean complete() {
        return address != null && !address.isBlank() && pincode != null && latitude != null && longitude != null;
    }
}
