package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.AuditOperation;
import dev.uffs.doisag.enums.AuditRecordType;
import dev.uffs.doisag.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

// uma linha da trilha de auditoria (RF31)
// n tem setter e o hibernate ignora update entao depois de gravada a linha n muda mais
@Entity
@Table(name = "audit_event")
@Immutable
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    // nulo quando quem fez foi o proprio sistema
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Users actor;

    // o perfil fica guardado pra filtrar a trilha sem precisar juntar as tabelas de usuario
    @Enumerated(EnumType.STRING)
    private UserRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditRecordType recordType;

    private Long recordId;

    @Column(nullable = false)
    private Long patientId;

    // o jpa precisa de um construtor vazio
    protected AuditEvent() {
    }

    public AuditEvent(Users actor, AuditOperation operation, AuditRecordType recordType, Long recordId,
                      Long patientId) {
        this.occurredAt = LocalDateTime.now();
        this.actor = actor;
        this.actorRole = actor == null ? null : actor.getRole();
        this.operation = operation;
        this.recordType = recordType;
        this.recordId = recordId;
        this.patientId = patientId;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public Users getActor() {
        return actor;
    }

    public UserRole getActorRole() {
        return actorRole;
    }

    public AuditOperation getOperation() {
        return operation;
    }

    public AuditRecordType getRecordType() {
        return recordType;
    }

    public Long getRecordId() {
        return recordId;
    }

    public Long getPatientId() {
        return patientId;
    }
}
