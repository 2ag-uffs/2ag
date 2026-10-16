package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.AvailabilityDTO;
import dev.uffs.doisag.dto.DayOffDTO;
import dev.uffs.doisag.dto.DayOffResponseDTO;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.service.AvailabilityService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// horarios de atendimento do prescritor logado (RF11)
@RestController
@RequestMapping("/availability")
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

    // dias sem atendimento de hoje em diante
    // a folga eh achada pela data e pelo prescritor logado, sem id no caminho
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping("/days-off")
    public List<DayOffResponseDTO> getMyDaysOff(@AuthenticationPrincipal Prescriber loggedPrescriber) {
        return availabilityService.getDaysOff(loggedPrescriber.getId());
    }

    @PreAuthorize("hasRole('PRESCRIBER')")
    @PostMapping("/days-off")
    public ResponseEntity<DayOffResponseDTO> addMyDayOff(@RequestBody @Valid DayOffDTO dayOffData,
                                                         @AuthenticationPrincipal Prescriber loggedPrescriber) {
        DayOffResponseDTO dayOff = availabilityService.addDayOff(loggedPrescriber.getId(), dayOffData);
        return ResponseEntity.status(HttpStatus.CREATED).body(dayOff);
    }

    @PreAuthorize("hasRole('PRESCRIBER')")
    @DeleteMapping("/days-off")
    public ResponseEntity<Void> removeMyDayOff(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Prescriber loggedPrescriber) {
        availabilityService.removeDayOff(loggedPrescriber.getId(), date);
        return ResponseEntity.noContent().build();
    }
}
