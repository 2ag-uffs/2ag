# identidade visual

O que a marca 2ag define e como isso está aplicado na interface. Os arquivos originais estão em [`docs/identidade-visual/`](./identidade-visual/).

---

## paleta

O manual define um sistema **60‑30‑10**: a primária ocupa cerca de 60% do espaço, a secundária 30%, e as cores de destaque no máximo 10%.

### primária — 60%

| | |
| :--- | :--- |
| **`#006633`** | verde escuro |
| RGB | 0, 102, 51 |
| Pantone | 7733 C |
| Significado | "fundamental, de confiança" |
| Uso | cor dominante, fundos e backgrounds |

### secundária — 30%

| | |
| :--- | :--- |
| **`#77954F`** | verde claro |
| RGB | 119, 149, 79 |
| Pantone | 7490 C |
| Significado | "natural, equilibrado, fitoterápico" |
| Uso | detalhes principais, grafismos |

### destaque — no máximo 10%

Usadas como cor de destaque em **botões e call to action**.

| Hex | Nome | Significado | Pantone |
| :--- | :--- | :--- | :--- |
| `#FFEEDA` | palha | calma, estabilidade e resiliência | Buttercream |
| `#F8BF7D` | pêssego | vitalidade e conforto | 2766 C |
| `#BF663F` | terracota | raízes, orgânico e nostálgico | 7592 C |
| `#1D1B1B` | preto neutro | — | Neutral Black |

---

## subtons

`tons-e-subtons.pdf` traz três tons por família, pensados para web. O PDF é só imagem, então os valores abaixo foram lidos à mão da cartela.

| Família | tom 1 | tom 2 | tom 3 |
| :--- | :--- | :--- | :--- |
| palha | **`#FFEEDA`** | `#FFE6C9` | `#FFD7A8` |
| pêssego | **`#F8BF7D`** | `#F8B466` | `#F8AD57` |
| terracota | **`#BF663F`** | `#BF563F` | `#A44819` |
| verde claro | `#819D5B` | **`#77954F`** | `#637D40` |
| verde escuro | `#006625` | **`#006633`** | `#193E21` |

Em negrito, a cor que o manual apresenta como oficial. Repare que nas três primeiras famílias ela é o tom 1, e nas duas de verde é o tom 2 — não dá para assumir que a cor base é sempre a mais clara da coluna.

Dois detalhes que mudam decisão:

- **`#006625` não é um verde mais claro.** Tem praticamente a mesma luminância de `#006633` (0,096 contra 0,097), é variação de matiz. A família do verde escuro não oferece um tom claro para hover; quem precisa disso usa a família do verde claro.
- **`#193E21` era a `--color-primary` do CSS antigo.** Quem escreveu o CSS original tirou a cor desta cartela, só que pegou o tom 3 em vez do tom 2.

---

## tipografia

| Fonte | Papel | Como o manual descreve |
| :--- | :--- | :--- |
| **Yaldevi** | corpo de texto | "contemporânea, profissional e minimalista", "escreve um texto inteiro sem cansar seus olhos" |
| **Belleza** | complementar, títulos | "elegância, rebusco e delicadeza por meio dos traços" |

As duas estão em `frontend/public/fonts/`: a Yaldevi nos quatro pesos que as telas usam e a Belleza em regular. Os arquivos da Yaldevi são o recorte latino dos originais, cerca de 30 kB cada em vez de 150 kB, porque os originais trazem também o alfabeto cingalês (21/11/2026). A licença da Yaldevi, gravada no próprio arquivo, é a SIL Open Font License 1.1 sem nome de fonte reservado, então o recorte pode manter o nome, e ele mantém o aviso de direitos e o de licença. O arquivo da Belleza que está lá é o recorte latin-ext do Google Fonts (só traz A, Á e o bloco Latin Extended-A), então na tela `h1`, `h2` e `h3` aparecem em Yaldevi até entrar o arquivo latin. Essa troca espera a decisão de quem cuida do projeto.

**Algarismos na fonte do aparelho (decisão da dona do projeto, novembro de 2026).** A Yaldevi só tem algarismos no estilo antigo, em que o zero é baixo como a letra o e os números sobem e descem na linha: *30 mL* parecia *3o mL*, *0,2%* parecia *o,2%* e *09:30* parecia *o9:3o*, e nas tabelas as colunas não alinhavam. Como o prescritor lê doses, horários e pontuações durante a consulta e o paciente lê a dose da receita, os algarismos de 0 a 9 passaram a sair na fonte padrão do aparelho (`system-ui`: Segoe UI no Windows, Roboto no Android, San Francisco no iPhone e no Mac), e o resto do texto, com a vírgula, o `%` e os dois-pontos, continua na Yaldevi. Nenhum arquivo novo é baixado: as quatro faces da Yaldevi em `frontend/src/styles/fonts.css` deixam os algarismos (`U+0030-0039`) fora do `unicode-range`, então o navegador os desenha com a fonte seguinte da lista, que no texto é `system-ui` (`:root`, em `index.css`). Os títulos (`h1`, `h2` e `h3`) e as legendas dos blocos de formulário continuam na Yaldevi, porque a fonte deles espera a decisão sobre a Belleza, mas a lista deles também ganhou o `system-ui` antes do `sans-serif`: títulos trazem datas e horários (*Semana de 23/11/2026*, *Preenchida em 05/11/2026*), e sem ele o algarismo do título saía na Arial no Windows, diferente do algarismo do texto logo abaixo. `frontend/src/styles/numerals.test.js` confere o recorte e a lista de fontes.

---

## logotipo

Três variantes, todas em `docs/identidade-visual/`:

- **horizontal** — a principal
- **vertical** — empilhada, "quando o espaço for limitado ou necessitar de um design mais compacto"
- **símbolo** — os caracteres 2AG posicionados de forma a lembrar uma planta, enfatizando o aspecto fitoterápico

### regras de uso

- **Área de proteção:** 1/4 da altura acima e abaixo, 1/6 da largura nas laterais
- **Redução máxima:** 20 mm
- **Fundo escuro:** obrigatório usar o logotipo negativo
- **Monocromático:** obrigatório remover a sombra colorida do símbolo

### o que não fazer

Sobre fundo sem contraste · esticar horizontalmente · aumentar o tamanho do símbolo · rotacionar na diagonal · aplicar sombras · modificar a altura · deixar fora do enquadramento · aplicar contornos.

---

## o que já está aplicado

### paleta

`frontend/src/styles/colors.css` é a única fonte de cor do frontend. Os quinze tons da cartela estão lá com o nome da família, mais o preto neutro.

Cinco valores são derivação nossa, porque a cartela não tem tom neutro nenhum: `--color-border`, `--color-border-light`, `--color-control-border`, `--color-primary-soft` e `--color-neutral`. Mais as três superfícies, pelo mesmo motivo. Estão marcados como tal no arquivo.

Nenhuma tela tem cor escrita à mão: o bundle de produção só contém valores da paleta. As três exceções são as faixas azul, âmbar e vermelha da escala de dor, que seguem o formulário impresso que a clínica já usa. No papel a faixa moderada vai do verde ao âmbar; na tela ela foi simplificada num âmbar só.

### contraste

| Combinação | Razão | WCAG |
| :--- | :--- | :--- |
| branco sobre `#006633` | 7,1:1 | AAA |
| palha `#FFEEDA` sobre `#006633` | 6,3:1 | AA |
| `#1D1B1B` sobre o fundo `#F4F6F4` | 15,8:1 | AAA |
| `#193E21` sobre o fundo `#F4F6F4` | 11,0:1 | AAA |
| `#006633` sobre o fundo `#F4F6F4` | 6,6:1 | AA |
| branco sobre `#77954F` | 3,4:1 | ❌ reprova |
| `#1D1B1B` sobre `#77954F` | 5,1:1 | AA |
| `#77954F` como texto sobre branco | 3,4:1 | ❌ reprova |
| `#637D40` como texto sobre branco | 4,6:1 | AA |
| `#BF663F` como texto sobre branco | 4,0:1 | ❌ reprova |
| `#A44819` como texto sobre branco | 6,0:1 | AA |

O verde claro e a terracota do manual **reprovam como cor de texto**, nos dois sentidos: branco em cima deles e eles em cima de branco. Isso não contraria o manual — ele descreve a secundária como "detalhes principais, grafismos", não como texto.

A regra que ficou: onde o verde claro é fundo, o texto vai escuro (`--color-text-on-secondary`); onde ele era texto, virou a primária, porque nem o tom 3 (`#637D40`, 4,3:1) passa em cima do fundo da página; e a terracota de texto e de fundo virou o tom 3 (`#A44819`), que é também a `--color-error`. O verde claro e a terracota do tom 1 continuam na paleta, em preenchimento, borda e ícone.

**Borda dos controles (20/11/2026).** A borda do que a pessoa precisa achar para tocar ou preencher (campo de texto, seletor, área de texto, cartão de opção, bolinha e caixa de marcar vazias, nota da régua) e a linha tracejada das faixas no gráfico usam `--color-control-border`: `#6B756F` no tema claro e `#7A887E` no escuro, dois neutros esverdeados da mesma receita dos outros. Passam de 3:1 em todo fundo em que a borda encosta:

| Fundo | claro `#6B756F` | escuro `#7A887E` |
| :--- | :--- | :--- |
| cartão, que é também o lado de dentro do campo e da nota | 4,7:1 | 4,6:1 |
| fundo da página, que é também o do campo travado | 4,3:1 | 5,0:1 |
| bloco dentro do cartão e realce verde | 3,9:1 | 4,1:1 |
| faixa azul da régua de dor | 3,7:1 | 3,8:1 |
| faixa âmbar | 3,9:1 | 3,5:1 |
| faixa vermelha | 3,2:1 | 3,8:1 |

Antes essas bordas usavam `--color-border`, que dá 1,3:1 sobre o cartão nos dois temas. `--color-border` e `--color-chart-grid` não mudaram de valor: continuam no contorno dos cartões e na grade do gráfico, que não precisam ser achados. Como a borda em repouso ficou quase tão escura quanto a de erro, o campo recusado passou a ter a borda dobrada (2 px), além da cor, do halo e da mensagem embaixo; a opção escolhida já tinha a borda dobrada e o check. `frontend/src/styles/control-border.test.js` refaz a conta nos dois temas.

### escala de letras

Os tamanhos de letra ficam em `frontend/src/styles/tokens.css`: 12 px (`--text-xs`), 14 px (`--text-sm`), 16 px (`--text-md`, o corpo do texto) e os tamanhos dos títulos. A regra da escala (decisão da dona do projeto, novembro de 2026): o que a pessoa precisa ler para agir nunca fica menor que o corpo. Por isso a mensagem de erro do campo, a dica embaixo do campo, o nome do campo nos cartões de receita, consulta, anamnese e do Perfil e, no celular, o texto do botão pequeno passaram de 14 para 16 px. Vale igual para o paciente, o prescritor e a administração, como um padrão só. O rótulo em cima do campo do formulário continua em 14 px e em negrito, e os tamanhos de 14 e 12 px ficam para o que é só apoio, como a data, a contagem e a etiqueta de situação. No computador o botão pequeno continua com a letra de 14 px. Medido no Chrome sem janela em 320 px, nos três perfis: nenhuma tela rola de lado. `frontend/src/styles/reading-size.test.js` lista as regras que seguem a escala.

### superfícies

O fundo das telas é um neutro frio levemente esverdeado (`#F4F6F4`), o cartão é branco e o bloco dentro do cartão é `#E9EDEA`. Nenhum dos três sai da cartela, porque ela não tem tom neutro.

A primeira versão usava a palha `#FFEEDA` como fundo de tudo. Na tela pesou: fundo creme, cabeçalho verde e caixa de destaque em pêssego são três coisas quentes empilhadas, e as faixas azul, amarela e vermelha da escala de dor brigavam com elas. Palha, pêssego e terracota voltaram para o papel que o manual dá a elas — destaque em até 10%: badge, estado, erro e faixa de escala.

A terracota de erro fica para o que a pessoa precisa corrigir. Uma busca que não acha ninguém não é erro de quem digitou: na tela de pacientes da administração, o e-mail sem conta aparece no aviso neutro de lista vazia, com o ícone de lupa, como na lista de pacientes do prescritor, e o campo só fica terracota quando o e-mail está mal escrito (decisão da dona do projeto, novembro de 2026).

Realce dentro do cartão (a caixa de pontuação das escalas, a linha selecionada) usa `--color-primary-soft`, um verde bem diluído, em vez do pêssego.

O pêssego marca o prazo que passou: um traço de 4 px na esquerda do cartão atrasado em Minhas avaliações e, desde novembro de 2026, também da linha atrasada no *Início* do paciente. É um dos destaques dos 10%, e o texto *O prazo era* diz o atraso, então o traço nunca é o único sinal. No mesmo *Início*, o verde cheio fica para um botão só, o **Responder** mais urgente; os outros **Responder** e o *Agendar consulta* são de contorno (decisão da dona do projeto, novembro de 2026).

O campo de formulário também perdeu a borda verde e o texto verde; ficou com borda neutra e o verde só aparece no foco. Desde 20/11/2026 essa borda neutra é a `--color-control-border`, mais escura que a do cartão (veja *contraste*). Com verde no cabeçalho, no botão, no título e em toda borda de campo, a tela virava verde de ponta a ponta.

No cabeçalho do celular do paciente, o *Perfil* é um botão com o nome escrito na cor do texto da moldura verde (`--color-on-brand-surface`), com borda branca a 70% (mais de 3:1 sobre o verde nos dois temas) e pelo menos 44 px de toque; a letra é a do corpo do texto (16 px), como a dos botões pequenos no celular, e cresce com a letra da pessoa; aberto, ganha fundo e sublinhado, para não depender só da cor. Com ele a logo do cabeçalho sai do meio e fica à esquerda, depois do *Voltar* (decisão da dona do projeto, novembro de 2026).

### logotipo

Os três SVGs oficiais substituíram os exports antigos em `frontend/public/images/`. As proporções do símbolo e da versão vertical são idênticas às anteriores; o horizontal ficou 6% mais largo na mesma altura, e como o cabeçalho dimensiona por altura, nenhuma tela precisou mudar.

O manual exige logotipo negativo sobre fundo escuro. Como o SVG oficial é `#006633` sólido e o cabeçalho também, existem três variantes em palha, com o mesmo desenho do arquivo oficial e só a cor trocada: `logotipo-icon-claro.svg` e `logotipo-vertical-claro.svg`, usadas no cabeçalho e na arte lateral do login e do cadastro, e `logotipo-horizontal-claro.svg`, do tema escuro das telas de entrada e das páginas de status. A versão verde continua no fundo branco.

O ícone da aba segue o tema do navegador: o `index.html` aponta para `logotipo-icon-claro.svg` quando o navegador está no escuro e para `logotipo-icon.svg` no claro.

O ícone da tela inicial do celular (21/11/2026) é o símbolo verde de `logotipo-icon.svg` sobre um quadrado sólido da cor do fundo das telas (`--color-background`, `#F4F6F4`), gerado uma vez a partir do próprio SVG, sem desenho novo: `logotipo-icon-192.png` e `logotipo-icon-512.png` no manifesto (`frontend/public/manifest.webmanifest`), `logotipo-icon-maskable-512.png` com o símbolo dentro dos 80% do centro, a área que o Android nunca corta quando recorta o ícone em círculo ou gota, e `apple-touch-icon.png` (180 px) para o iPhone, com fundo sólido porque ele pinta de preto a parte transparente. O nome embaixo do ícone é só *2AG*; no manifesto, `theme_color` é o verde do cabeçalho e `background_color` é o fundo das telas.

A barra do navegador no celular (`<meta name="theme-color">` do `index.html`) tem a cor do cabeçalho de cada tema, a `--color-brand-surface`: `#006633` no claro e `#16281D` no escuro. Até 21/11/2026 o escuro usava o fundo das telas, `#0F1411`, e a barra se separava do cabeçalho. Sem tema escolhido, as duas metas seguem o aparelho pelo `media`; com o tema escolhido no botão, o `public/theme-boot.js` e o `utils/theme.js` pintam as duas com a cor do tema escolhido.

Nas telas de entrada e nas páginas de status (página não encontrada, sem conexão) o logotipo horizontal fica sobre o fundo da tela, que no tema escuro é escuro. Elas trazem as duas imagens, a verde com a classe `logo-on-light` e a de palha com `logo-on-dark`, e `frontend/src/styles/colors.css` mostra só uma: a de palha no tema escuro (automático ou escolhido no botão) e a verde no claro e na impressão. A imagem escondida não é lida pelo leitor de tela, então ele ouve um logotipo só.

### gráfico de progresso

O gráfico de evolução é um SVG feito à mão, e as cores vêm direto dos tokens do CSS por `var()` (`--color-chart-line`, `--color-chart-grid`, `--color-control-border` nas linhas das faixas, `--color-secondary` e `--color-error`, em `line-chart.module.css`). Por isso o tema escuro vale no gráfico sem nenhum JavaScript.

---

## o que falta

**Confirmar a leitura do 60‑30‑10**, abaixo.

---

## uma decisão de design que vale confirmar

O sistema 60‑30‑10 diz que a **primária ocupa 60% e serve de fundo**. Isso funciona em material impresso, mas aplicar literalmente aqui significaria **60% da tela em verde escuro `#006633`**.

O 2ag é um sistema clínico: o paciente passa minutos preenchendo formulário de 15 itens, e a prescritora lê prontuário e gráfico de evolução. Fundo escuro em 60% da tela prejudica leitura longa e contraste de texto.

A leitura aplicada foi: a **primária domina a identidade** (cabeçalho, barra de navegação, botões principais, arte do login, elementos de marca), e o corpo de leitura usa um neutro quase sem cor, derivado da própria primária.

Ou seja: nenhuma cor da marca serve de fundo de tela. A primária porque escureceria demais, e a palha porque já tentamos e ficou pesada.

**Vale confirmar com quem fez o manual.** É a mesma natureza das perguntas para a clínica: a resposta muda o resultado e não está no meu alcance decidir. Se a resposta for "aplique literal", o que muda é só `--color-background` e `--color-surface-alt`.

---

## tema escuro

Aplicado em 20/09/2026. Segue a preferência do aparelho e tem um botão no menu — a escolha da pessoa vence a do sistema e fica gravada no navegador.

**A cartela do manual não mudou.** Os quinze tons das cinco famílias continuam com os mesmos valores. O que o tema escuro troca é o **papel** que cada tom ocupa:

| No claro | No escuro | Por quê |
| :--- | :--- | :--- |
| Primária `#006633` é fundo de botão, com texto branco | A ação passa a `#3fa96a`, com texto escuro | No escuro a primária não carrega texto branco com contraste suficiente |
| Fundo da página é um neutro quase branco | `#0f1411`, verde quase sem saturação | Mesma receita dos neutros claros; preto puro não pertence à paleta |
| Palha é fundo do selo, com texto escuro | Fundo palha escurecido, texto palha claro | Palha, pêssego e terracota continuam nos 10% de destaque, invertidos |
| Menu e arte do login são verde escuro | Continuam escuros, `#16281d` | Com a primária clareada, a navegação viraria um bloco brilhante |

Três papéis novos nasceram daí, porque a família sozinha não diz o que a cor faz:

- `--color-brand-surface` e `--color-on-brand-surface`: o verde como **moldura** (menu lateral, cabeçalho do celular, arte das telas de entrada), que precisa escurecer no tema escuro enquanto a primária clareia
- `--color-highlight-surface`, `--color-highlight-border` e `--color-highlight-text`: o bloco de destaque em palha, que inverte

A impressão sai **sempre clara**, independentemente do tema: é peça de prontuário.

**O que vale confirmar com quem fez o manual:** o verde `#3fa96a` como cor de ação é a única cor que não está na cartela. É a mesma matiz da primária, dois tons acima, e existe porque o contraste exige.
