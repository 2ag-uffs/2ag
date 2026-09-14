package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AvailabilityDTO;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.AvailabilityService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// horarios de atendimento do prescritor logado (RF11)
@RestController
@RequestMapping("/agenda/disponibilidade")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public AvailabilityDTO getMyAvailability(@AuthenticationPrincipal Prescriber loggedPrescriber) {
        return availabilityService.getAvailability(loggedPrescriber.getId());
    }

    // a semana enviada substitui a anterior inteira
    @PreAuthorize("hasRole('PRESCRIBER')")
    @PutMapping
    public AvailabilityDTO replaceMyAvailability(@RequestBody @Valid AvailabilityDTO availabilityData,
                                                 @AuthenticationPrincipal Prescriber loggedPrescriber) {
        return availabilityService.replaceAvailability(loggedPrescriber.getId(), availabilityData);
    }
}
