package dev.uffs.doisag.service;

import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.dto.AppointmentCreateDTO;
import dev.uffs.doisag.dto.AppointmentMarkerDTO;
import dev.uffs.doisag.dto.BusySlotDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.enums.TimePeriod;
import dev.uffs.doisag.model.Appointment;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class AppointmentService {

    // a agenda trabalha em blocos de uma hora quando ninguem diz outra coisa
    public static final int DURACAO_PADRAO = 60;

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

    // create. quando quem marca eh o prescritor, ele vem do token. quando
    // eh o paciente (RF10), o prescritor eh o que ele ja tem vinculado,
    // entao n da pra marcar consulta na agenda de outro profissional
    public Appointment create(AppointmentCreateDTO dados, Prescriber prescritorLogado) {
        Patient patient = patientRepository.findById(dados.patientId())
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + dados.patientId()));

        Prescriber prescriber = prescritorLogado != null ? prescritorLogado : patient.getPrescriber();
        if (prescriber == null) {
            throw new IllegalArgumentException("Você ainda não tem um prescritor vinculado");
        }

        recusaDataNoPassado(dados.dateTime());
        recusaHorarioOcupado(prescriber, dados.dateTime(), dados.durationMinutes(), null);

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
        appointment.setDurationMinutes(dados.durationMinutes() == null ? DURACAO_PADRAO : dados.durationMinutes());

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
                orElseThrow(() -> new NotFoundException("Paciente não encontrado com o id: " + id));

    }

    public Appointment update(Long id, AppointmentCreateDTO dados) {
        Appointment appointment = getById(id);
        // a propria consulta n conta como conflito com ela mesma
        recusaHorarioOcupado(appointment.getPrescriber(), dados.dateTime(), dados.durationMinutes(), id);
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

    // as consultas de um paciente dentro da janela do grafico. cancelada
    // fica de fora: consulta que n aconteceu n explica mudanca nenhuma
    public List<AppointmentMarkerDTO> getMarcadoresDoPaciente(Long patientId, TimePeriod periodo) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusDays(periodo.getDays());

        return appointmentRepository
                .findByPatientIdAndDateTimeBetweenOrderByDateTimeAsc(
                        patientId, inicio.atStartOfDay(), hoje.atTime(LocalTime.MAX))
                .stream()
                .filter(consulta -> consulta.getStatus() != AppointmentStatus.CANCELADA)
                .map(AppointmentMarkerDTO::new)
                .toList();
    }

    // os horarios ja ocupados de um prescritor num dia, sem dizer de
    // quem sao. eh o que o paciente ve pra escolher horario (RF10)
    public List<BusySlotDTO> getHorariosOcupados(Long prescriberId, LocalDate dia) {
        return appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriberId, dia.atStartOfDay(), dia.atTime(LocalTime.MAX))
                .stream()
                .filter(consulta -> consulta.getStatus() != AppointmentStatus.CANCELADA)
                .map(consulta -> {
                    int minutos = consulta.getDurationMinutes() == null ? DURACAO_PADRAO : consulta.getDurationMinutes();
                    LocalTime inicio = consulta.getDateTime().toLocalTime();
                    return new BusySlotDTO(inicio, inicio.plusMinutes(minutos));
                })
                .sorted((a, b) -> a.inicio().compareTo(b.inicio()))
                .toList();
    }

    // consultas de um prescritor especifico
    public List<Appointment> getByPrescriberId(Long prescriberId) {
        return appointmentRepository.findByPrescriberId(prescriberId);
    }

    // cancelar n apaga: a consulta fica no historico com status
    // CANCELADA. apagar perderia o registro de que ela existiu, e o
    // paciente e o prescritor precisam poder olhar pra tras e ver isso
    public Appointment cancel(Long id) {
        Appointment appointment = getById(id);

        if (appointment.getStatus() == AppointmentStatus.CANCELADA) {
            throw new IllegalArgumentException("Esta consulta já está cancelada");
        }
        if (appointment.getStatus() == AppointmentStatus.CONCLUIDA) {
            throw new IllegalArgumentException("Consulta já concluída não pode ser cancelada");
        }

        appointment.setStatus(AppointmentStatus.CANCELADA);
        Appointment cancelada = appointmentRepository.save(appointment);
        avisaDoCancelamento(cancelada);
        return cancelada;
    }

    // RN08: dois agendamentos do mesmo prescritor n podem ocupar o
    // mesmo horario. a conta de sobreposicao fica aqui em vez de virar
    // query, porque a duracao eh coluna separada do inicio
    private void recusaHorarioOcupado(Prescriber prescriber, LocalDateTime inicio, Integer duracao, Long idQueEstaSendoAlterada) {
        int minutos = duracao == null ? DURACAO_PADRAO : duracao;
        LocalDateTime fim = inicio.plusMinutes(minutos);
        LocalDate dia = inicio.toLocalDate();

        boolean ocupado = appointmentRepository
                .findByPrescriberIdAndDateTimeBetween(prescriber.getId(), dia.atStartOfDay(), dia.atTime(LocalTime.MAX))
                .stream()
                .filter(outra -> !outra.getId().equals(idQueEstaSendoAlterada))
                // consulta cancelada devolveu o horario
                .filter(outra -> outra.getStatus() != AppointmentStatus.CANCELADA)
                .anyMatch(outra -> {
                    int duracaoOutra = outra.getDurationMinutes() == null ? DURACAO_PADRAO : outra.getDurationMinutes();
                    LocalDateTime inicioOutra = outra.getDateTime();
                    LocalDateTime fimOutra = inicioOutra.plusMinutes(duracaoOutra);
                    return inicio.isBefore(fimOutra) && inicioOutra.isBefore(fim);
                });

        if (ocupado) {
            throw new IllegalArgumentException("Já existe consulta marcada nesse horário");
        }
    }

    // RF10: n da pra marcar consulta pra tras
    private void recusaDataNoPassado(LocalDateTime quando) {
        if (quando.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Não é possível agendar em uma data que já passou");
        }
    }

    private void avisaDoCancelamento(Appointment appointment) {
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
    }
}
