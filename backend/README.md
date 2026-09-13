# backend do 2ag

api em java que guarda os dados clínicos e aplica as regras de acesso. o que o sistema precisa fazer está em [`docs/requisitos-v2.md`](../docs/requisitos-v2.md).

## tecnologias

- java 17 e spring boot 3.5
- spring data jpa com hibernate, e **flyway** para o esquema do banco
- spring security com sessão em cookie `httpOnly` assinada com jwt (jjwt 0.11.5)
- postgresql em produção e h2 em memória nos testes
- maven, pelo wrapper `mvnw` que já vem no repositório

## rodando

```bash
cd backend/doisag
export DATABASE_PASSWORD='a_senha_do_banco'
export JWT_SECRET="$(openssl rand -base64 48)"
export SESSION_SECURE_COOKIE=false
export SEED_DADOS_TESTE=true
./mvnw spring-boot:run
```

a api responde em `http://localhost:8080/api`. toda rota fica embaixo de `/api` para o front e a api dividirem a mesma origem, sem cors.

para mexer só nas telas, sem instalar postgres, a api sobe com o h2 em memória dos testes. os dados somem quando ela para:

```bash
./mvnw spring-boot:test-run "-Dspring-boot.run.arguments=--spring.profiles.active=test --api.seed.enabled=true --api.session.secure-cookie=false"
```

## configuração

tudo que muda entre ambientes vem de variável de ambiente:

| variável | para que serve | padrão |
| :--- | :--- | :--- |
| `DATABASE_URL` | endereço jdbc do banco | `jdbc:postgresql://localhost:5432/doisag` |
| `DATABASE_USER` | usuário do banco | `admindoisag` |
| `DATABASE_PASSWORD` | senha do banco | **obrigatória** |
| `JWT_SECRET` | chave que assina a sessão, com pelo menos 32 caracteres | **obrigatória** |
| `SESSION_DURATION_MINUTES` | minutos que a sessão dura sem uso | `120` |
| `SESSION_SECURE_COOKIE` | cookie da sessão só trafega em https | `true` |
| `ADMIN_EMAIL` e `ADMIN_PASSWORD` | conta administrativa criada na primeira subida | nenhuma conta |
| `SEED_DADOS_TESTE` | cria as contas de teste | `false` |
| `JPA_SHOW_SQL` | mostra o sql no console | `false` |
| `SERVER_PORT` | porta da api | `8080` |
| `PUBLIC_URL` | endereço onde as pessoas abrem o sistema, usado nos links enviados por e-mail | `http://localhost:5173` |

a api trabalha sempre no fuso `America/Sao_Paulo`, independente da máquina onde roda.

## sessão e perfis

| rota | o que faz |
| :--- | :--- |
| `POST /auth/login` | confere e-mail e senha, grava o cookie `session` e devolve `{id, name, role}` |
| `POST /auth/logout` | apaga o cookie |
| `GET /auth/me` | quem está logado |
| `POST /auth/register` | cadastro do paciente pelo link de convite, já entrando logado |

- o cookie é `httpOnly` e `SameSite=Strict`: o javascript não lê o token e outro site não consegue usar a sessão
- a sessão é renovada enquanto a pessoa usa o sistema, então ninguém é derrubado no meio de um formulário
- depois de 5 senhas erradas seguidas, o login fica bloqueado por 15 minutos
- conta desativada perde o acesso na requisição seguinte
- testes e ferramentas podem mandar o mesmo token no cabeçalho `Authorization: Bearer`

os perfis são `PATIENT`, `PRESCRIBER` e `ADMIN`. quem pode o quê está no `@PreAuthorize` de cada rota, e o vínculo entre prescritor e paciente é conferido no `PatientAccessService`.

## administração

| rota | o que faz |
| :--- | :--- |
| `GET /admin/prescribers` | lista os prescritores |
| `POST /admin/prescribers` | cria conta de prescritor |
| `PUT /admin/prescribers/{id}/active` | ativa ou desativa a conta, sem apagar nada |

o administrador não acessa nenhum dado clínico.

## perfil

| rota | o que faz |
| :--- | :--- |
| `GET /profile` | dados da própria conta |
| `PUT /profile` | nome, data de nascimento, telefone e endereço |
| `PUT /profile/email` | troca o e-mail de acesso pedindo a senha atual |
| `PUT /profile/email-preference` | liga ou desliga os avisos por e-mail |
| `PUT /profile/password` | troca a senha pedindo a atual. as outras sessões caem e este aparelho continua logado |

as rotas de perfil usam sempre a conta da sessão, então ninguém altera o perfil de outra pessoa.

## recuperação de senha

| rota | o que faz |
| :--- | :--- |
| `POST /auth/password-reset/request` | manda o link de senha nova para o e-mail, se ele for de uma conta ativa. a resposta é sempre a mesma |
| `POST /auth/password-reset/confirm` | grava a senha nova. o link vale uma vez, por 30 minutos, e as sessões abertas caem |

cada conta recebe no máximo 3 links por hora e um link novo cancela os anteriores. **o envio de e-mail ainda não está configurado:** por enquanto a mensagem, com o link, aparece no log da api.

## convite de paciente

| rota | o que faz |
| :--- | :--- |
| `POST /invites` | o prescritor gera um link de convite de uso único, válido por 7 dias |
| `GET /invites/{token}` | rota pública que a tela de cadastro usa para conferir o convite |

o banco guarda só o hash do código que vai no link.

## termo de consentimento

| rota | o que faz |
| :--- | :--- |
| `GET /consent-term` | rota pública com a versão e o texto do termo que o cadastro pede |

o texto fica em `src/main/resources/consent/termo-de-consentimento.txt` e ainda é um rascunho que a clínica precisa aprovar. quando o texto mudar, troque também `ConsentTermService.CURRENT_VERSION`: cada aceite fica guardado com a versão e a data.

## erros

toda resposta de erro tem `timestamp`, `status`, `error`, `message` e `path`. erro de validação traz também `errors`, com a mensagem de cada campo.

| status | quando |
| :--- | :--- |
| `400` | dado inválido, parâmetro faltando ou regra de negócio violada |
| `401` | sem sessão, sessão vencida ou senha errada |
| `403` | logado, mas sem permissão para aquele dado, ou conta desativada tentando entrar |
| `404` | registro ou rota que não existe |
| `405` | método http que a rota não aceita |
| `409` | e-mail, CPF ou registro que já pertence a outra conta, com o campo em `errors` |
| `429` | login bloqueado por tentativas erradas |
| `500` | erro inesperado, registrado no log |

`GET /health` responde se a api e o banco estão de pé. é o que o docker usa.

## banco

o esquema vem das migrações do flyway em `src/main/resources/db/migration/`. o hibernate só confere se as entidades batem com o banco, nunca altera nada.

- `V1__esquema_base.sql` consolida as migrações antigas numa base limpa, com restrições de nulidade e índices
- toda mudança de esquema é uma migração nova. nunca edite uma migração que já rodou em algum banco

## testes

```bash
./mvnw clean install
```

os testes rodam no perfil `test`, com h2 em memória, então não precisam de postgres nem de variável de ambiente. todo teste que sobe o contexto do spring precisa de `@ActiveProfiles("test")`.

testes de fluxo que envolvem job agendado ou serialização de resposta devem rodar sem `@Transactional`: a transação do teste esconde carregamento sob demanda que falharia em uso real.

## padrão de código

vale para todo código novo ou reescrito:

- dto de entrada e saída em toda rota, nunca a entidade
- regra de negócio no serviço, com `@Transactional`
- nomes em inglês, em camelCase e descritivos
- comentários curtos, em minúsculas, sem acento e sem pontuação

## situação dos módulos

a fundação (configuração, erros, sessão, administração e migração base) e o módulo de acesso e identidade (login, convite, cadastro, termo de consentimento, perfil e recuperação de senha) já seguem o padrão novo. os módulos clínicos (paciente, consulta, prescrição, escalas, acompanhamento e notificações) ainda são os de 2025 e estão sendo reescritos na ordem do §8.6 do documento de requisitos. até o último deles ser reescrito, o `open-in-view` continua ligado.
