package dev.uffs.doisag.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // pra quem eh a notificacao
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // titulo e mensagem
    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 512)
    private String message;

    // pra saber se ja foi lida
    @Column(nullable = false)
    private boolean isRead = false;

    // quando foi criada
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // tipo da notificacao, pra gente poder filtrar e por icones diferentes no front
    @Column(nullable = false)
    private String type; // ex: "FORM", "APPOINTMENT", "ALERT"

    // um link pra onde o usuario deve ir quando clicar na notificacao
    private String link; // ex: "/diario-sono"

    // construtor, getters e setters
    public Notification() {
        this.createdAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }
}
