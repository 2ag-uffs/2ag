package dev.uffs.doisag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

// o ultimo dia em q o job diario terminou todas as etapas
// a tabela tem uma linha so, sempre com o mesmo id
@Entity
@Table(name = "daily_cycle")
public class DailyCycle {

    public static final Long ID = 1L;

    @Id
    private Long id = ID;

    @Column(nullable = false)
    private LocalDate lastCompletedDate;

    public Long getId() {
        return id;
    }

    public LocalDate getLastCompletedDate() {
        return lastCompletedDate;
    }

    public void setLastCompletedDate(LocalDate lastCompletedDate) {
        this.lastCompletedDate = lastCompletedDate;
    }
}
