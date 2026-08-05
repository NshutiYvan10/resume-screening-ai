package com.resumeai.web;

import com.resumeai.dto.ProfileDtos.*;
import com.resumeai.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * The caller's own profile. Every method acts on the authenticated user, so there is
 * no id in any path and no role annotation beyond "must be signed in".
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public MyProfileResponse me() {
        return profileService.myProfile();
    }

    /** Company admin / recruiter / platform admin profile. */
    @PutMapping("/staff")
    public MyProfileResponse updateStaff(@Valid @RequestBody StaffProfileRequest request) {
        return profileService.updateStaffProfile(request);
    }

    @PutMapping("/candidate")
    public MyProfileResponse updateCandidate(@Valid @RequestBody CandidateProfileRequest request) {
        return profileService.updateCandidateProfile(request);
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MyProfileResponse uploadPhoto(@RequestParam("file") MultipartFile file) {
        return profileService.uploadPhoto(file);
    }

    @DeleteMapping("/photo")
    public MyProfileResponse removePhoto() {
        return profileService.removePhoto();
    }

    /** Voluntary demographics: its own endpoint so it never shares a payload with the profile. */
    @PutMapping("/demographics")
    public MyProfileResponse updateDemographics(@Valid @RequestBody DemographicsRequest request) {
        return profileService.updateDemographics(request);
    }

    @DeleteMapping("/demographics")
    public MyProfileResponse deleteDemographics() {
        return profileService.deleteDemographics();
    }
}
