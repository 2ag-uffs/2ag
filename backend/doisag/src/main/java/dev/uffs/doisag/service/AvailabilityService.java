package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.AvailabilityDTO;
import dev.uffs.doisag.dto.AvailabilityPeriodDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.model.PrescriberAvailability;
import dev.uffs.doisag.repository.PrescriberAvailabilityRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// horarios de atendimento do prescritor (RF11)
// ele diz em q periodos da semana atende e quanto tempo dura cada consulta
@Service
public class AvailabilityService {

    public static final String INVALID_PERIOD_MESSAGE = "O horário de fim precisa ser depois do horário de início";
    public static final String OVERLAPPING_PERIODS_MESSAGE = "Dois períodos do mesmo dia não podem se sobrepor";

    private final PrescriberAvailabilityRepository availabilityRepository;
    private final PrescriberRepository prescriberRepository;

    public AvailabilityService(PrescriberAvailabilityRepository availabilityRepository,
                               PrescriberRepository prescriberRepository) {
        this.availabilityRepository = availabilityRepository;
        this.prescriberRepository = prescriberRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityDTO getAvailability(Long prescriberId) {
        Prescriber prescriber = findPrescriber(prescriberId);
        List<AvailabilityPeriodDTO> periods = availabilityRepository
                .findByPrescriberIdOrderByDayOfWeekAscStartTimeAsc(prescriberId)
                .stream()
                .map(AvailabilityPeriodDTO::new)
                .toList();
        return new AvailabilityDTO(prescriber.getAppointmentDurationMinutes(), periods);
    }

    // a semana enviada substitui a anterior inteira
    // consulta ja marcada continua valendo mesmo fora dos periodos novos
    @Transactional
    public AvailabilityDTO replaceAvailability(Long prescriberId, AvailabilityDTO availabilityData) {
        checkPeriods(availabilityData.periods());

        Prescriber prescriber = findPrescriber(prescriberId);
        prescriber.setAppointmentDurationMinutes(availabilityData.appointmentDurationMinutes());
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
