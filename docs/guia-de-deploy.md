# guia de deploy

Passo a passo para colocar o 2ag no ar numa VPS alugada para a clínica, com https (RNF05), backup diário (RNF07) e configuração por variável de ambiente (RNF15). Quem nunca viu o projeto consegue subir seguindo este documento: o que precisa saber está aqui ou nos documentos apontados.

A seção *rodando com docker* do [`README.md`](../README.md) é a receita do computador de desenvolvimento. Este guia é a do servidor.

---

## o que você precisa antes

- **Uma VPS Linux** (Ubuntu 24.04 ou Debian 12) num data center no Brasil, com acesso `sudo`, 2 vCPU, 4 GB de memória e 20 GB de disco. Para 300 pessoas o sistema roda com menos, mas as imagens são montadas no próprio servidor a cada atualização e o build fica apertado com 2 GB. De preferência, o contrato fica no nome da clínica, que é a controladora dos dados (LGPD), e o grupo entra como administrador. O disco, ou pelo menos o volume onde o docker guarda os dados, cifrado: LUKS na instalação ou a cifra que o provedor oferece. Quem leva o disco não pode ler o prontuário.
- **Um nome próprio para o sistema**, como `2ag.clinica.com.br`, apontando para o servidor por registro A. Não crie registro AAAA por enquanto: a rede do compose é só IPv4, e um acesso por IPv6 chegaria ao nginx com o endereço do próprio docker, o que estragaria o limite de tentativas por endereço. Precisa ser um nome dedicado ao 2ag, e não o domínio raiz da clínica: o sistema manda o navegador exigir https nesse nome e nos subdomínios dele por um ano.
- **Um certificado para esse nome.** Let's Encrypt é gratuito e renova sozinho com o `certbot`; um certificado comprado também serve, mas aí a renovação é manual e, desde março de 2026, eles valem no máximo 200 dias.
- **Uma conta de e-mail SMTP** (o e-mail da clínica no Google Workspace, Microsoft 365 ou provedor parecido). Sem ela o sistema funciona, mas ninguém recebe link de senha nova nem lembrete, a troca de e-mail de acesso não acontece e o administrador só consegue entregar links na mão.
- **O par de chaves do backup**, gerado com `age-keygen` como explica [`backup-e-restauracao.md`](./backup-e-restauracao.md). A chave pública vai para o servidor; a privada fica no cofre de senhas da clínica, fora do servidor.
- **As portas 80 e 443 liberadas** no firewall do provedor, e a 22 só para quem administra.

---

## 1. preparar o servidor

```bash
sudo apt update && sudo apt install -y git age openssl
```

Instale o docker com o plugin do compose seguindo a página oficial (https://docs.docker.com/engine/install/) e coloque o seu usuário no grupo `docker`:

```bash
sudo usermod -aG docker "$USER"
```

Saia e entre de novo para o grupo valer. Confira com `docker compose version`.

Firewall da máquina, se usar o `ufw`:

```bash
sudo ufw allow 22 && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```

O docker publica portas por fora do `ufw`, então o que segura é o próprio `docker-compose.yml`: só o serviço `web` publica porta (80 e 443). Nunca acrescente `ports` no serviço `banco`. O relógio do servidor precisa estar certo (`timedatectl` deve mostrar `NTP service: active`): os containers rodam no fuso `America/Sao_Paulo`, mas o horário vem da máquina.

---

## 2. baixar o projeto

```bash
sudo mkdir -p /opt/2ag && sudo chown "$USER" /opt/2ag
git clone https://github.com/2ag-uffs/2ag.git /opt/2ag
cd /opt/2ag
```

Todos os comandos daqui em diante rodam dentro de `/opt/2ag`. O script de backup e o do certificado assumem essa pasta.

---

## 3. um certificado provisório

O nginx não sobe sem certificado, e o certificado de verdade só pode ser emitido com o nginx no ar respondendo pelo domínio. Então a primeira subida usa um certificado autoassinado, trocado logo depois:

```bash
./scripts/gerar-certificado-local.sh 2ag.clinica.com.br
```

Ele cria `certs/fullchain.pem` e `certs/privkey.pem`. A pasta `certs/` está no `.gitignore`, nada dela vai para o git.

---

## 4. o .env de produção

```bash
cp .env.example .env
chmod 600 .env
```

Abra o `.env` e preencha. O arquivo de exemplo explica cada linha; o que importa no servidor:

| variável | o que colocar |
| :--- | :--- |
| `POSTGRES_PASSWORD` | uma senha gerada: `openssl rand -base64 24` |
| `JWT_SECRET` | `openssl rand -base64 48`. nunca reaproveite uma chave que já apareceu em alguma versão antiga do repositório |
| `ADMIN_EMAIL` e `ADMIN_PASSWORD` | o e-mail de quem vai administrar e uma senha com 12 caracteres ou mais, com letra maiúscula, número e símbolo. a conta nasce na primeira subida; a senha ela troca depois em *Perfil*, o e-mail não. se a senha não seguir a regra, a conta não é criada e o log da api diz por quê |
| `PUBLIC_URL` | `https://2ag.clinica.com.br`, exatamente como as pessoas vão abrir, sem barra no fim |
| `CLINIC_NAME` e `CLINIC_DPO_CONTACT` | a razão social da clínica e o contato do encarregado de dados. entram no termo de consentimento no lugar dos colchetes |
| `SESSION_SECURE_COOKIE` | `true`, que já é o valor do exemplo |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | os dados da conta SMTP. porta 587 com STARTTLS é o padrão |
| `BACKUP_AGE_PUBKEY` | a chave pública do backup (`age1...`) |

O que **não** pode existir no `.env` do servidor:

- `SEED_DADOS_TESTE`: nem a linha. O exemplo vem com ela comentada. Ligada, ela criaria contas de teste com uma senha que está no README. A api ignora o seed em qualquer endereço `https://` que não seja `localhost`, mas a primeira trava é a variável não existir.
- `EMAIL_LOG_TEXT=true`: escreveria no log os links de senha nova.

A api recusa subir, com a razão no log, quando: o `PUBLIC_URL` não é um endereço completo; o esquema do `PUBLIC_URL` não combina com `SESSION_SECURE_COOKIE` (`https` pede `true`); `MAIL_HOST` está preenchido sem `MAIL_FROM` nem `MAIL_USERNAME`; `JWT_SECRET` tem menos de 32 caracteres.

---

## 5. subir

```bash
docker compose build
docker compose run --rm --no-deps web nginx -t
docker compose up -d --wait
docker compose ps
```

O segundo comando confere a configuração do nginx e o certificado antes de subir qualquer coisa. O `--wait` espera o banco, a api e o nginx responderem ao healthcheck; `docker compose ps` deve mostrar os três como `healthy`. A primeira vez demora alguns minutos por causa do build.

Os logs ficam em `docker compose logs -f api` (e `web`, `banco`), limitados a 50 MB por container. Na primeira subida o log da api mostra `conta administrativa criada para <e-mail>`. Se em vez disso aparecer um erro sobre `PUBLIC_URL`, `SESSION_SECURE_COOKIE` ou `MAIL_FROM`, corrija o `.env` e rode `docker compose up -d` de novo.

---

## 6. o certificado de verdade

### com Let's Encrypt

```bash
sudo apt install -y certbot
sudo certbot certonly --webroot -w /opt/2ag/acme -d 2ag.clinica.com.br \
    --deploy-hook /opt/2ag/scripts/instalar-certificado.sh
```

O certbot prova o domínio pela pasta `acme/`, que o nginx já serve na porta 80, e ao terminar chama o script, que copia `fullchain.pem` e `privkey.pem` para `certs/` e recarrega o nginx. A renovação é automática: o pacote instala um timer que roda `certbot renew` duas vezes por dia e repete o mesmo script quando renova. Confira a renovação sem esperar o vencimento:

```bash
sudo certbot renew --dry-run
```

### com certificado comprado

Peça ao fornecedor o certificado com a cadeia completa (o seu certificado seguido dos intermediários, nessa ordem) e a chave privada sem senha. Com os dois arquivos numa pasta, chamados `fullchain.pem` e `privkey.pem`:

```bash
./scripts/instalar-certificado.sh /pasta/com/os/arquivos
```

Anote o vencimento, porque a renovação é na mão. O comando abaixo mostra a data, e vale para qualquer um dos dois caminhos:

```bash
openssl x509 -enddate -noout -in certs/fullchain.pem
```

Certificado vencido não é só um aviso: como o sistema manda o navegador exigir https por um ano, o navegador bloqueia a página sem opção de continuar.

---

## 7. conferir por fora

```bash
curl -sI http://2ag.clinica.com.br/ | head -1
curl -s -D - -o /dev/null https://2ag.clinica.com.br/api/health
```

O primeiro responde `301` (http só redireciona). O segundo responde `200` com o cabeçalho `strict-transport-security` (é um `GET` só com os cabeçalhos: a rota de saúde não aceita `HEAD`, então `curl -I` nela responderia `401`). No navegador, abra o `PUBLIC_URL`, confira o cadeado e entre com `ADMIN_EMAIL` e `ADMIN_PASSWORD`. Nas ferramentas do desenvolvedor, o cookie se chama `__Host-session` e vem com `Secure` e `HttpOnly`.

Se o login funciona mas todo o resto responde `403`, o `PUBLIC_URL` do `.env` não é exatamente o endereço aberto no navegador (`www` a mais ou a menos, `http` em vez de `https`, o IP no lugar do nome).

---

## 8. primeira entrada

1. O administrador entra, troca a senha inicial em *Perfil* e, em *Prescritores*, cria a conta de cada prescritor. A pessoa recebe por e-mail um link de primeiro acesso que vale 48 horas e cria a própria senha. Sem `MAIL_HOST` o link aparece na tela para o administrador entregar.
2. O prescritor entra e gera convites para os pacientes dele (7 dias cada). O paciente se cadastra pelo convite e aceita o termo de consentimento.
3. O administrador não vê dado clínico nenhum; quem vê é o prescritor de cada paciente.

**Antes de cadastrar o primeiro paciente:** o termo de consentimento que o sistema mostra ainda é um rascunho (`backend/doisag/src/main/resources/consent/termo-de-consentimento.txt`). A clínica precisa aprovar o texto e ele precisa entrar numa versão nova antes do uso com pacientes.

---

## 9. backup todo dia

O procedimento completo, com a restauração, está em [`backup-e-restauracao.md`](./backup-e-restauracao.md). No servidor, o que falta fazer:

1. Garantir que `BACKUP_AGE_PUBKEY` está no `.env` e que a chave privada está no cofre da clínica, fora do servidor.
2. Criar a pasta dos backups e o arquivo de log, que nascem do `root`, e entregar os dois ao seu usuário:

```bash
sudo mkdir -p /var/backups/2ag && sudo chown "$USER" /var/backups/2ag
sudo touch /var/log/2ag-backup.log && sudo chown "$USER" /var/log/2ag-backup.log
```

3. Rodar um backup na mão para conferir: `./scripts/backup-banco.sh /var/backups/2ag`.
4. Agendar no `crontab` do seu usuário, que está no grupo `docker`, com `crontab -e`:

```
0 3 * * * cd /opt/2ag && ./scripts/backup-banco.sh /var/backups/2ag >> /var/log/2ag-backup.log 2>&1
```

5. Copiar os arquivos de `/var/backups/2ag` para fora do servidor (outro disco, um bucket, o backup do provedor). Eles saem cifrados, então o destino não precisa ser de confiança.
6. Fazer uma restauração de teste num banco separado e anotar na tabela do documento de backup. O requisito de backup só fecha com esse registro.

---

## 10. atualizar

```bash
cd /opt/2ag
./scripts/backup-banco.sh /var/backups/2ag
git pull
docker compose build
docker compose up -d --wait
docker compose ps
```

Backup antes de qualquer atualização. As migrações do banco rodam sozinhas quando a api sobe, e só andam para a frente. Enquanto a api reinicia, o nginx responde `502` por alguns segundos. Nunca rode `docker compose down -v` no servidor: o `-v` apaga o volume do banco.

---

## problemas comuns

- **A api fica reiniciando.** `docker compose logs api` mostra a razão: quase sempre é um valor do `.env` (as regras da seção 4). Corrija e `docker compose up -d`.
- **O web não sobe.** Falta `certs/fullchain.pem` ou `certs/privkey.pem`, ou a chave não bate com o certificado. `docker compose run --rm --no-deps web nginx -t` diz qual é o arquivo.
- **Porta 80 ou 443 ocupada.** Outro servidor web na máquina: `ss -ltnp | grep -E ':80 |:443 '`.
- **Login entra, o resto dá 403.** `PUBLIC_URL` diferente do endereço aberto (seção 7).
- **Muita gente recebe "muitas requisições".** O limite é por endereço de rede: 10 tentativas de login por minuto e 20 chamadas por segundo. Numa clínica onde todos saem pelo mesmo IP isso pode apertar; os limites do nginx ficam em `frontend/nginx.conf` e os da api em `LOGIN_*` no `.env`.
- **E-mail não chega.** `docker compose logs api | grep -i e-mail` mostra se saiu. Confira `MAIL_FROM`, a porta 587 e se o provedor exige senha de aplicativo.
- **Esqueceram a senha do administrador.** Com e-mail funcionando, use *Esqueci minha senha* na tela de entrada. Sem e-mail: coloque uma senha nova em `ADMIN_PASSWORD`, acrescente `ADMIN_PASSWORD_RESET=true` no `.env` e rode `docker compose up -d`. A api troca a senha, derruba as sessões abertas e avisa no log. Entre com a senha nova, **apague a linha `ADMIN_PASSWORD_RESET`** e rode `docker compose up -d` de novo; enquanto ela estiver lá, a senha volta para a do `.env` a cada subida.

---

## checklist final

- [ ] https abre com cadeado e http redireciona
- [ ] `.env` com `chmod 600`, sem `SEED_DADOS_TESTE` e sem `EMAIL_LOG_TEXT=true`
- [ ] o administrador entrou e trocou a senha inicial
- [ ] renovação do certificado testada (`certbot renew --dry-run`) ou vencimento anotado
- [ ] backup agendado, cópia saindo do servidor e primeira restauração testada e registrada
- [ ] chave privada do backup guardada fora do servidor
- [ ] termo de consentimento aprovado pela clínica antes do primeiro paciente
