package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.ResourceNotFoundException;
import dev.uffs.doisag.model.Appointment;
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
    private NotificationService notificationService;

    // removemos o NotificationService do construtor
    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    // criamos um metodo setter para o spring injetar a dependencia depois
    @Autowired
    public void setNotificationService(@Lazy NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // create
    public Appointment create(Appointment appointment) {
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

    public Appointment update(Long id, Appointment appointmentDetails) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Consulta não encontrada para o id:: " + id));

        LocalDateTime oldDateTime = appointment.getDateTime();

        appointment.setDateTime(appointmentDetails.getDateTime());
        appointment.setModality(appointmentDetails.getModality());
        appointment.setStatus(appointmentDetails.getStatus());
        appointment.setDiagnosis(appointmentDetails.getDiagnosis());
        appointment.setClinicalObservation(appointmentDetails.getClinicalObservation());
        appointment.setTherapeuticPlan(appointmentDetails.getTherapeuticPlan());
        appointment.setEvolution(appointmentDetails.getEvolution());

        Appointment updatedAppointment = appointmentRepository.save(appointment);

        if (!oldDateTime.equals(updatedAppointment.getDateTime())) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
            String newFormattedDateTime = updatedAppointment.getDateTime().format(formatter);

            notificationService.createNotification(
                    updatedAppointment.getPatient(),
                    "Consulta Reagendada",
                    "Sua consulta com " + updatedAppointment.getPrescriber().getName() + " foi reagendada para " + newFormattedDateTime + ".",
                    "APPOINTMENT",
                    "/agendamento-consulta"
            );

            notificationService.createNotification(
                    updatedAppointment.getPrescriber(),
                    "Consulta Reagendada",
                    "A consulta com o paciente " + updatedAppointment.getPatient().getName() + " foi reagendada para " + newFormattedDateTime + ".",
                    "APPOINTMENT",
                    "/agendamento-prescritor"
            );
        }
        return updatedAppointment;
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
