-- o bloqueio do login saiu da conta e passou a contar por e-mail e endereco em memoria (issue 48)
-- assim errar a senha de outra pessoa n trava a conta dela

alter table users drop column failed_login_attempts;
alter table users drop column locked_until;
