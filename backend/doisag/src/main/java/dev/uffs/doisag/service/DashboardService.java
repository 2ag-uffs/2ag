package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.PatientDashboardDTO;
import dev.uffs.doisag.dto.PrescriberDashboardDTO;
import dev.uffs.doisag.dto.PrescriberDashboardDTO.AppointmentSummaryDTO;
import dev.uffs.doisag.enums.AssignmentStatus;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.AssignedScaleRepository;
import dev.uffs.doisag.repository.PatientRepository;
import org.springframework.stereotype.Service;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    // injetando os repositórios que a gente vai precisar consultar
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AssignedScaleRepository assignedScaleRepository;

    public DashboardService(
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            AssignedScaleRepository assignedScaleRepository
    ) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.assignedScaleRepository = assignedScaleRepository;
    }


    // método que monta o dashboard do prescritor, ele junta info de vários lugares e empacota no dto
    public PrescriberDashboardDTO getPrescriberDashboard(Long prescriberId) {

        // busca o número de pacientes ativos
        long activePatients = patientRepository.countByPrescriberId(prescriberId);

        // busca as consultas agendadas pra hoje
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay(); // comecinho do dia de hoje
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX); // finalzão do dia de hoje

        // declara a variável todaysAppointments
        List<AppointmentSummaryDTO> todaysAppointments = appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriberId, startOfDay, endOfDay)
                .stream()
                .map(apt -> new AppointmentSummaryDTO(apt.getId(), apt.getPatient().getName(), apt.getModality()))
                .collect(Collectors.toList());

        // busca as escalas pendentes que o prescritor designou
        List<PrescriberDashboardDTO.PendingFormSummaryDTO> pendingForms = assignedScaleRepository
                // uso o enum importado diretamente
                .findByPrescriberIdAndStatus(prescriberId, AssignmentStatus.PENDENTE)
                .stream()
                .map(scale -> new PrescriberDashboardDTO.PendingFormSummaryDTO(
                        scale.getId(),
                        scale.getPatient().getName(),
                        scale.getScaleType().toString() // converte o enum pra string
                ))
                .toList();

        // montando o objeto final que vai pro front
        return new PrescriberDashboardDTO(
                activePatients,
                todaysAppointments.size(),
                pendingForms.size(),
                todaysAppointments,
                pendingForms
        );
    }

    //método que monta o dashboard do paciente
    public PatientDashboardDTO getPatientDashboard(Long patientId) {

        // implementar a lógica pra buscar consultas futuras do paciente
        List<PatientDashboardDTO.UpcomingAppointmentDTO> upcomingAppointments = Collections.emptyList();

        // busca as escalas pendentes para o paciente logado
        List<PatientDashboardDTO.PendingScaleDTO> pendingScales = assignedScaleRepository
                .findByPatientIdAndStatus(patientId, AssignmentStatus.PENDENTE)
                .stream()
                .map(scale -> new PatientDashboardDTO.PendingScaleDTO(
                        // Usamos os novos métodos do enum para preencher o DTO
                        scale.getScaleType().getDisplayName(), // pega o nome amigável
                        scale.getStatus().toString(),
                        scale.getScaleType().getPath()      // pega a rota
                ))
                .collect(Collectors.toList());

        // montando o dto do paciente
        return new PatientDashboardDTO(upcomingAppointments, pendingScales);
    }
}