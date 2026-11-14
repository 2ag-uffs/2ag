package dev.uffs.doisag.model;

import dev.uffs.doisag.infra.ScaleAnswersConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// a versao q uma correcao do paciente substituiu (decisao de 14/11/2026, revisao das regras R4)
//
// a correcao gravava por cima e o valor q embasou um ajuste de dose sumia. agora cada correcao
// guarda o q estava antes, e so a prescritora ve. a linha so eh inserida, nunca muda
@Entity
public class ScaleResponseCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_response_id", nullable = false)
    private ScaleResponse response;

    // qnd a versao deixou de valer, q eh o dia da correcao
    @Column(nullable = false)
    private LocalDateTime correctedAt;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Convert(converter = ScaleAnswersConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private Map<String, Object> answers = new LinkedHashMap<>();

    private Integer score;

    private String scoreBand;

    protected ScaleResponseCorrection() {
    }

    // copia a resposta como ela esta agora, antes de a correcao mexer nela, com a hora da correcao
    public ScaleResponseCorrection(ScaleResponse response) {
        this.response = response;
        this.correctedAt = LocalDateTime.now();
        this.periodStart = response.getPeriodStart();
        this.periodEnd = response.getPeriodEnd();
        this.answers = new LinkedHashMap<>(response.getAnswers());
        this.score = response.getScore();
        this.scoreBand = response.getScoreBand();
    }

    // mandar de novo o mesmo conteudo n eh correcao e n vira versao
    public boolean differsFrom(ScaleResponse corrected) {
        return !periodStart.equals(corrected.getPeriodStart()) || !periodEnd.equals(corrected.getPeriodEnd())
                || !answers.equals(corrected.getAnswers());
    }

    public Long getId() {
        return id;
    }

    public ScaleResponse getResponse() {
        return response;
    }

    public LocalDateTime getCorrectedAt() {
        return correctedAt;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Map<String, Object> getAnswers() {
        return answers;
    }

    public Integer getScore() {
        return score;
    }

    public String getScoreBand() {
        return scoreBand;
    }
}
