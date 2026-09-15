package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.LoginDTO;
import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.dto.SessionUserDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.security.SessionCookieService;
import dev.uffs.doisag.service.AuthService;
import dev.uffs.doisag.service.PatientService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// entrada e saida do sistema
@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final AuthService authService;
    private final SessionCookieService sessionCookieService;
    private final PatientService patientService;

    public AuthenticationController(AuthService authService, SessionCookieService sessionCookieService,
                                    PatientService patientService) {
        this.authService = authService;
        this.sessionCookieService = sessionCookieService;
        this.patientService = patientService;
    }

    // confere a senha e devolve o cookie da sessao junto com os dados de quem entrou
    @PostMapping("/login")
    public SessionUserDTO login(@RequestBody @Valid LoginDTO loginData, HttpServletResponse response) {
        Users user = authService.login(loginData.email(), loginData.password());
        sessionCookieService.writeSession(response, user);
        return new SessionUserDTO(user);
    }

    // encerra as sessoes abertas de quem esta saindo e apaga o cookie
    // sem sessao valida so apaga o cookie
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Users loggedUser, HttpServletResponse response) {
        if (loggedUser != null) {
            authService.endSessions(loggedUser.getId());
        }
        sessionCookieService.clearSession(response);
        return ResponseEntity.noContent().build();
    }

    // quem esta logado agora
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER', 'ADMIN')")
    @GetMapping("/me")
    public SessionUserDTO getLoggedUser(@AuthenticationPrincipal Users loggedUser) {
        return new SessionUserDTO(loggedUser);
    }

    // o paciente cria a conta pelo link de convite e ja sai logado
    @PostMapping("/register")
    public ResponseEntity<SessionUserDTO> register(@RequestBody @Valid RegisterDTO registerData,
                                                   HttpServletResponse response) {
        Patient patient = patientService.registerPatient(registerData);
        sessionCookieService.writeSession(response, patient);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SessionUserDTO(patient));
    }
}
