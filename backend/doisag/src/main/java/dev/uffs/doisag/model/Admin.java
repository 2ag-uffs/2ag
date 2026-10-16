package dev.uffs.doisag.model;

import dev.uffs.doisag.enums.UserRole;
import jakarta.persistence.Entity;
import org.hibernate.annotations.DynamicUpdate;

// conta administrativa
// cria e desativa contas de prescritor e nunca enxerga dado clinico
@Entity
// msm motivo do Users
@DynamicUpdate
public class Admin extends Users {

    @Override
    public UserRole getRole() {
        return UserRole.ADMIN;
    }
}
