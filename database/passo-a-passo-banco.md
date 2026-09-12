## **configuração do PostgreSQL para o projeto doisag**

guia rápido pra quem **usa Linux (Debian/Ubuntu)** e **não tem PostgreSQL instalado**

para os outros sistemas operacionais, veja o [`README.md`](./README.md) desta pasta


### **instalar o PostgreSQL**

No terminal, rode:

```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

---

### **verificar se o serviço está ativo**

```bash
sudo systemctl status postgresql
```

Se aparecer active (running) está tudo certo.
Se não, ative:

```bash
sudo systemctl start postgresql
```

---

### **acessar o console do PostgreSQL**

```bash
sudo -u postgres psql
```

Vai aparecer um prompt tipo:

```
postgres=#
```

---

### **criar o usuário da aplicação**

Ainda dentro do psql, crie o usuário `admindoisag`. Escolha uma senha e anote, porque você vai precisar dela no próximo passo:

```sql
CREATE USER admindoisag WITH PASSWORD '<sua_senha>';
```

---

### **criar o banco de dados e definir o dono**

```sql
CREATE DATABASE doisag OWNER admindoisag;
```

---

### **garantir que o usuário tem todos os privilégios**

```sql
GRANT ALL PRIVILEGES ON DATABASE doisag TO admindoisag;
```

---

### **sair do console**

```sql
\q
```

---

### **configurar as credenciais**

A senha **não vai em arquivo versionado**. Exporte as variáveis antes de subir a aplicação:

```bash
export DATABASE_PASSWORD='<a senha que você criou acima>'
export JWT_SECRET="$(openssl rand -base64 48)"
export SEED_DADOS_TESTE=true
```

> `DATABASE_PASSWORD` e `JWT_SECRET` não têm valor padrão: a aplicação não sobe sem elas. Isso é intencional — é melhor falhar do que subir com credencial publicada no repositório.

---

### **rodar a aplicação**

Na pasta do backend rode:

```bash
cd backend/doisag
./mvnw spring-boot:run
```

Se tudo der certo, a API vai subir em:

```
http://localhost:8080
```

> **não use `mvn clean install`**: o `install` executa os testes, e o único teste do projeto sobe o contexto Spring inteiro e exige o banco acessível — ele falha sempre. Pra gerar o `.jar` use `./mvnw clean package -DskipTests`
>
> Também não precisa instalar o Maven: o `mvnw` do repositório baixa a versão correta sozinho. Só o **JDK 17** é obrigatório.

### **usuários de teste já cadastrados**

Ao rodar o projeto pela primeira vez, um `CommandLineRunner` já cria dois usuários para teste:

| perfil | email | senha |
| :--- | :--- | :--- |
| prescritor | `prescritor@email.com` | `123456` |
| paciente | `paciente@email.com` | `123456` |

> ⚠️ esse seed roda em **toda** inicialização, sem distinção de ambiente — num deploy real ele criaria uma conta de prescritor com senha `123456` em produção. Limitar ao profile `dev` é parte do RNF15
