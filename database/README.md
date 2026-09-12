# configurando o banco postgres pro projeto doisag

aqui tá o guia pra instalar e configurar o postgres pro backend do doisag rodar

## primeiro passo: instalar o postgresql

escolha o guia pro seu sistema operacional

### se você usa windows

1.  **download**: baixa o instalador do site oficial do postgresql
2.  **instalação**: executa o instalador e segue as instruções
      - quando ele pedir pra criar uma senha pro usuário **postgres**, anota essa senha, você vai precisar dela
      - garante que a opção de instalar as **'command line tools'** esteja marcada
3.  **configurar o path**: você precisa adicionar o caminho da pasta `bin` do postgres nas variáveis de ambiente do seu sistema pra conseguir usar os comandos no terminal
      - o caminho geralmente é algo como `c:\program files\postgresql\<versão>\bin`

### se você usa macos

1.  **instalar homebrew**: se você ainda não tiver o homebrew, instala ele primeiro
2.  **instalar o postgres**: depois abre o terminal e roda:
    ```bash
    brew install postgresql
    ```
3.  **iniciar o serviço**: pra garantir que o banco de dados esteja sempre rodando, você pode iniciar o serviço com:
    ```bash
    brew services start postgresql
    ```

### se você usa linux

1.  **instalar o postgres**: só abrir o terminal e rodar:
    ```bash
    sudo apt update
    sudo apt install postgresql postgresql-contrib
    ```

## segundo passo: criar o banco e os usuários

depois de instalar, você precisa entrar no `psql` pra configurar as coisas.

  - **no windows**: abre o terminal e digita `psql -U postgres` e coloca a senha que você criou
  - **no macos**: é só rodar `psql postgres` no terminal
  - **no linux**: é `sudo -u postgres psql`

### criar um superusuário pessoal

é uma boa ideia criar um usuário só pra você não ficar usando o `postgres` padrão o tempo todo. dentro do `psql`, rode o comando:

```sql
create role <seu_nome_de_usuario> with login superuser password '<sua_senha_segura>';
```

*lembra de trocar `<seu_nome_de_usuario>` e `<sua_senha_segura>` pelos seus dados. depois de criar, pode sair do `psql` com `\q`.*

### criar o usuário e o banco da aplicação

1.  entra no `psql` de novo, mas dessa vez com o seu usuário: `psql -U <seu_nome_de_usuario> -d postgres`. ele vai pedir sua senha
2.  lá dentro, cria o usuário que a aplicação vai usar, o nome dele é `admindoisag`:
    ```sql
    create user admindoisag with password '<senha_para_a_aplicacao>';
    ```
    *escolha uma senha forte e anote ela*
3.  agora cria o banco de dados com o nome `doisag` e já define o `admindoisag` como dono:
    ```sql
    create database doisag owner admindoisag;
    ```
4.  pode sair do psql com `\q`

## terceiro passo: configurar o projeto

a senha do banco **não vai em arquivo**, ela vem de variável de ambiente. nenhum segredo fica versionado no repositório.

antes de subir o backend, exporte as duas variáveis obrigatórias:

```bash
export DATABASE_PASSWORD='<a senha que você criou pro admindoisag>'
export JWT_SECRET="$(openssl rand -base64 48)"
```

se quiser os usuários de teste criados na primeira subida, exporte também:

```bash
export SEED_DADOS_TESTE=true
```

no IntelliJ o caminho é `Run` → `Edit Configurations` → `Environment variables`, na configuração do `DoisagApplication`.

a lista completa de variáveis está no [`backend/README.md`](../backend/README.md#configuração)

## quarto passo: subir a aplicação

```bash
cd backend/doisag
./mvnw spring-boot:run
```

na primeira vez que sobe, o hibernate cria as tabelas sozinho (a propriedade `ddl-auto` tá em `update`) e um `CommandLineRunner` cria os usuários de teste

> pra rodar os **testes** você n precisa de nada disso: `./mvnw clean install` usa h2 em memoria e passa sem postgres instalado. o banco de verdade só é necessário pra rodar a aplicação

## sobre os scripts sql

a pasta `physical-model/` tem os scripts de criação (`script-creates.sql`), inserção de exemplo (`script-insert.sql`) e consultas (`scrip-select.sql`)

> ⚠️ **eles não são a fonte de verdade do esquema hoje**. quem cria e altera as tabelas na prática é o hibernate, pelo `ddl-auto: update`, a partir das entidades java. os dois já divergem entre si — por exemplo, o `NOT NULL` da coluna `email` existe no script e não na entidade. ter uma fonte única, com migrações versionadas, é o RNF14 do documento de requisitos

## conclusão

agora seu ambiente tá todo configurado, quando você rodar o backend ele vai conseguir conectar no banco de dados sem problemas. para um passo a passo focado em linux veja o [`passo-a-passo-banco.md`](./passo-a-passo-banco.md)
