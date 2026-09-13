package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.dto.AppointmentCreateDTO;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.AppointmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class AppointmentService {

    // injecoes
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private NotificationService notificationService;

    // removemos o NotificationService do construtor
    public AppointmentService(AppointmentRepository appointmentRepository, PatientRepository patientRepository) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
    }

    // criamos um metodo setter para o spring injetar a dependencia depois
    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // create. o prescritor vem de quem esta logado, n do corpo
    public Appointment create(AppointmentCreateDTO dados, Prescriber prescriber) {
        Patient patient = patientRepository.findById(dados.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + dados.patientId()));

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPrescriber(prescriber);
        appointment.setDateTime(dados.dateTime());
        appointment.setModality(dados.modality());
        appointment.setStatus(dados.status());
        appointment.setDiagnosis(dados.diagnosis());
        appointment.setClinicalObservation(dados.clinicalObservation());
        appointment.setTherapeuticPlan(dados.therapeuticPlan());
        appointment.setEvolution(dados.evolution());
        appointment.setPhysicalExam(dados.physicalExam());
        appointment.setComplementaryExams(dados.complementaryExams());
        appointment.setBloodPressure(dados.bloodPressure());
        appointment.setWeight(dados.weight());
        appointment.setHeight(dados.height());
        // sem duracao a grade da agenda n sabe ate quando o horario
        // esta ocupado, entao uma hora eh o padrao
        appointment.setDurationMinutes(dados.durationMinutes() == null ? 60 : dados.durationMinutes());

        Appointment savedAppointment = appointmentRepository.save(appointment);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
        String formattedDateTime = savedAppointment.getDateTime().format(formatter);

        notificationService.createNotification(
                savedAppointment.getPatient(),
                "Consulta Agendada!",
                "Sua consulta com " + savedAppointment.getPrescriber().getName() + " foi agendada para " + formattedDateTime + ".",
                "APPOINTMENT",
                "/agendamento-consulta"
        );

        notificationService.createNotification(
                savedAppointment.getPrescriber(),
                "Novo Agendamento",
                "Você tem uma nova consulta com " + savedAppointment.getPatient().getName() + " em " + formattedDateTime + ".",
                "APPOINTMENT",
                "/agendamento-prescritor"
        );

        return savedAppointment;
    }

    public List<Appointment> getAll() {
        return appointmentRepository.findAll();
    }

    public Appointment getById(Long id) {
        return appointmentRepository.findById(id).
                orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado com o id: " + id));

    }

    public Appointment update(Long id, AppointmentCreateDTO dados) {
        Appointment appointment = getById(id);
        appointment.setDateTime(dados.dateTime());
        appointment.setModality(dados.modality());
        appointment.setStatus(dados.status());
        appointment.setDiagnosis(dados.diagnosis());
        appointment.setClinicalObservation(dados.clinicalObservation());
        appointment.setTherapeuticPlan(dados.therapeuticPlan());
        appointment.setEvolution(dados.evolution());
        appointment.setPhysicalExam(dados.physicalExam());
        appointment.setComplementaryExams(dados.complementaryExams());
        appointment.setBloodPressure(dados.bloodPressure());
        appointment.setWeight(dados.weight());
        appointment.setHeight(dados.height());
        if (dados.durationMinutes() != null) {
            appointment.setDurationMinutes(dados.durationMinutes());
        }
        return appointmentRepository.save(appointment);
    }

    // consultas de um prescritor especifico
    public List<Appointment> getByPrescriberId(Long prescriberId) {
        return appointmentRepository.findByPrescriberId(prescriberId);
    }

    public void delete(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Consulta não encontrada para o id :: " + id));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
        String formattedDateTime = appointment.getDateTime().format(formatter);

        notificationService.createNotification(
                appointment.getPatient(),
                "Consulta Cancelada",
                "Sua consulta com " + appointment.getPrescriber().getName() + " do dia " + formattedDateTime + " foi cancelada.",
                "ALERT",
                "/agendamento-consulta"
        );

        notificationService.createNotification(
                appointment.getPrescriber(),
                "Consulta Cancelada",
                "A consulta com " + appointment.getPatient().getName() + " do dia " + formattedDateTime + " foi cancelada.",
                "ALERT",
                "/agendamento-prescritor"
        );

        appointmentRepository.delete(appointment);
    }
}
