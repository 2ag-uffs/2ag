package dev.uffs.doisag.dto;

import dev.uffs.doisag.model.Address;

// endereco do jeito q vai e volta pela api
public record AddressDTO(
        String street,
        String number,
        String city,
        String state,
        String country
) {
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
        return new Address(street, number, city, state, country);
    }
}
