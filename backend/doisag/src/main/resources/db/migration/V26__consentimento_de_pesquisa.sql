-- consentimento de pesquisa separado do termo do cadastro (issue 126, RF37)
-- o tipo separa os dois aceites e a retirada fica marcada sem apagar a linha
alter table consent_acceptance add column term_type varchar(20) not null default 'REGISTRATION';
alter table consent_acceptance add column revoked_at timestamp(6);
