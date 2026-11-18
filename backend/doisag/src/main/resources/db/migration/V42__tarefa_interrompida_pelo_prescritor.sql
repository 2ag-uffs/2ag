-- a tarefa guarda o dia em q o prescritor interrompeu ela, arquivando o paciente ou tirando a escala do
-- acompanhamento. antes isso era lido do paciente e do acompanhamento de agora, entao reativar o paciente, ou
-- encerrar o acompanhamento novo, fazia a semana interrompida do diario cobrar os dias em branco de novo
-- as tarefas de antes ficam sem o dia, pq n da pra saber quais foram interrompidas

alter table scale_task add column stopped_on date;
