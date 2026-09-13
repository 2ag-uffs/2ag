-- o vinculo do paciente passa a ser feito pelo link de convite (RN06)
-- o codigo de tres letras e dois numeros era facil de adivinhar e deixa de existir
alter table prescriber drop column professional_code;
