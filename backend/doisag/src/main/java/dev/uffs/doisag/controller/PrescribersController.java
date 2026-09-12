package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.PrescriberCreateDTO;
import dev.uffs.doisag.dto.PrescriberResponseDTO;
import dev.uffs.doisag.dto.PrescriberUpdateDTO;
import dev.uffs.doisag.service.PrescriberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/prescritor")
public class PrescribersController {
    private final PrescriberService prescriberService;

    public PrescribersController(PrescriberService prescriberService) {
        this.prescriberService = prescriberService;
    }

    // conta de prescritor eh provisionada, n eh autocadastro (RF02.2).
    // antes essa rota era publica e criava conta com privilegio, ou seja
    // qualquer um da internet virava prescritor.
    // hoje ninguem tem ROLE_ADMIN ainda: quem cria prescritor eh o seed.
    // quando existir o perfil administrativo, ele entra por aqui
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<PrescriberResponseDTO> create(@RequestBody @Valid PrescriberCreateDTO dados) {
        var prescriber = prescriberService.create(dados);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PrescriberResponseDTO(prescriber));
    }

    // read all prescriber
    @PreAuthorize("hasRole('PRESCRIBER')")
    @GetMapping
    public List<PrescriberResponseDTO> getAll() {
        return prescriberService.getAll()
                .stream()
                .map(PrescriberResponseDTO::new)
                .toList();
    }

    // read by id prescriber. ele mesmo, ou um paciente da carteira dele
    @PreAuthorize("@patientAccess.canViewPrescriber(#id, authentication)")
    @GetMapping("/{id}")
    public ResponseEntity<PrescriberResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new PrescriberResponseDTO(prescriberService.getById(id)));
    }

    // update prescriber. so a propria ficha
    @PreAuthorize("@patientAccess.isSelf(#id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<PrescriberResponseDTO> update(@PathVariable Long id,
                                                        @RequestBody @Valid PrescriberUpdateDTO dados) {
        return ResponseEntity.ok(new PrescriberResponseDTO(prescriberService.update(id, dados)));
    }

    // delete prescriber. so a propria conta
    @PreAuthorize("@patientAccess.isSelf(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        prescriberService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
