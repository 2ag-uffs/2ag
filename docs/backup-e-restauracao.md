# backup e restauração

Procedimento de backup do banco do 2ag e, principalmente, o de restauração (RNF07).

Backup que ninguém nunca restaurou não é backup, é só um arquivo. O requisito só está cumprido quando existe o registro de uma restauração bem-sucedida, na tabela do fim desta página.

O prontuário tem guarda mínima de 20 anos (Lei 13.787/2018), então o backup diário **não substitui** o do dia anterior: os arquivos se acumulam, e a cópia antiga sai da pasta local só depois de ir para o destino de longo prazo.

---

## o que é salvo

O banco inteiro, em um arquivo por execução, no formato `custom` do PostgreSQL — o mesmo que o `pg_restore` lê. Isso cobre prontuário, escalas respondidas, agenda, prescrições, trilha de auditoria e contas.

Não entram no backup, porque não existem: arquivos enviados por paciente (o sistema não recebe anexo) e segredo de configuração, que vive em variável de ambiente.

---

## rodando o backup

```bash
export DATABASE_PASSWORD='a_senha_do_banco'
./scripts/backup-banco.sh /caminho/dos/backups
```

Variáveis aceitas, todas com padrão: `DATABASE_NAME` (doisag), `DATABASE_USER` (admindoisag), `DATABASE_HOST` (localhost), `DATABASE_PORT` (5432) e `BACKUP_DIAS`, que é por quantos dias o arquivo fica nessa pasta antes de ser apagado (30).

### todo dia, sem ninguém lembrar

No servidor, com o cron, às 3 da manhã:

```
0 3 * * * cd /opt/2ag && DATABASE_PASSWORD='...' ./scripts/backup-banco.sh /var/backups/2ag >> /var/log/2ag-backup.log 2>&1
```

A senha não deve ficar escrita no crontab de um servidor compartilhado: prefira um arquivo de ambiente lido pelo cron, com permissão só do dono.

### para onde vai a cópia

A pasta local protege de erro humano e de uma tabela corrompida, mas não de perder a máquina. Uma cópia precisa sair do servidor: outro disco, um bucket, ou o backup do provedor. Sem isso, o requisito continua em aberto.

---

## restaurando

```bash
export DATABASE_PASSWORD='a_senha_do_banco'
./scripts/restaurar-banco.sh /var/backups/2ag/doisag-2026-09-14-0300.dump doisag_teste
```

O script pede o nome do banco digitado por extenso antes de mexer em qualquer coisa, porque a restauração apaga e recria o que já existe.

**Para o teste, restaure sempre num banco separado** (`doisag_teste`), nunca por cima do banco em uso.

Depois de restaurar, o teste só vale se você conferir:

1. a api sobe apontando para o banco restaurado e o `GET /api/health` responde;
2. uma consulta antiga aparece no histórico de um paciente, com a conduta registrada;
3. uma escala respondida aparece com o escore e a faixa;
4. a trilha de auditoria mostra os eventos anteriores ao backup.

---

## registro das restaurações testadas

Cada linha aqui é uma restauração feita de verdade, não uma intenção.

| Quando | Arquivo restaurado | Quem fez | Resultado |
| :--- | :--- | :--- | :--- |
| | | | |
