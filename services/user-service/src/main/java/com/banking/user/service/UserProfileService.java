package com.banking.user.service;

import com.banking.common.exception.ResourceNotFoundException;
import com.banking.user.dto.UpdateProfileRequest;
import com.banking.user.entity.UserProfile;
import com.banking.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfile getProfile(UUID authUserId) {
        return userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile", authUserId.toString()));
    }

    @Transactional
    public UserProfile updateProfile(UUID authUserId, UpdateProfileRequest request) {
        UserProfile profile = getProfile(authUserId);
        if (request.getFirstName() != null) profile.setFirstName(request.getFirstName());
        if (request.getLastName() != null) profile.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) profile.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getState() != null) profile.setState(request.getState());
        if (request.getPostalCode() != null) profile.setPostalCode(request.getPostalCode());
        if (request.getCountry() != null) profile.setCountry(request.getCountry());
        if (request.getPreferredLanguage() != null) profile.setPreferredLanguage(request.getPreferredLanguage());
        if (request.getPreferredCurrency() != null) profile.setPreferredCurrency(request.getPreferredCurrency());
        return userProfileRepository.save(profile);
    }

    @Transactional
    public UserProfile submitKyc(UUID authUserId, String docType, String docNumber) {
        UserProfile profile = getProfile(authUserId);
        profile.setKycDocumentType(docType);
        profile.setKycDocumentNumber(docNumber);
        profile.setKycStatus(UserProfile.KycStatus.SUBMITTED);
        log.info("KYC submitted for user: {}", authUserId);
        return userProfileRepository.save(profile);
    }
}
