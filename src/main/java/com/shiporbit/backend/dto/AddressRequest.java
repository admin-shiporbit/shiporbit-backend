package com.shiporbit.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotBlank
        @Size(max = 48)
        String label,

        @NotBlank
        @Pattern(
                regexp = "\\d{1,3}",
                message = "isdCode must contain 1 to 3 digits"
        )
        String isdCode,


        @NotBlank
        @Pattern(
                regexp = "\\d{10}",
                message = "Phone Number must contain 10 digit"
        )
        String phoneNumber,

        @NotBlank
        @Size(max = 256)
        String addressLine1,

        @Size(max = 256)
        String addressLine2,

        @NotBlank
        @Size(max = 200)
        String city,

        @NotBlank
        @Size(max = 10)
        String stateCode,

        @NotBlank
        @Pattern(
                regexp = "\\d{6}",
                message = "Pin Code must contain six digits exactly"
        )
        String pinCode,

        boolean isDefault
) {
}
