package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.ChangePasswordDTO;
import dev.uffs.doisag.dto.EmailChangeDTO;
import dev.uffs.doisag.dto.EmailPreferenceDTO;
import dev.uffs.doisag.dto.ProfileDTO;
import dev.uffs.doisag.dto.ProfileUpdateDTO;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.security.SessionCookieService;
import dev.uffs.doisag.service.PasswordService;
import dev.uffs.doisag.service.ProfileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// perfil da propria conta (RF18)
// toda rota usa a conta da sessao entao nenhuma recebe id
@RestController
@RequestMapping("/profile")
@PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER', 'ADMIN')")
public class ProfileController {

    private final ProfileService profileService;
    private final PasswordService passwordService;
    private final SessionCookieService sessionCookieService;

    public ProfileController(ProfileService profileService, PasswordService passwordService,
                             SessionCookieService sessionCookieService) {
        this.profileService = profileService;
        this.passwordService = passwordService;
        this.sessionCookieService = sessionCookieService;
    }

    @GetMapping
    public ProfileDTO getProfile(@AuthenticationPrincipal Users loggedUser) {
        return profileService.getProfile(loggedUser.getId());
    }

    @PutMapping
    public ProfileDTO updateProfile(@AuthenticationPrincipal Users loggedUser,
                                    @RequestBody @Valid ProfileUpdateDTO profileData) {
        return profileService.updateProfile(loggedUser.getId(), profileData);
    }

    @PutMapping("/email")
    public ProfileDTO changeEmail(@AuthenticationPrincipal Users loggedUser,
                                  @RequestBody @Valid EmailChangeDTO emailData) {
        return profileService.changeEmail(loggedUser.getId(), emailData);
    }

    @PutMapping("/email-preference")
    public ProfileDTO changeEmailPreference(@AuthenticationPrincipal Users loggedUser,
                                            @RequestBody @Valid EmailPreferenceDTO preferenceData) {
        return profileService.changeEmailPreference(loggedUser.getId(), preferenceData);
    }

    // a troca de senha derruba as sessoes abertas antes dela
    // esse aparelho recebe um cookie novo e continua logado
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Users loggedUser,
                                               @RequestBody @Valid ChangePasswordDTO passwordData,
                                               HttpServletResponse response) {
        Users updatedUser = passwordService.changePassword(loggedUser.getId(), passwordData);
        sessionCookieService.writeSession(response, updatedUser);
        return ResponseEntity.noContent().build();
    }
}
