# Guia de implantação e uso para Clínicas
Aqui estão reunidos os guias para utilizar o 2AG de forma independente. Disponibilizamos uma breve visão (para leigos) sobre o sistema e onde encontrar cada coisa se sua clínica deseja testar o sistema.

## 1. Visão geral do sistema
O **2ag** é um software livre (licença AGPL-3.0) desenvolvido na UFFS para acompanhar o tratamento de pacientes com óleo de *Cannabis sativa* ao longo do tempo. Cada clínica instala a sua própria cópia em um servidor próprio, garantindo que os dados de lugares diferentes nunca se misturem. 

O sistema reúne triagem (anamnese), agenda, prescrições, aplicação de escalas clínicas, envio automático de formulários por 90 dias e gráficos de evolução. Ele não faz controle financeiro nem venda de produtos.

### Perfis de acesso
1. **Administração**: Cria as contas dos prescritores e pode desativar ou reativar contas de prescritores e pacientes. Não escolhe a senha de ninguém e não tem acesso a nomes de pacientes na auditoria nem a prontuários.
2. **Prescritor**: Cadastra seus horários e folgas, gera links de convite para os pacientes, registra consultas e receitas, aplica o Mini-Exame (MEEM), configura o envio automático de escalas por 90 dias, acompanha gráficos e exporta dados em PDF ou planilha (CSV). Só vê os seus próprios pacientes.
3. **Paciente (e Responsável)**: Entra no sistema apenas através de um link de convite de uso único enviado pelo prescritor (válido por 7 dias), aceita o termo de consentimento, responde às escalas pelo celular ou computador e pede consultas nos horários livres. Para menores de 18 anos, o cadastro exige a identificação e o aceite do responsável legal.

---

## 2. O que a clínica precisa para colocar no ar
1. **Aprovar o Termo de Consentimento**: O texto atual do sistema é um rascunho; a clínica precisa aprovar a versão final e informar sua Razão Social, CNPJ e contato do Encarregado de Dados (DPO).
2. **Alugar um Servidor (VPS) e um Domínio**: Contratar um servidor virtual Linux no Brasil (2 vCPU, 4 GB de memória e 20 GB de disco criptografado) e um endereço exclusivo (ex.: `2ag.clinica.com.br`) com certificado de segurança HTTPS.
3. **Configurar uma Conta de E-mail**: Fornecer um e-mail da clínica (Google Workspace, Microsoft 365 ou similar) para o envio automático de links de senha e lembretes de consulta e formulários.
4. **Guardar a Chave de Backup**: O prontuário exige guarda mínima de 20 anos (Lei 13.787/2018). O sistema faz cópias diárias protegidas por senha, mas a clínica deve guardar a chave privada em um cofre de senhas fora do servidor e copiar os arquivos para outro disco ou nuvem.

---

## 3. Mapa de Documentos da Clínica ("Onde encontrar cada coisa")

| Objetivo da Clínica | Documento para abrir |
| :--- | :--- |
| **Testar o sistema no próprio computador (demonstração)** | [`WindowsTutorial.md`](./WindowsTutorial.md) (Windows) ou [`LinuxTutorial.md`](./LinuxTutorial.md) (Linux) — ensinam a abrir o sistema com contas e dados fictícios. |
| **Treinar os prescritores e a administração** | [`docs/capacitacao/roteiro-de-capacitacao.md`](./capacitacao/roteiro-de-capacitacao.md) — passo a passo de uma sessão prática passando por todas as telas. |
| **Entregar instruções ao paciente** | [`docs/capacitacao/guia-do-paciente.md`](./capacitacao/guia-do-paciente.md) — folha de 1 página para imprimir ou enviar junto com o convite. |
| **Colocar o sistema no ar no servidor da clínica** | [`docs/guia-de-deploy.md`](./guia-de-deploy.md) — guia passo a passo para o responsável técnico configurar o servidor. |
| **Configurar e testar a cópia diária dos prontuários** | [`docs/backup-e-restauracao.md`](./backup-e-restauracao.md) — instruções de backup automático e tabela de registro de restauração. |
| **Conferir as fichas clínicas originais e regras de cálculo** | Pasta [`docs/scales/`](./scales/) (PDFs originais dos formulários) e Anexo A de [`docs/requisitos-v2.md`](./requisitos-v2.md). |