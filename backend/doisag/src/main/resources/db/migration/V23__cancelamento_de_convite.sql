-- o prescritor cancela um convite q foi pro numero errado (issue 83)
-- coluna propria, senao usedAt sem paciente ficaria ambiguo na trilha

alter table patient_invite add column cancelled_at timestamp(6);
