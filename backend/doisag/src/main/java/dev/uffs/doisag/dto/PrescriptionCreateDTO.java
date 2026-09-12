package dev.uffs.doisag.dto; // você pode criar um pacote 'dto'

// este record carrega apenas os dados que o prescritor precisa enviar
public record PrescriptionCreateDTO(
        String productDescription,
        String posology,
        String brand,
        String concentration,
        String spectrum,
        String observation
) {}