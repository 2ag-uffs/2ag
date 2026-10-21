# 2ag

sistema livre para acompanhamento terapêutico longitudinal de pacientes em tratamento com óleo de Cannabis sativa. O 2ag digitaliza o ciclo de cuidado de uma clínica que prescreve fitocanabinoides: triagem por anamnese, registro de consulta e prescrição, aplicação das escalas clínicas padronizadas com cálculo dos escores, envio automatizado dos formulários de acompanhamento ao longo dos 90 dias de tratamento e visualização gráfica da evolução dos sintomas.

## projeto

sistema desenvolvido como ação de extensão no componente GCH1993 — Projeto de Integração de Extensão da Universidade Federal da Fronteira Sul (UFFS), campus Chapecó.

o que o sistema precisa fazer está em [`docs/requisitos-v2.md`](./docs/requisitos-v2.md).
para instalar o sistema no ambiente do Windows, o passo a passo está em [`docs/WindowsTutorial.md`](./docs/WindowsTutorial.md).
para instalar o sistema no ambiente do Linux, o passo a passo está em [`docs/LinuxTutorial.md`](./docs/LinuxTutorial.md).


---

## umas coisas boas de saber

- o paciente cria a própria conta pelo link de convite que o prescritor gera no sistema. conta de prescritor é criada pelo administrador, nunca por autocadastro.
- cada pessoa cria a própria senha. o administrador não escolhe a senha do prescritor: a conta nova recebe um link de primeiro acesso.
- o administrador cuida só das contas: cria prescritor, desativa e reativa conta de prescritor e de paciente, e não vê prontuário. desativar tira o acesso na hora e não apaga nada.
- nenhum dado clínico é apagado, e toda criação, alteração e abertura de prontuário fica na trilha de auditoria. o prescritor consulta a trilha de cada paciente pela lista de pacientes, e o administrador consulta em Auditoria, sem ver nome de paciente.
- o paciente pede consulta só nos horários de atendimento que o prescritor cadastra, e o horário fica reservado até o prescritor responder.
- paciente e prescritor exportam o histórico em CSV, e a página de impressão salva em PDF pelo próprio navegador. a exportação do prescritor tem modo sem identificação para pesquisa.
- cada clínica roda a própria instalação. os dados não se misturam porque nem ficam no mesmo lugar.
- nunca comite arquivo com dado de paciente: formulário preenchido, planilha de acompanhamento, exportação de prontuário. o `.gitignore` pega os casos mais comuns, mas confira o `git diff` antes de enviar.

---

## licença

[agpl-3.0](./LICENSE). qualquer clínica pode baixar, usar e mudar. quem mudar e oferecer como serviço precisa publicar as mudanças também.