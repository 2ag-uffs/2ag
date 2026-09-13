package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

// endereco do jeito q vai e volta pela api
public record AddressDTO(
        @NotBlank(message = "Informe a rua")
        String street,

        @NotBlank(message = "Informe o número ou s/n")
        String number,

        @NotBlank(message = "Informe a cidade")
        String city,

        @NotBlank(message = "Informe o estado")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Use a sigla do estado, como SC")
        String state,

        String country
) {
    // a clinica atende no brasil entao pais vazio vira brasil
    private static final String DEFAULT_COUNTRY = "Brasil";

    public AddressDTO(Address address) {
        this(
                address.getStreet(),
                address.getNumber(),
                address.getCity(),
                address.getState(),
                address.getCountry()
        );
    }

    // helper pra virar entidade de novo quando chega do front
    public Address toAddress() {
        String countryToSave = country;
        if (countryToSave == null || countryToSave.isBlank()) {
            countryToSave = DEFAULT_COUNTRY;
        }
        return new Address(street.trim(), number.trim(), city.trim(), state.trim().toUpperCase(Locale.ROOT),
                countryToSave.trim());
    }
}
