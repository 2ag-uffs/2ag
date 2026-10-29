package dev.uffs.doisag.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import dev.uffs.doisag.enums.UserRole;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

@Entity
// especializacao total do banco c/ cada tipo de conta na propria tabela ligada a users
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
// quem anulou um registro eh um usuario lazy, e numa lista eles vem em lote em vez de um por linha
@BatchSize(size = 50)
// o update grava so as colunas q mudaram: o perfil, a senha e a desativacao mexem na msm linha,
// e cada um gravando a linha inteira q tinha lido apagava o q o outro gravou no meio
// a anotacao n passa pra subclasse, entao Patient, Prescriber e Admin repetem ela
@DynamicUpdate
public abstract class Users implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    // cpf tbm precisa ser unico, senao o mesmo paciente entra duas vezes
    // e o historico dele fica partido em duas fichas
    @Column(unique = true)
    private String cpf;
    @Column(unique = true)
    private String email;
    private String password;
    private LocalDate birthDate;
    private String phone;
    @Embedded
    private Address address;

    // conta desativada n entra no sistema
    private boolean active = true;

    // a pessoa escolhe se quer receber lembretes e avisos por e-mail (RF18)
    private boolean emailNotificationsEnabled = true;

    // sessao emitida antes da ultima troca de senha deixa de valer
    private LocalDateTime passwordChangedAt;

    // sair da conta encerra as sessoes emitidas antes desse momento
    private LocalDateTime sessionsEndedAt;

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

    // o nome q as telas e os avisos usam. o paciente troca pelo nome social (RN15)
    @JsonIgnore
    public String getDisplayName() {
        return name;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    @JsonIgnore
    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    @JsonIgnore
    public LocalDateTime getSessionsEndedAt() {
        return sessionsEndedAt;
    }

    public void setSessionsEndedAt(LocalDateTime sessionsEndedAt) {
        this.sessionsEndedAt = sessionsEndedAt;
    }

    // o token guarda a emissao em milissegundos entao o fim das sessoes tbm fica em milissegundos
    public void endSessionsNow() {
        this.sessionsEndedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // o token guarda a emissao em milissegundos entao a troca tbm fica em milissegundos
    // senao a sessao emitida no mesmo segundo da troca continuava valendo
    public void markPasswordChangedNow() {
        this.passwordChangedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // cada tipo de conta diz o proprio papel
    @JsonIgnore
    public abstract UserRole getRole();

    // o spring security usa o papel com o prefixo ROLE
    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + getRole().name()));
    }

    // o JsonIgnore eh o q impede o hash da senha de sair na resposta da api
    @JsonIgnore
    @Override
    public String getPassword() {
        return this.password;
    }

    // o login eh pelo e-mail
    @JsonIgnore
    @Override
    public String getUsername() {
        return this.email;
    }

    // expirar e travar a conta n se usa aqui entao do UserDetails so o isEnabled vale
    @JsonIgnore
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @JsonIgnore
    @Override
    public boolean isEnabled() {
        return active;
    }

    // qnd o registro nasceu e qnd foi mexido pela ultima vez
    // o spring preenche sozinho e ninguem seta na mao (RF31)
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
