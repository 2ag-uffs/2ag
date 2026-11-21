# guarda de dados

**Rascunho para a clínica validar.** Esta página diz por quanto tempo cada tipo de dado do 2ag fica guardado. Foi escrita pela equipe do 2ag a partir do que o sistema grava, e ainda não foi lida nem aprovada pela clínica, que é quem responde pelos dados (LGPD) e quem define os prazos.

Hoje só um prazo é regra: os **20 anos** do prontuário e do termo de consentimento. Todos os outros estão como *a definir pela clínica*, de propósito: escrever um prazo que ninguém escolheu e que o sistema não cumpre seria registrar uma falha como se fosse política.

**Hoje o sistema não apaga nada sozinho por prazo de guarda.** As duas únicas limpezas automáticas que existem não olham esses prazos: o script de backup tira da pasta do servidor os arquivos com mais de 30 dias, e o docker descarta o log mais antigo quando o container passa de 50 MB.

---

## por quanto tempo cada dado fica

| dado | onde fica | prazo de guarda | o que o sistema faz hoje |
| :--- | :--- | :--- | :--- |
| prontuário: anamnese, consultas, prescrições, respostas das escalas e dos diários, Mini-Exame, acompanhamento automático, com as correções e as anulações | banco | **20 anos a partir do último registro** (Lei nº 13.787/2018) | não apaga. anular marca o registro e guarda o motivo, e nenhuma rota apaga dado clínico |
| aceite do termo de consentimento do cadastro: a versão, a data, o hash do texto aceito e, de menor de 18 anos, o nome e o parentesco do responsável legal | banco | **20 anos**, junto com o prontuário | não apaga |
| consentimento de pesquisa: cada aceite e cada retirada | banco | a definir pela clínica | não apaga |
| cadastro do paciente: nome, nome social, CPF, data de nascimento, contato, endereço e o familiar ou cuidador | banco | a definir pela clínica | não apaga. a conta desativada e o paciente arquivado continuam guardados |
| contas de prescritor e da administração | banco | a definir pela clínica | não apaga. desativar tira o acesso e mantém a conta |
| trilha de auditoria: quem criou, alterou, anulou, abriu ou exportou, e quando | banco, em tabela que só recebe linha nova | a definir pela clínica | não apaga |
| avisos dentro do sistema | banco | a definir pela clínica | não apaga. cada pessoa pode apagar os próprios avisos |
| convites de paciente: o hash do código, quem gerou e as datas | banco | a definir pela clínica | o convite vale 7 dias e uma vez só. a linha usada, vencida ou cancelada continua guardada |
| links de senha nova e de troca de e-mail: o hash do código, as datas e, na troca, o endereço novo | banco | a definir pela clínica | o link vale 30 minutos (48 horas no primeiro acesso do prescritor) e uma vez só. a linha continua guardada |
| logs do servidor: endereço de rede, tela aberta e navegador no `web`; número da conta, rota chamada e erros na `api`, mais o e-mail da administração na primeira subida e, quando o `ADMIN_EMAIL` muda, a primeira letra e o domínio do e-mail da conta administrativa antiga que a subida desativa; e os erros do banco no `banco`. nem a `api` nem o `banco` escrevem o valor que bateu numa chave única, como o CPF ou o e-mail repetido de um cadastro: o banco sobe sem o detalhe do erro (`log_error_verbosity=terse`), o endereço do banco da `api` pede o erro sem esse detalhe (`logServerErrorDetail=false`) e a `api` não escreve a mensagem do banco no log. sem `MAIL_HOST` (servidor sem envio de e-mail), a `api` também escreve o e-mail de quem recebe e o assunto de cada mensagem que não saiu: lembretes, senha nova e troca de e-mail. o texto da mensagem e o link só entram no log com `EMAIL_LOG_TEXT=true`, que é só de desenvolvimento | arquivos do docker, no servidor | a definir pela clínica | sem prazo por tempo. cada container guarda até 50 MB e o mais antigo sai quando enche |
| backups do banco, cifrados | pasta do servidor e cópia fora dele | a definir pela clínica | a pasta do servidor guarda 30 dias. a cópia de fora não é apagada pelo sistema, e é ela que precisa alcançar os 20 anos do prontuário ([`backup-e-restauracao.md`](./backup-e-restauracao.md)) |
| arquivos exportados (CSV e impressão em PDF), inclusive a exportação sem identificação para pesquisa | no computador de quem baixou, fora do sistema | a definir pela clínica | o sistema não guarda o arquivo, só a linha na trilha dizendo quem baixou e quando. apagar a cópia é com quem baixou |
| e-mails enviados: links e lembretes | na conta de e-mail da clínica e na caixa de quem recebeu | a definir pela clínica | o sistema não guarda cópia |
| registro de incidentes de segurança | com a clínica, fora do sistema | a definir pela clínica | não é do sistema. veja [`incidente-de-seguranca.md`](./incidente-de-seguranca.md) |

Não ficam guardados: a sessão, que é um cookie no navegador e vale no máximo 12 horas, e a contagem de senhas erradas, que fica só na memória da api, com o hash do e-mail, e some quando ela reinicia.

---

## o que os 20 anos significam na prática

- O paciente que deixa de usar o sistema, que tem a conta desativada ou que é arquivado continua com o prontuário guardado. É o que o termo de consentimento já diz a ele.
- Como o termo diz, retirar o consentimento interrompe registros novos, mas não apaga o prontuário dentro do prazo legal. O pedido de apagar é respondido pela clínica, com o encarregado de dados, e o sistema não tem botão de apagar prontuário, de propósito.
- O prazo conta do último registro, então o backup de fora do servidor precisa continuar legível por todo esse tempo: os arquivos, a chave privada e alguém que saiba restaurar.

---

## o que falta a clínica decidir

- [ ] O prazo de cada linha marcada como *a definir pela clínica*.
- [ ] Quem guarda os arquivos exportados para pesquisa, onde, e quando eles são apagados.
- [ ] Por quanto tempo a cópia externa do backup guarda cada arquivo.
- [ ] Revisar esta página com o encarregado de dados e tirar a marca de rascunho.

Depois que os prazos forem escolhidos, a limpeza automática do que venceu (logs, convites e links antigos) entra no sistema como uma rotina própria. Antes disso ela não existe, e esta página não deve ser lida como se existisse.
