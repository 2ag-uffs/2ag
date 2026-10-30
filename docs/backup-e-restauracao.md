# backup e restauração

Procedimento de backup do banco do 2ag e, principalmente, o de restauração (RNF07).

Backup que ninguém nunca restaurou não é backup, é só um arquivo. O requisito só está cumprido quando existe o registro de uma restauração bem-sucedida, na tabela do fim desta página.

O prontuário tem guarda mínima de 20 anos (Lei 13.787/2018), então o backup diário **não substitui** o do dia anterior: os arquivos se acumulam, e a cópia antiga sai da pasta local só depois de ir para o destino de longo prazo.

---

## o que é salvo

O banco inteiro, em um arquivo por execução, no formato `custom` do PostgreSQL — o mesmo que o `pg_restore` lê. Isso cobre prontuário, escalas respondidas, agenda, prescrições, trilha de auditoria e contas.

Não entram no backup, porque não existem: arquivos enviados por paciente (o sistema não recebe anexo) e segredo de configuração, que vive em variável de ambiente.

---

## o backup sai cifrado

Quem leva o pendrive ou abre o bucket leria o prontuário inteiro. Por isso o arquivo é cifrado com o [age](https://github.com/FiloSottile/age) antes de tocar o disco, e o dado em claro nunca fica gravado.

A cifra usa um par de chaves:

- a **chave pública** (`age1...`) fica no servidor, em `BACKUP_AGE_PUBKEY` no `.env`. Ela só cifra. Quem leva o servidor, o disco ou os arquivos não lê backup nenhum;
- a **chave privada** (`AGE-SECRET-KEY-1...`) fica **fora do servidor**: no cofre de senhas da clínica e uma cópia com a equipe da UFFS. Ela é a única coisa que abre os backups. **Perder a chave privada é perder todos os backups.**

Para gerar o par, em qualquer máquina com o `age` instalado (`apt install age` no Debian e no Ubuntu), **fora da pasta do repositório**, para a chave nunca ir parar num commit:

```bash
age-keygen -o ~/chave-do-backup-2ag.txt
```

O comando mostra a chave pública na tela e grava a privada no arquivo. A pública vai para o `.env` do servidor; o arquivo vai para o cofre e some da máquina onde foi gerado.

Sem `BACKUP_AGE_PUBKEY` o script se recusa a gerar backup, de propósito. `BACKUP_SEM_CIFRA=true` grava o `.dump` em claro e só serve para testar no próprio computador.

Não cifrar por campo dentro do banco é decisão registrada no `docs/requisitos-v2.md` (RNF05): o banco não publica porta, só a api chega nele, e o que sai do servidor é este backup, cifrado.

---

## rodando o backup

No servidor o banco roda dentro do `docker compose` e não publica porta, então o script roda o `pg_dump` dentro do container do banco. Rode da pasta onde está o `docker-compose.yml` (ele lê `POSTGRES_DB`, `POSTGRES_USER` e `BACKUP_AGE_PUBKEY` do `.env`, sem precisar de senha):

```bash
./scripts/backup-banco.sh /var/backups/2ag
```

O arquivo sai como `doisag-2026-09-14-0300.dump.age`. Precisa do `age` instalado no servidor.

Com o PostgreSQL instalado na própria máquina, sem compose, o script usa o `pg_dump` 17 local:

```bash
BACKUP_VIA_COMPOSE=false DATABASE_PASSWORD='a_senha_do_banco' ./scripts/backup-banco.sh /caminho/dos/backups
```

Variáveis aceitas, todas com padrão: `DATABASE_NAME` e `DATABASE_USER` (os do `.env`), `DATABASE_HOST` (localhost) e `DATABASE_PORT` (5432) só no modo sem compose, `BACKUP_AGE_PUBKEY` (a do `.env`) e `BACKUP_DIAS`, que é por quantos dias o arquivo fica nessa pasta antes de ser apagado (30).

### todo dia, sem ninguém lembrar

No servidor, com o cron, às 3 da manhã. Pelo compose não há senha nenhuma na linha:

```
0 3 * * * cd /opt/2ag && ./scripts/backup-banco.sh /var/backups/2ag >> /var/log/2ag-backup.log 2>&1
```

Olhe o `/var/log/2ag-backup.log` de vez em quando: o script escreve o nome do arquivo gravado, e um erro ali é um dia sem backup.

### para onde vai a cópia

A pasta local protege de erro humano e de uma tabela corrompida, mas não de perder a máquina. Uma cópia precisa sair do servidor: outro disco, um bucket, ou o backup do provedor. Como o arquivo já sai cifrado, o destino não precisa ser de confiança. Sem isso, o requisito continua em aberto.

---

## restaurando

Pelo compose, da pasta do `docker-compose.yml`, apontando a chave privada do age:

```bash
BACKUP_AGE_KEY=/caminho/da/chave-do-backup-2ag.txt ./scripts/restaurar-banco.sh /var/backups/2ag/doisag-2026-09-14-0300.dump.age doisag_teste
```

O script pede o nome do banco digitado por extenso antes de mexer em qualquer coisa, porque a restauração apaga e recria o que já existe. Sem terminal (cron, ci) a confirmação vai na variável `CONFIRMAR_RESTAURACAO=doisag_teste`. O banco de destino é criado se não existir.

Sem compose: `BACKUP_VIA_COMPOSE=false DATABASE_PASSWORD='...' BACKUP_AGE_KEY=... ./scripts/restaurar-banco.sh arquivo [banco]`.

**Para o teste, restaure sempre num banco separado** (`doisag_teste`), nunca por cima do banco em uso.

No dia ruim, para restaurar por cima do banco de verdade: pare a api antes (`docker compose stop api`), restaure no banco do `.env` e suba de novo (`docker compose up -d api`).

Depois de restaurar, o teste só vale se você conferir:

1. a api sobe apontando para o banco restaurado e o `GET /api/health` responde;
2. uma consulta antiga aparece no histórico de um paciente, com a conduta registrada;
3. uma escala respondida aparece com o escore e a faixa;
4. a trilha de auditoria mostra os eventos anteriores ao backup.

O ci faz uma versão curta disso a cada push (`scripts/ci-compose-smoke.sh`): gera um par de chaves de mentira, faz o backup cifrado pelo compose, restaura num banco separado e confere que a conta criada antes está lá. Isso prova que os scripts funcionam, não substitui o teste no servidor do piloto.

---

## registro das restaurações testadas

Cada linha aqui é uma restauração feita de verdade, não uma intenção.

| Quando | Arquivo restaurado | Quem fez | Resultado |
| :--- | :--- | :--- | :--- |
| | | | |
