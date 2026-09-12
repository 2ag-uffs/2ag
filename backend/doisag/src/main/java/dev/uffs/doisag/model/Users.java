package dev.uffs.doisag.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
@Entity
@Inheritance(strategy = InheritanceType.JOINED) // respeitar minha definição de especialização total em BD
public abstract class Users implements UserDetails { // implementa a interface do spring security
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String cpf;
    @Column(unique = true) // garantir email único no banco
    private String email;
    private String password;
    private LocalDate birthDate;
    private String phone;
    @Embedded
    private Address address;


    public Users() {
    }

    public Users(Address address, LocalDate birthDate, String cpf, String email, Long id, String name, String password, String phone) {
        this.address = address;
        this.birthDate = birthDate;
        this.cpf = cpf;
        this.email = email;
        this.id = id;
        this.name = name;
        this.password = password;
        this.phone = phone;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    // a partir daqui vou trabalhar os metodos de permissão do usuário a partir do userdetails implemnetado

    // prescritores papel de admin e pacientes papel de user
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this instanceof Prescriber) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // retorna a senha criptografada do banco
    @Override
    public String getPassword() {
        return this.password;
    }

    // username vai ser o e-mail
    @Override
    public String getUsername() {
        return this.email;
    }

    // vamos dizer q as contas nunca expiram
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
