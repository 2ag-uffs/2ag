package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.AppointmentModality;
import dev.uffs.doisag.enums.AppointmentStatus;
import dev.uffs.doisag.model.Appointment;

import java.time.LocalDate;

// uma consulta vista de dentro do grafico de evolucao.
//
// n eh a consulta inteira: o grafico so precisa saber em que dia ela
// foi e o que saiu dela, pra quem le o grafico conseguir ligar uma
// mudanca de sintoma a uma mudanca de conduta
public record AppointmentMarkerDTO(
        Long id,
        LocalDate data,
        AppointmentModality modality,
        AppointmentStatus status,
        String diagnosis,
        boolean geraPrescricao
) {
    public AppointmentMarkerDTO(Appointment appointment) {
        this(
                appointment.getId(),
                appointment.getDateTime().toLocalDate(),
                appointment.getModality(),
                appointment.getStatus(),
                appointment.getDiagnosis(),
                hasValidPrescription(appointment)
        );
    }

    // receita anulada n conta, senao a marca ligava a curva a uma dose q n valeu
    private static boolean hasValidPrescription(Appointment appointment) {
        return appointment.getPrescriptions() != null
                && appointment.getPrescriptions().stream().anyMatch(prescription -> !prescription.isAnnulled());
    }
}
