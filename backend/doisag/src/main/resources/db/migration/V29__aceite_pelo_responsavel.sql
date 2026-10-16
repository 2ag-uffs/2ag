-- paciente com menos de 18 anos aceita o termo com o responsavel legal junto (LGPD art. 14 e codigo civil)
-- nome e parentesco ficam congelados no aceite, nulo qnd foi o proprio paciente
alter table consent_acceptance add column guardian_name varchar(255);
alter table consent_acceptance add column guardian_relationship varchar(60);
