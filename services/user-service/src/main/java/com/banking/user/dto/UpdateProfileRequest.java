package com.banking.user.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String preferredLanguage;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Invalid currency code")
    private String preferredCurrency;
}
