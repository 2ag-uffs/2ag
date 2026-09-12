package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPrescriberIdAndDateTimeBetween(Long prescriberId, LocalDateTime startOfDay, LocalDateTime endOfDay);
/**
 * aqui a gente caça no banco as consultas de um médico específico, num dia específico
 * o spring data jpa é divo e cria a query sozinho só pelo nome do método
 */
}
