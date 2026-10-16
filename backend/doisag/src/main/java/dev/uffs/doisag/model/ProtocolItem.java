package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.Periodicity;
import dev.uffs.doisag.enums.ScaleType;
import jakarta.persistence.*;

import java.time.LocalDate;

// uma escala dentro do protocolo, com a periodicidade dela.
// o mesmo paciente pode responder o acompanhamento toda semana e o
// pittsburgh uma vez por mes, por exemplo
@Entity
public class ProtocolItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScaleType scaleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Periodicity periodicity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private TreatmentProtocol protocol;

    // o fim do prazo da ultima rodada q esse acompanhamento mandou, nulo antes da primeira
    // fica no item e n na tarefa pq a rodada do acompanhamento anterior n pode segurar o novo
    private LocalDate lastRoundEnd;

    public Long getId() {
        return id;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public Periodicity getPeriodicity() {
        return periodicity;
    }

    public void setPeriodicity(Periodicity periodicity) {
        this.periodicity = periodicity;
    }

    public TreatmentProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(TreatmentProtocol protocol) {
        this.protocol = protocol;
    }

    public LocalDate getLastRoundEnd() {
        return lastRoundEnd;
    }

    public void setLastRoundEnd(LocalDate lastRoundEnd) {
        this.lastRoundEnd = lastRoundEnd;
    }
}
