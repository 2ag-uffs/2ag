package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AvailabilityDTO;
import dev.uffs.doisag.dto.AvailabilityPeriodDTO;
import dev.uffs.doisag.dto.DayOffDTO;
import dev.uffs.doisag.dto.DayOffResponseDTO;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberAvailability;
import dev.uffs.doisag.model.PrescriberDayOff;
import dev.uffs.doisag.repository.AppointmentRepository;
import dev.uffs.doisag.repository.PrescriberAvailabilityRepository;
import dev.uffs.doisag.repository.PrescriberDayOffRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

// horarios de atendimento do prescritor (RF11)
// ele diz em q periodos da semana atende, quanto tempo dura a primeira consulta e o retorno
// e os dias em q n vai atender
@Service
public class AvailabilityService {

    public static final String INVALID_PERIOD_MESSAGE = "O horário de fim precisa ser depois do horário de início";
    public static final String OVERLAPPING_PERIODS_MESSAGE = "Dois períodos do mesmo dia não podem se sobrepor";
    public static final String PAST_DAY_OFF_MESSAGE = "Escolha um dia a partir de hoje";
    public static final String DAY_OFF_TAKEN_MESSAGE = "Esse dia já está marcado como sem atendimento";
    public static final String DAY_OFF_NOT_FOUND_MESSAGE = "Esse dia não está marcado como sem atendimento";

    private final PrescriberAvailabilityRepository availabilityRepository;
    private final PrescriberRepository prescriberRepository;
    private final PrescriberDayOffRepository dayOffRepository;
    private final AppointmentRepository appointmentRepository;

    public AvailabilityService(PrescriberAvailabilityRepository availabilityRepository,
                               PrescriberRepository prescriberRepository,
                               PrescriberDayOffRepository dayOffRepository,
                               AppointmentRepository appointmentRepository) {
        this.availabilityRepository = availabilityRepository;
        this.prescriberRepository = prescriberRepository;
        this.dayOffRepository = dayOffRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityDTO getAvailability(Long prescriberId) {
        Prescriber prescriber = findPrescriber(prescriberId);
        List<AvailabilityPeriodDTO> periods = availabilityRepository
                .findByPrescriberIdOrderByDayOfWeekAscStartTimeAsc(prescriberId)
                .stream()
                .map(AvailabilityPeriodDTO::new)
                .toList();
        return new AvailabilityDTO(prescriber.getAppointmentDurationMinutes(),
                prescriber.getFirstAppointmentDurationMinutes(), periods);
    }

    // a semana enviada substitui a anterior inteira
    // consulta ja marcada continua valendo mesmo fora dos periodos novos
    @Transactional
    public AvailabilityDTO replaceAvailability(Long prescriberId, AvailabilityDTO availabilityData) {
        checkPeriods(availabilityData.periods());

        Prescriber prescriber = findPrescriber(prescriberId);
        prescriber.setAppointmentDurationMinutes(availabilityData.appointmentDurationMinutes());
        // sem a duracao da primeira consulta ela fica igual a do retorno
        Integer firstDurationMinutes = availabilityData.firstAppointmentDurationMinutes();
        if (firstDurationMinutes == null) {
            firstDurationMinutes = availabilityData.appointmentDurationMinutes();
        }
        prescriber.setFirstAppointmentDurationMinutes(firstDurationMinutes);
        prescriberRepository.save(prescriber);

        availabilityRepository.deleteByPrescriberId(prescriberId);
        for (AvailabilityPeriodDTO periodData : availabilityData.periods()) {
            PrescriberAvailability period = new PrescriberAvailability();
            period.setPrescriber(prescriber);
            period.setDayOfWeek(periodData.dayOfWeek());
            period.setStartTime(periodData.startTime());
            period.setEndTime(periodData.endTime());
            availabilityRepository.save(period);
        }
        return getAvailability(prescriberId);
    }

    // as folgas de hoje em diante, cada uma com o q ainda esta marcado nela
    @Transactional(readOnly = true)
    public List<DayOffResponseDTO> getDaysOff(Long prescriberId) {
        List<PrescriberDayOff> daysOff = dayOffRepository
                .findByPrescriberIdAndOffDateGreaterThanEqualOrderByOffDateAsc(prescriberId, LocalDate.now());
        List<DayOffResponseDTO> response = new ArrayList<>();
        for (PrescriberDayOff dayOff : daysOff) {
            response.add(new DayOffResponseDTO(dayOff.getOffDate(),
                    countAppointmentsOn(prescriberId, dayOff.getOffDate())));
        }
        return response;
    }

    // o dia some dos horarios livres dos pacientes
    // pedido e consulta ja marcados no dia continuam valendo e a resposta conta quantos sao
    @Transactional
    public DayOffResponseDTO addDayOff(Long prescriberId, DayOffDTO dayOffData) {
        LocalDate date = dayOffData.date();
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(PAST_DAY_OFF_MESSAGE);
        }
        Prescriber prescriber = findPrescriber(prescriberId);
        // a mesma trava da agenda pq o pedido do paciente pro mesmo dia espera a folga gravar
        // entao a conta dos marcados n deixa ninguem de fora
        prescriberRepository.lockById(prescriberId);
        if (dayOffRepository.existsByPrescriberIdAndOffDate(prescriberId, date)) {
            throw new BusinessException(DAY_OFF_TAKEN_MESSAGE);
        }

        PrescriberDayOff dayOff = new PrescriberDayOff();
        dayOff.setPrescriber(prescriber);
        dayOff.setOffDate(date);
        dayOffRepository.save(dayOff);
        return new DayOffResponseDTO(date, countAppointmentsOn(prescriberId, date));
    }

    @Transactional
    public void removeDayOff(Long prescriberId, LocalDate date) {
        PrescriberDayOff dayOff = dayOffRepository.findByPrescriberIdAndOffDate(prescriberId, date)
                .orElseThrow(() -> NotFoundException.forUser(DAY_OFF_NOT_FOUND_MESSAGE));
        dayOffRepository.delete(dayOff);
    }

    // so o q ainda vai acontecer no dia: pedido esperando resposta e consulta marcada
    // consulta q ja passou ou ja foi atendida n precisa de aviso
    private long countAppointmentsOn(Long prescriberId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(start)) {
            start = now;
        }
        return appointmentRepository.countByPrescriberIdAndDateTimeBetweenAndStatusInAndAnnulmentAnnulledAtIsNull(
                prescriberId, start, date.atTime(LocalTime.MAX),
                List.of(AppointmentStatus.SOLICITADA, AppointmentStatus.AGENDADA));
    }

    private void checkPeriods(List<AvailabilityPeriodDTO> periods) {
        for (AvailabilityPeriodDTO period : periods) {
            if (!period.startTime().isBefore(period.endTime())) {
                throw new BusinessException(INVALID_PERIOD_MESSAGE);
            }
        }

        // compara cada periodo com os outros do mesmo dia
        for (int firstIndex = 0; firstIndex < periods.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < periods.size(); secondIndex++) {
                AvailabilityPeriodDTO firstPeriod = periods.get(firstIndex);
                AvailabilityPeriodDTO secondPeriod = periods.get(secondIndex);
                boolean sameDay = firstPeriod.dayOfWeek().equals(secondPeriod.dayOfWeek());
                boolean overlaps = firstPeriod.startTime().isBefore(secondPeriod.endTime())
                        && secondPeriod.startTime().isBefore(firstPeriod.endTime());
                if (sameDay && overlaps) {
                    throw new BusinessException(OVERLAPPING_PERIODS_MESSAGE);
                }
            }
        }
    }

    private Prescriber findPrescriber(Long prescriberId) {
        return prescriberRepository.findById(prescriberId)
                .orElseThrow(() -> new NotFoundException("Prescritor não encontrado com o id: " + prescriberId));
    }
}
