package dev.uffs.doisag.repository;

import dev.uffs.doisag.model.MentalStateExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MentalStateExamRepository extends JpaRepository<MentalStateExam, Long> {
}
