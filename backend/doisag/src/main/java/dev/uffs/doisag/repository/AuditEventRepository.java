package dev.uffs.doisag.repository;

import dev.uffs.doisag.enums.AuditOperation;
import dev.uffs.doisag.enums.UserRole;
import dev.uffs.doisag.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// a trilha so salva e consulta
// de proposito n estende o JpaRepository pra n existir metodo de alterar nem de apagar evento
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent auditEvent);

    // se a mesma pessoa ja abriu o prontuario do paciente nos ultimos minutos
    boolean existsByActorIdAndPatientIdAndOperationAndOccurredAtAfter(Long actorId, Long patientId,
                                                                      AuditOperation operation,
                                                                      LocalDateTime moment);

    @Query(value = "select e from AuditEvent e where e.patientId = :patientId"
            + " and e.occurredAt >= :start and e.occurredAt < :end order by e.occurredAt desc, e.id desc",
            countQuery = "select count(e) from AuditEvent e where e.patientId = :patientId"
                    + " and e.occurredAt >= :start and e.occurredAt < :end")
    Page<AuditEvent> findPatientEvents(@Param("patientId") Long patientId, @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end, Pageable pageable);

    // o q prescritores e o sistema fizeram sem as acoes dos proprios pacientes
    @Query(value = "select e from AuditEvent e where e.occurredAt >= :start and e.occurredAt < :end"
            + " and (e.actorRole is null or e.actorRole <> :hiddenRole) order by e.occurredAt desc, e.id desc",
            countQuery = "select count(e) from AuditEvent e where e.occurredAt >= :start and e.occurredAt < :end"
                    + " and (e.actorRole is null or e.actorRole <> :hiddenRole)")
    Page<AuditEvent> findStaffEvents(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                                     @Param("hiddenRole") UserRole hiddenRole, Pageable pageable);
}
