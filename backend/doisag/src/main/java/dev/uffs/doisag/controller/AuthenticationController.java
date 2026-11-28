package dev.uffs.doisag.controller;

import dev.uffs.doisag.dto.LoginDTO;
import dev.uffs.doisag.dto.RegisterDTO;
import dev.uffs.doisag.dto.SessionUserDTO;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.security.SecurityFilter;
import dev.uffs.doisag.security.SessionCookieService;
import dev.uffs.doisag.service.AuthService;
import dev.uffs.doisag.service.ConsentTermService;
import dev.uffs.doisag.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ConsentTermService consentTermService;

    public AuthenticationController(AuthService authService, SessionCookieService sessionCookieService,
                                    PatientService patientService, ConsentTermService consentTermService) {
        this.authService = authService;
        this.sessionCookieService = sessionCookieService;
        this.patientService = patientService;
        this.consentTermService = consentTermService;
    }

    // confere a senha e devolve o cookie da sessao junto com os dados de quem entrou
    // o endereco de quem tenta entra na contagem de senhas erradas
    @PostMapping("/login")
    public SessionUserDTO login(@RequestBody @Valid LoginDTO loginData, HttpServletRequest request,
                                HttpServletResponse response) {
        AuthService.CheckedLogin checkedLogin = authService.login(loginData.email(), loginData.password(),
                request.getRemoteAddr());
        Users user = checkedLogin.user();
        sessionCookieService.writeSession(response, user, checkedLogin.loginAt());
        return new SessionUserDTO(user, consentTermService.needsNewAcceptance(user),
                consentTermService.isPendingOnlyByAge(user));
    }

    // encerra as sessoes abertas de quem esta saindo e apaga o cookie
    // sem sessao valida so apaga o cookie
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Users loggedUser, HttpServletRequest request,
                                       HttpServletResponse response) {
        if (loggedUser != null) {
            authService.endSessions(loggedUser.getId());
        } else if (request.getAttribute(SecurityFilter.DEACTIVATED_ACCOUNT_ATTRIBUTE) instanceof Long deactivatedUserId) {
            // a sessao da conta desativada n entra, mas sair por ela tbm encerra as sessoes. senao o valor
            // do cookie copiado antes continuava dizendo q a conta foi desativada (CT-41)
            authService.endSessions(deactivatedUserId);
        }
        sessionCookieService.clearSession(response);
        return ResponseEntity.noContent().build();
    }

    // quem esta logado agora
    @PreAuthorize("hasAnyRole('PATIENT', 'PRESCRIBER', 'ADMIN')")
    @GetMapping("/me")
    public SessionUserDTO getLoggedUser(@AuthenticationPrincipal Users loggedUser) {
        return new SessionUserDTO(loggedUser, consentTermService.needsNewAcceptance(loggedUser),
                consentTermService.isPendingOnlyByAge(loggedUser));
    }

    // o paciente cria a conta pelo link de convite e ja sai logado
    @PostMapping("/register")
    public ResponseEntity<SessionUserDTO> register(@RequestBody @Valid RegisterDTO registerData,
                                                   HttpServletRequest request, HttpServletResponse response) {
        Patient patient = patientService.registerPatient(registerData, request.getRemoteAddr());
        sessionCookieService.writeSession(response, patient);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SessionUserDTO(patient));
    }
}
