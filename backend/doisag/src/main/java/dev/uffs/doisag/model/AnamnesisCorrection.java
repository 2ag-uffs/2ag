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

// a versao da anamnese q uma correcao do paciente substituiu (decisao de 14/11/2026, revisao das regras R4)
//
// os remedios e as reacoes podiam mudar meses depois sem ninguem ver. as respostas vao como o par
// campo e texto, com o nome do campo q a api usa, e a linha so eh inserida, nunca muda
@Entity
public class AnamnesisCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anamnesis_id", nullable = false)
    private Anamnesis anamnesis;

    @Column(nullable = false)
    private LocalDateTime correctedAt;

    @Column(nullable = false)
    private LocalDate assessmentDate;

    @Convert(converter = ScaleAnswersConverter.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private Map<String, Object> answers = new LinkedHashMap<>();

    protected AnamnesisCorrection() {
    }

    // copia a ficha como ela esta agora, antes de a correcao mexer nela, com a hora da correcao
    public AnamnesisCorrection(Anamnesis anamnesis) {
        this.anamnesis = anamnesis;
        this.correctedAt = LocalDateTime.now();
        this.assessmentDate = anamnesis.getAssessmentDate();
        this.answers = anamnesis.answersByField();
    }

    // mandar de novo o mesmo conteudo n eh correcao e n vira versao
    public boolean differsFrom(Anamnesis corrected) {
        return !assessmentDate.equals(corrected.getAssessmentDate()) || !answers.equals(corrected.answersByField());
    }

    public Long getId() {
        return id;
    }

    public Anamnesis getAnamnesis() {
        return anamnesis;
    }

    public LocalDateTime getCorrectedAt() {
        return correctedAt;
    }

    public LocalDate getAssessmentDate() {
        return assessmentDate;
    }

    public Map<String, Object> getAnswers() {
        return answers;
    }
}
