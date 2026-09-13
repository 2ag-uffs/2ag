-- perfil da propria conta (RF18)
-- a pessoa escolhe se quer receber avisos por e-mail
-- e a hora da ultima troca de senha derruba as sessoes abertas antes dela

alter table users add column email_notifications_enabled boolean not null default true;
alter table users add column password_changed_at timestamp(6);
