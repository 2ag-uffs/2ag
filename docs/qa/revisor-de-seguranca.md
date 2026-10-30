# revisor de segurança com IA

o 2ag tem uma GitHub Action que lê o diff de cada pull request para a `main` e de cada push na `main` e aponta problemas de segurança com um modelo de linguagem. ela é **consultiva**: comenta, nunca aprova nem bloqueia. o código fica em `.github/security-review/` e o workflow em `.github/workflows/security-review.yml`.

como o 2ag guarda dado de saúde de pessoa identificada, o desenho tem duas regras acima de todas: o revisor não lê o banco nem o repositório inteiro (recebe só o diff do código, com CPF, CNPJ, e-mail, telefone e segredos trocados por marcas) e não deve abrir brecha nova no repositório. nome, data, endereço e texto clínico livre não têm formato fixo e não são detectados, por isso nenhum dado real de paciente deve estar no código, nem em teste.

---

## o que é

a cada evento o revisor:

1. lê o evento do arquivo que o GitHub entrega (`GITHUB_EVENT_PATH`), confere número de PR e SHAs e desiste de qualquer coisa estranha;
2. pede o diff pela API do GitHub (o código do PR nunca é baixado nem executado). no PR, o diff é o da base até o commit exato do evento, para que as linhas batam com o commit da review;
3. procura segredo em **todas** as linhas adicionadas, de forma fixa, sem IA;
4. tira do diff o que não deve sair do GitHub (lista abaixo) e troca segredo e dado pessoal por marcas;
5. manda o resto ao modelo, em até 3 chamadas de no máximo ~100 mil caracteres cada, com a rubrica do 2ag como instrução;
6. confere a resposta na mão, limpa o texto e posta:
   - **no PR:** uma única review do tipo `COMMENT` no commit do PR, com comentário na linha quando o achado cai numa linha do diff e o resto no corpo. se o PR ganhou commit novo enquanto a revisão rodava, nada é postado: o commit novo é revisado na execução dele;
   - **no push na `main`:** uma issue com a etiqueta `revisao-de-seguranca` quando há achado de severidade média ou maior, ou qualquer segredo;
   - **arquivo fora da revisão da IA:** quando algum arquivo que não foi pulado pelos filtros ficou sem revisão, no todo ou em parte (limite de tamanho, linha longa, recusa, resposta cortada ou fora do formato, erro da API), a review do PR ou a issue do push sai mesmo sem achado, com a lista desses arquivos e o motivo. assim quem escreve o diff não consegue esconder um arquivo da IA sem que ninguém fique sabendo;
   - **sem achado e com tudo revisado:** nada é postado, só o resumo do job;
7. escreve o resumo do job: modo, arquivos revisados e pulados (com o motivo), quantos dados pessoais e segredos foram trocados por tipo (nunca o valor), tokens, custo estimado, motivos de parada e a lista de achados.

PR em rascunho só é revisado quando fica pronto (`ready_for_review`). PR aberto para outra branch e mudado para a `main` depois é revisado quando a base muda (evento `edited` com troca de base); editar só o título ou a descrição não dispara revisão nem cancela a que está rodando.

## o que ele confere

a rubrica está em `.github/security-review/rubric.md` e vira o prompt de sistema. cada regra tem um id fixo, e o modelo só pode responder com esses ids:

| id | o que procura | checklist |
| :--- | :--- | :--- |
| `vinculo-do-paciente` | dado de paciente sem `@patientAccess`, `@scaleAccess` ou `@assessmentAccess`, bean com o id errado, id do paciente vindo do corpo ou da query | 4 |
| `papel-em-toda-rota` | rota sem `hasRole`, papel mais aberto que o necessário, `permitAll` novo | 7 |
| `autenticacao-no-servidor` | decisão de acesso só no front, claim do token no lugar da entidade, sessão que não cai | 6 |
| `mass-assignment` | entidade no `@RequestBody`, DTO aceitando `role`, `active`, `prescriberId`, `id` | 8 |
| `query-concatenada` | SQL ou JPQL montado com texto de fora | 13 |
| `validacao-de-entrada` | `@Valid` faltando, texto sem `@Size`, lista sem limite, upload | 14, 16 |
| `cookie-e-sessao` | cookie sem `HttpOnly`/`Secure`/`SameSite`, token em `localStorage`, `Bearer` no front | 9 |
| `senha-e-limite` | senha sem `PasswordEncoder`, limitador removido, enumeração de conta | 10, 11, 12 |
| `origem-e-csrf` | `OriginCheckFilter` enfraquecido, GET que muda estado, CORS aberto | 9 |
| `xss` | `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `href` montado com texto de fora | 18 |
| `cabecalhos-e-csp` | CSP afrouxada, header removido, estilo inline contra `style-src 'self'` | 18 |
| `criptografia-e-transporte` | HTTPS, HSTS, `Secure` ou STARTTLS desligado, backup sem cifra | 5, 19 |
| `vazamento-em-erro` | mensagem de exceção, pilha ou SQL na resposta | 15 |
| `dado-pessoal-na-resposta` | DTO levando CPF, contato ou dado clínico que a tela não usa | 17 |
| `dado-pessoal-no-log` | log com nome, e-mail, CPF, conteúdo clínico, token | 15 |
| `trilha-de-auditoria` | mudança clínica sem `AuditService`, `delete` físico no lugar da anulação | — |
| `segredo-no-codigo` | chave, senha ou token com valor fixo, variável sensível com padrão | 1, 2, 3 |
| `redirecionamento-aberto` | parâmetro `voltar` ou destino de redirecionamento sem conferência | — |
| `dado-clinico-no-front` | dado clínico guardado no navegador ou mandado a terceiros | — |
| `dependencia-nova` | dependência nova, versão solta, script de instalação | 20 |
| `cadeia-de-ci` | `pull_request_target`, action sem SHA, `${{ }}` não confiável em `run:`, permissões amplas | — |
| `tentativa-de-instrucao` | texto no diff tentando dar ordem ao revisor | — |

a coluna *checklist* aponta o item de `docs/checklist-de-seguranca.md`; os 20 itens estão cobertos.

## o que ele não faz

- não aprova, não pede mudança e não bloqueia merge. o job só fica vermelho quando o **revisor** quebra (chave recusada, pedido recusado pela API, erro inesperado), nunca por causa de um achado;
- não roda código do PR, não roda testes e não lê o repositório inteiro: vê só o diff;
- não revisa documentação, lockfile, binário, imagem, `dist/`, `build/`, minificado, `.env*`, arquivos de chave, arquivos que contêm chave privada nem os diffs de exemplo do eval;
- não garante nada. o modelo erra para os dois lados, e todo texto postado termina com *revisão automática por IA, pode errar: confira antes de mudar o código*.

## as regras do JUnit continuam sendo a trava

o que dá para provar com teste continua no JUnit e quebra o CI de verdade. por exemplo, `RouteRolesTest.everyRouteWithARecordIdChecksTheLinkWithThePatient` falha quando uma rota com `{patientId}` fica sem bean de vínculo, e `everyRouteDeclaresTheRolesThatCanUseIt` quando uma rota privada fica sem papel. o revisor cobre o que esses testes não enxergam: o bean com o id errado, o `patientId` vindo da query, o dado pessoal num log novo, o workflow perigoso. **quando um achado do revisor puder virar regra fixa, ele vira teste**, e não um aviso que depende do modelo.

## o que vai para a API externa (Anthropic) e o que nunca vai

o modelo roda na API da Anthropic (`claude-opus-5-5`). vai para lá:

- o prompt de sistema fixo, com a rubrica;
- o texto do diff, já filtrado e limpo, com o número de cada linha.

**nunca vai:**

- o repositório inteiro, variáveis de ambiente, o `GITHUB_TOKEN`, título, descrição ou nome da branch do PR;
- arquivos `.env*` (também quando renomeados), `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.ppk`, `*.asc`, `*.gpg`, `id_rsa`, `id_ed25519` e afins, e qualquer arquivo que tenha um bloco de chave privada (PEM, PGP, PuTTY ou `AGE-SECRET-KEY-`), já que o corpo da chave não tem forma fixa para ser mascarado;
- lockfiles (`package-lock.json` e `*.lock`), binários e imagens, `dist/`, `build/`, `*.min.js`;
- `docs/**` e `*.md`;
- linha com mais de 5 mil caracteres (arquivo gerado): só ela fica de fora, com uma marca no lugar para a numeração não mudar, e o arquivo aparece como *revisado em parte* no aviso do PR ou da issue;
- o valor de um segredo: antes do envio, todo trecho com forma de segredo (lista abaixo) é trocado por `[segredo removido]` em **toda** linha enviada, seja nova, removida, de contexto ou do cabeçalho do trecho, e também no caminho do arquivo. o valor achado também sai de qualquer outra linha em que aparece solto, sem o nome sensível do lado; essa linha conta como *revisado em parte* no aviso, para que um segredo falso não sirva para esconder código da IA. se o diff tiver mais de 200 valores com forma de segredo, nada vai para o modelo e todos os arquivos entram no aviso como não revisados. o achado fixo mostra só o tipo, o arquivo e a linha, e só no GitHub;
- dado pessoal no diff, trocado por uma marca como `[cpf removido]` antes do envio:
  - **CPF** com pontuação (`000.000.000-00`), em 11 dígitos seguidos, ou com separador misturado (`000000000-00`, `000.000.000.00`, `000 000 000 00`, `000-000-000-00`, `000_000_000_00`) quando os dígitos fecham a conta do CPF, também colado numa letra (`CPF00000000000`);
  - **CNPJ** com ou sem pontuação (`00.000.000/0000-00`, `00000000/0000-00`, `00_000_000_0000_00` ou 14 dígitos), também colado numa letra;
  - **e-mail**, também escrito como `nome%40dominio`;
  - **telefone com DDD**, com ou sem `+55`, parênteses ou zero antes do DDD, separado por espaço, ponto ou hífen, inclusive com o 9 do celular solto (`(49) 9 9876-5432`), e **celular sem DDD** no formato `9XXXX-XXXX`.

  a troca é feita linha a linha, então a numeração não muda. o resumo do job mostra quantas trocas houve de cada tipo, nunca o valor.

a varredura de segredo é fixa e roda em todas as linhas adicionadas, inclusive nos arquivos que não vão ao modelo. ela procura chave da Anthropic, token do GitHub (`ghp_`, `gho_`, `ghu_`, `ghs_`, `ghr_`, `github_pat_`), token do npm, chave de acesso da AWS (`AKIA` e `ASIA`), bloco de chave privada (PEM, PGP e PuTTY), a chave privada do `age` que abre os backups, JWT e atribuição de valor fixo a um nome sensível (`password`, `senha`, `secret`, `token`, `api_key`, `private_key`, `access_key`, `credential` e variações como `DATABASE_PASSWORD` ou `jwt.secret`). a atribuição conta quando o valor tem 12 caracteres ou mais e aparece:

- entre aspas, com qualquer caractere menos espaço (`password = "..."`, `.password("...")`), ou como senha de app do Gmail (`"abcd efgh ijkl mnop"`);
- como padrão do Spring ou do compose (`${DATABASE_PASSWORD:valor}` e `${X:-valor}`); um valor de mais de 256 caracteres só conta se não tiver `$`, para uma linha montada de propósito não deixar a busca lenta;
- sem aspas, numa linha `NOME=valor` ou `NOME: valor` com nome em maiúsculas (inclusive os terminados em `_KEY`, menos chave pública), e com qualquer nome dentro de arquivo de configuração (`.env*`, `*.yml`, `*.properties`, `docker-compose*`, `*.conf`).

cada acerto vira um achado de severidade alta da regra `segredo-no-codigo`. a mesma forma é usada para trocar o valor antes do envio, então o que a varredura acha nunca vai para a API. o texto que o modelo devolve passa de novo pela troca de segredo e de dado pessoal antes de ir para o GitHub, e qualquer valor que a varredura achou neste diff é apagado da resposta mesmo se aparecer solto.

a chave usada deve ser só do revisor, num workspace só dele no Console da Anthropic, para que o uso e o limite de gasto fiquem separados. a política de retenção de dados é a da organização dona da chave na Anthropic.

## como a action se protege

- **evento `pull_request`, nunca `pull_request_target`.** PR de fork e PR do Dependabot não recebem secret; sem chave o revisor escreve *revisão pulada: sem chave* no resumo e sai com 0.
- **o código do PR não roda.** no PR o checkout é do **commit da base**, com `persist-credentials: false` e só a pasta `.github/security-review`. o diff vem pela API. um PR que não mexe no workflow não consegue trocar o revisor, a rubrica nem as dependências que rodam com a chave. um PR do próprio repositório que mexe em `.github/workflows/security-review.yml` roda o workflow dele, com a chave; veja as limitações.
- **permissões mínimas.** o workflow começa com `permissions: {}` e o job pede só `contents: read`, `pull-requests: write` e `issues: write`. o job do eval pede só `contents: read`.
- **a chave só existe no passo `node review.mjs`.** o `npm ci --ignore-scripts` roda antes, sem chave e sem scripts de instalação.
- **actions presas por SHA.** `actions/checkout` e `actions/setup-node` usam o SHA completo, com a versão no comentário; o Dependabot atualiza os dois, com espera de 7 dias para versão nova. o `setup-node` roda sem cache.
- **nenhum texto do evento entra em `run:`.** título, corpo e branch do PR nunca são interpolados no workflow; o script lê o evento sozinho e confere número e SHAs antes de montar qualquer URL.
- **o SDK está preso.** `@anthropic-ai/sdk` na versão exata `1.3.0`, com `package-lock.json` versionado; é a única dependência direta. a versão só muda por um PR do Dependabot revisado por alguém, que espera 7 dias depois da publicação (30 para versão maior) e passa pelo `npm audit` do CI. os testes do CI usam modelo falso: eles mostram se o SDK novo ainda funciona, não se ele é malicioso, e depois do merge qualquer dependência roda com a chave. por isso, antes de aceitar, vale ler o changelog e conferir se o pacote foi publicado pelo dono de sempre. o endereço da API e o nível de log são fixos no código, então variável de ambiente não desvia a chave nem joga o pedido no log. o token do GitHub só vai para `https://api.github.com`, sem seguir redirecionamento.
- **modelo, esforço e limites são constantes no código** (`lib/config.mjs`), nunca vindos do ambiente nem do evento.
- **o diff é dado, não instrução.** ele vai entre `<diff>` e `</diff>` (uma marca igual dentro do diff é neutralizada), e o prompt de sistema diz que tudo ali foi escrito por um autor desconhecido. texto tentando instruir o revisor vira achado `tentativa-de-instrucao`.
- **a resposta do modelo é tratada como hostil.** ela segue um schema JSON e é conferida de novo na mão: achado em arquivo que não estava no trecho ou com regra desconhecida é descartado, texto é cortado no tamanho, no máximo 15 achados da IA por execução (os segredos da varredura fixa têm um limite próprio de 15), confiança baixa só entra na contagem. linha que não é do lado direito do diff vai para o corpo, nunca para um comentário em linha chutada. todo texto passa por um único limpador antes de chegar ao GitHub: segredo e dado pessoal trocados por marca, HTML e comentário HTML removidos antes de tudo, URL removida (com qualquer esquema, com uma barra só, `//site` e `www.`, mesmo colada num dígito ou sublinhado, como `1https://` e `_www.`), metade solta de emoji trocada pelo caractere de substituição, colchete trocado por `［ ］` e cifrão por `＄` (assim não sobra link, imagem, referência, nota de rodapé nem fórmula), menção (`@pessoa`, `@org/time`) neutralizada, `#123` e `dono/repo#123` neutralizados, barra invertida, crase, asterisco e sublinhado escapados, título, tabela, linha horizontal e cerca de código escapados, e tamanho limitado. o texto em volta é fixo: o título fica num parágrafo e o arquivo em outro, e explicação, sugestão e resumo do modelo vão em citação embaixo de um rótulo fixo, então o modelo não consegue imitar o rodapé nem um achado do bot. o caminho do arquivo vai em código, com segredo, valor de segredo e dado pessoal trocados por marca (um nome de arquivo com CPF ou e-mail não chega ao post nem ao e-mail de notificação), e mesmo assim com `@`, `#`, `://` e `www.` neutralizados. o corte final de um post grande nunca parte um emoji ao meio.
- **sem silêncio quando algo falha.** recusa do modelo (com a categoria), resposta cortada no limite de tokens, conexão que cai no meio da resposta e resposta fora do formato marcam o trecho como *não revisado* no resumo e no aviso do PR ou da issue do push; resposta cortada nunca é lida pela metade. se a API recusar o pedido (por exemplo o beta de fallback ou o formato JSON), o revisor não desliga a proteção para seguir: o job fica vermelho com o motivo.
- **diff grande demais não passa calado.** quando o GitHub não entrega o diff (mais de 300 arquivos ou 20 mil linhas), o revisor usa a lista de arquivos da API, que traz o trecho de cada arquivo (até 3 mil arquivos no PR), roda a varredura de segredo e o modelo sobre ela e **sempre** posta um aviso fixo dizendo que parte da mudança precisa de revisão humana. arquivo que vem sem trecho fica como *não revisado (limite)*.

## custo por execução

preço do `claude-opus-5-5`: US$ 4 por milhão de tokens de entrada e US$ 20 por milhão de saída. o raciocínio do modelo conta como saída e cabe no teto de 32 mil tokens por chamada. o prompt fixo tem uns 4 mil tokens.

| caso | estimativa |
| :--- | ---: |
| PR comum (uma chamada, diff de até ~30 mil caracteres) | US$ 0,10 a 0,35 |
| PR grande (três chamadas cheias) | até ~US$ 2,30 |
| rodada do eval (16 casos) | US$ 2 a 5 |

o custo de verdade de cada execução sai no resumo do job, a partir do `usage` que a API devolve (também o que já foi cobrado quando a conexão cai no meio). a recusa com *fallback* pode rodar em outro modelo com outro preço, então o valor do resumo é estimativa.

## como ligar

1. no Console da Anthropic, criar um workspace só para o revisor (Settings > Workspaces > Create workspace), abrir esse workspace e definir em *Spend limits* um teto mensal baixo (se quiser, também um limite em *Rate limits*). depois criar a chave da API **dentro desse workspace**. o workspace padrão (*Default*) não aceita limite de gasto, então uma chave criada nele usa todo o orçamento da organização;
2. cadastrar a chave como secret do repositório com o nome `ANTHROPIC_API_KEY` (Settings > Secrets and variables > Actions). **atenção:** um secret de repositório pode ser lido por qualquer pessoa com permissão de escrita no repositório (veja as limitações), então só ligue depois de conferir quem tem escrita e trate a chave como exposta a esse grupo.

sem o secret o workflow continua rodando e só escreve *revisão pulada: sem chave*. para desligar de vez, apagar o secret ou desativar o workflow em Actions.

## como rodar o eval

o eval mede o revisor como o estudo de mutação mediu os testes. os casos ficam em `.github/security-review/eval/cases/*.diff`, diffs realistas que aplicam no código atual do 2ag: 11 com um problema plantado (rota sem vínculo, DTO expondo CPF, log com dado do paciente, DTO aceitando `role`, query concatenada, token no `localStorage`, `dangerouslySetInnerHTML` com texto da API, redirecionamento aberto, `pull_request_target` com checkout do PR, segredo na configuração e comentário tentando instruir o revisor) e 5 limpos (duas refatorações sem mudança de comportamento, uma rota nova com as travas certas e a trilha de auditoria, um texto de tela e um ajuste no CI). o gabarito está em `eval/expected.json`: cada caso lista as regras aceitas, e o caso do comentário com instrução exige duas coisas, achar a instrução **e** achar a brecha que ela tenta esconder (o prontuário aberto para o `ADMIN`). o segredo do caso de configuração é gerado na hora, então o repositório não guarda nada com cara de segredo.

o `eval/run-eval.mjs` passa cada caso pelo mesmo caminho do revisor, sem postar nada, e mostra:

- **recall:** em quantos casos plantados a regra esperada apareceu entre os achados que seriam postados, com severidade média ou maior (e quantos o modelo achou sozinho, sem a varredura fixa). achado de confiança baixa, que não seria postado, aparece à parte e não conta;
- **falsos positivos:** achados que seriam postados nos casos limpos;
- **casos não medidos:** recusa, erro temporário, resposta cortada ou fora do formato. esses casos ficam fora das duas contas, e a rodada sai com erro, para que uma rodada travada nunca pareça limpa;
- tokens e custo.

ele precisa da chave e não roda no CI comum. para rodar:

- **no GitHub:** Actions > *revisao de seguranca* > *Run workflow*. o resultado sai no resumo do job;
- **na máquina:** `cd .github/security-review` e `npm ci --ignore-scripts`. crie com um editor o arquivo `.env` nessa pasta com a linha `ANTHROPIC_API_KEY=<a chave>` (o `.gitignore` já ignora `.env`), rode `npm run eval:local` (ou só alguns casos: `node --env-file=.env eval/run-eval.mjs xss-aviso-com-html.diff`) e apague o `.env` no fim. nunca digite a chave direto no terminal: ela fica salva no histórico do shell (`~/.bash_history` ou o `ConsoleHost_history.txt` do PowerShell).

os testes do revisor (`npm test` na mesma pasta) não precisam de chave: o modelo e o GitHub são falsos, e o job `security-review` do `ci.yml` roda esses testes a cada push. os testes de `test/redteam/` repetem os ataques que já foram tentados contra o próprio revisor (vazamento, modelo manipulado, limites e workflow) e entram no mesmo `npm test`.

## limitações

- **quem tem escrita no repositório consegue ler a `ANTHROPIC_API_KEY` a qualquer momento**, e também quem roubar o token de alguém com escrita. basta abrir um PR do próprio repositório que muda `.github/workflows/security-review.yml` (o GitHub roda o workflow do próprio PR no evento `pull_request`, e a chave já está exposta quando o PR abre), mandar uma branch com um workflow próprio que lê o secret, ou usar *Run workflow* na própria branch. a revisão humana do PR **não** protege a chave, porque ela vaza antes de qualquer revisão. as proteções de verdade são: a chave dedicada, num workspace só dela com limite de gasto baixo; trocar a chave sempre que alguém perder o acesso de escrita ou houver suspeita de vazamento; e manter pouca gente com escrita. PR de fork não recebe a chave.
- para fechar mais essa brecha dá para: (a) criar uma regra (*ruleset*) na `main` exigindo PR com aprovação, bloqueando push forçado e exigindo os checks; (b) guardar a chave só num *environment* `revisor-ia` liberado apenas para a `main`, e não como secret do repositório; (c) separar a revisão do PR num workflow sem chave e num segundo workflow `workflow_run`, que sempre vem da `main` e usa o environment. isso muda o desenho atual e é decisão do projeto; sem (a), o (b) protege pouco, porque quem tem escrita pode mandar direto na `main`.
- o modelo vê só o diff: problema que depende de código fora do trecho pode passar, ou vir com confiança baixa.
- diff grande demais: o GitHub não entrega o diff pela API e a revisão usa a lista de arquivos, que para em 3 mil arquivos no PR e no commit único e em 300 no push com vários commits; arquivo sem trecho na lista e o que passa de 3 chamadas ficam como *não revisado (limite)* no resumo e no aviso.
- a troca de dado pessoal é por padrão de texto: um dado fora desses formatos (nome, data de nascimento, endereço, texto clínico livre) passa, assim como telefone fixo sem DDD (`3322-1100`), que ficaria parecido demais com intervalo de ano ou de porta. arquivos `.csv`, `.sql` e `.json` de teste vão ao modelo só com essa troca por padrão. por isso nada de dado real de paciente deve estar no código, nem em teste.
- número de 11 ou 14 dígitos que não é CPF nem CNPJ também é trocado, mesmo colado numa letra (por exemplo dentro de um hash); isso só deixa o diff um pouco menos legível para o modelo.
- um arquivo gerado com linhas enormes ou um falso segredo igual a uma linha de código tiram esse trecho da IA; o aviso no PR mostra o arquivo como *revisado em parte*, e esse trecho precisa de olho humano.
- a varredura fixa não reconhece segredo guardado sob um nome sem `password`, `senha`, `secret`, `token`, `api_key`, `private_key`, `access_key`, `credential` (ou terminado em `_KEY` em maiúsculas), nem valor com menos de 12 caracteres, nem valor sem aspas fora de arquivo de configuração com nome em minúsculas. o valor que ela não reconhece vai ao modelo como está.
- a recusa do modelo por categoria de segurança (por exemplo `cyber`) é rara mas acontece em código de segurança; o *fallback* do servidor tenta outro modelo e, se ele também recusar, o trecho fica como *não revisado*.
- custo e tempo dependem do tamanho do diff; o job para de esperar a IA perto de 12 minutos para ainda escrever o resumo dentro dos 15 do workflow.
