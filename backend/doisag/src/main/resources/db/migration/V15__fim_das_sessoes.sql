-- sair da conta encerra as sessoes abertas daquela pessoa em qualquer aparelho
-- token emitido antes desse momento deixa de valer (issue 47)

alter table users add column sessions_ended_at timestamp(6);
