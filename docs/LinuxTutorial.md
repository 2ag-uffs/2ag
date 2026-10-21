# 2ag

sistema livre para acompanhamento terapêutico longitudinal de pacientes em tratamento com óleo de Cannabis sativa. O 2ag digitaliza o ciclo de cuidado de uma clínica que prescreve fitocanabinoides: triagem por anamnese, registro de consulta e prescrição, aplicação das escalas clínicas padronizadas com cálculo dos escores, envio automatizado dos formulários de acompanhamento ao longo dos 90 dias de tratamento e visualização gráfica da evolução dos sintomas.

## projeto

sistema desenvolvido como ação de extensão no componente GCH1993 — Projeto de Integração de Extensão da Universidade Federal da Fronteira Sul (UFFS), campus Chapecó.

o que o sistema precisa fazer está em [`docs/requisitos-v2.md`](./docs/requisitos-v2.md).

## o que você precisa instalar
Este tutorial utiliza o docker como base para instalação.
O tutorial sem uso de Docker será futuramente adicionado.

1. Atualize o sistema;
``bash
sudo apt update
``
2. Instale o Git (será utilizado para clonarmos o repositório em seguida);
``bash
sudo apt install git
``
3. Clone o repositório para sua máquina de acesso;
``bash
git clone https://github.com/2ag-uffs/2ag
``
4. Instale o Docker para rodar o servidor;
``bash
sudo apt install docker.io
``

5. [OPCIONAL] Instale o pacote de utilidades do linux (importante para utilizarmos o próximo comando caso seu usuário de acesso não seja um perfil super-usuário)
``bash
sudo apt install util-linux-extra
``
6. [OPCIONAL] Dê permissão ao seu usuário para compôr o servidor.
``bash
sudo usermod -aG docker $USER
``
7. [OPCIONAL] Atualize o grupo de permissão.
``bash
newgrp docker
``

---

## rodando com docker

**1.** copie o arquivo de exemplo das configurações:

```bash
cp .env.example .env
```

**2.** abra o `.env` e preencha:

- `POSTGRES_PASSWORD`: uma senha para o banco
- `JWT_SECRET`: a chave que assina a sessão. para gerar uma, use `openssl rand -base64 48`
- `ADMIN_EMAIL` e `ADMIN_PASSWORD`: a conta administrativa, criada na primeira subida. é ela que cria as contas de prescritor
- `PUBLIC_URL`: o endereço onde as pessoas abrem o sistema, com https. vai nos links enviados por e-mail. no seu computador, `https://localhost`
- `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` e `MAIL_FROM`: o servidor de e-mail que manda o link de senha nova e o link que confirma a troca do e-mail de acesso. sem `MAIL_HOST` o e-mail não sai e o log mostra só o destinatário e o assunto, sem o link

sem e-mail configurado, quem esquece a senha depende do administrador: em *Prescritores*, o botão **Senha nova** gera o link de recuperação na tela, para o administrador entregar à pessoa. ele pede a senha do próprio administrador e fica registrado na auditoria. o link de primeiro acesso de uma conta nova de prescritor funciona igual: sem e-mail, aparece na tela. para a senha do administrador não existe essa saída — ele troca em *Perfil* enquanto lembra a atual, e se esquecer só com `MAIL_HOST`.

`POSTGRES_PASSWORD`, `JWT_SECRET` e `PUBLIC_URL` não têm valor padrão de propósito: sem elas a aplicação não sobe. o `PUBLIC_URL` precisa ser exatamente o endereço pelo qual as pessoas abrem o sistema — com ele errado, o login funciona e todo o resto responde `403`.

sem `ADMIN_EMAIL` e `ADMIN_PASSWORD` o sistema sobe normalmente, mas nenhuma conta é criada e ninguém consegue entrar: o autocadastro do paciente exige convite, o convite exige prescritor e o prescritor exige administrador. o log da api avisa quando isso acontece; preencha as duas e suba de novo.

**3.** gere um certificado só para o seu computador, porque o nginx não sobe sem certificado:

```bash
./scripts/gerar-certificado-local.sh
```

**4.** suba tudo:

```bash
docker compose up
```

abra https://localhost. o navegador avisa que o certificado não é confiável, porque ele acabou de ser gerado no seu computador: aceite o aviso, mas não instale o certificado como confiável, senão o navegador passa a exigir https também no `npm run dev`. o nginx do front entrega a tela pela porta 443, redireciona a 80 para https e repassa as chamadas de `/api` para o backend, então só essas duas portas ficam expostas. o banco não fica acessível de fora da rede do compose.

os dados ficam num volume e não somem quando os containers param. para parar, use `ctrl+c`.

> o `.env.example` já vem com `SESSION_SECURE_COOKIE=true`, que é o valor do servidor e do compose no computador, os dois em https. só o `npm run dev` sem docker roda em http, e aí a variável precisa ser `false` (veja a seção seguinte). a api confere na subida que o valor combina com o esquema do `PUBLIC_URL` e recusa subir quando não combina.

---
## rodando os testes

```bash
cd backend/doisag
./mvnw clean install
```

não precisa de postgres: os testes usam banco em memória.

```bash
cd frontend
npm run lint
npm test
npm run build
```

os testes do front usam vitest e testing library e ficam ao lado do arquivo testado, como `initials.test.js` junto de `initials.js`. o ci do github roda tudo isso a cada push e também confere se as imagens do docker continuam montando.

---

## colocando no ar

o passo a passo do servidor, uma VPS alugada para a clínica (docker, domínio, certificado, `.env` de produção, primeira entrada, backup e atualização) está em [`docs/guia-de-deploy.md`](./docs/guia-de-deploy.md). a seção *rodando com docker* acima é a receita do computador de desenvolvimento, não a do servidor.

---

## backup

o banco tem script de backup e de restauração em [`scripts/`](./scripts), e o procedimento está em [`docs/backup-e-restauracao.md`](./docs/backup-e-restauracao.md). o backup sai cifrado com o `age` (chave pública em `BACKUP_AGE_PUBKEY` no `.env`, privada guardada fora do servidor) e roda pelo compose, sem precisar de senha nem de porta aberta:

```bash
./scripts/backup-banco.sh /var/backups/2ag
```

precisa do `age` instalado no servidor (`apt install age`). com o postgres instalado na própria máquina, sem compose, `BACKUP_VIA_COMPOSE=false` e o `pg_dump` e o `pg_restore` 17 instalados.

o backup do dia não substitui o do dia anterior, porque o prontuário tem guarda mínima de 20 anos. e backup que ninguém restaurou não vale: o documento tem a tabela onde cada teste de restauração fica registrado.

---

## onde fica cada coisa

```
backend/     a api, em java com spring boot
frontend/    a tela, em react
database/    a modelagem de 2025 e o passo a passo de instalação do banco
docs/        requisitos, guia de deploy, backup, segurança, escalas clínicas, identidade visual e documentos da extensão
```

---

## licença

[agpl-3.0](./LICENSE). qualquer clínica pode baixar, usar e mudar. quem mudar e oferecer como serviço precisa publicar as mudanças também.
