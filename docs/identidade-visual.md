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

As duas estão em `frontend/public/fonts/`: a Yaldevi em seis pesos e a Belleza em regular. `frontend/src/styles/fonts.css` aplica a Belleza em `h1`, `h2` e `h3`, e o corpo herda a Yaldevi.

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

Quatro valores são derivação nossa, porque a cartela não tem tom neutro nenhum: `--color-border`, `--color-border-light`, `--color-primary-soft` e `--color-neutral`. Mais as três superfícies, pelo mesmo motivo. Estão marcados como tal no arquivo.

Nenhuma tela tem cor escrita à mão: o bundle de produção só contém valores da paleta. As três exceções são as faixas azul, amarela e vermelha da escala de dor, que copiam o formulário impresso que a clínica já usa.

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

### superfícies

O fundo das telas é um neutro frio levemente esverdeado (`#F4F6F4`), o cartão é branco e o bloco dentro do cartão é `#E9EDEA`. Nenhum dos três sai da cartela, porque ela não tem tom neutro.

A primeira versão usava a palha `#FFEEDA` como fundo de tudo. Na tela pesou: fundo creme, cabeçalho verde e caixa de destaque em pêssego são três coisas quentes empilhadas, e as faixas azul, amarela e vermelha da escala de dor brigavam com elas. Palha, pêssego e terracota voltaram para o papel que o manual dá a elas — destaque em até 10%: badge, estado, erro e faixa de escala.

Realce dentro do cartão (a caixa de pontuação das escalas, a linha selecionada) usa `--color-primary-soft`, um verde bem diluído, em vez do pêssego.

O campo de formulário também perdeu a borda verde e o texto verde; ficou com borda neutra e o verde só aparece no foco. Com verde no cabeçalho, no botão, no título e em toda borda de campo, a tela virava verde de ponta a ponta.

### logotipo

Os três SVGs oficiais substituíram os exports antigos em `frontend/public/images/`. As proporções do símbolo e da versão vertical são idênticas às anteriores; o horizontal ficou 6% mais largo na mesma altura, e como o cabeçalho dimensiona por altura, nenhuma tela precisou mudar.

O manual exige logotipo negativo sobre fundo escuro. Como o SVG oficial é `#006633` sólido e o cabeçalho também, existem duas variantes em palha (`logotipo-icon-claro.svg` e `logotipo-vertical-claro.svg`) usadas no cabeçalho e na arte lateral do login e do cadastro. A versão verde continua no fundo branco.

### gráfico de progresso

A tela de progresso lê `--color-chart-line` e `--color-chart-grid` do CSS em tempo de execução, porque o Recharts precisa da cor como valor e não aceita `var()`.

---

## o que falta

**Confirmar a leitura do 60‑30‑10**, abaixo.

---

## uma decisão de design que vale confirmar

O sistema 60‑30‑10 diz que a **primária ocupa 60% e serve de fundo**. Isso funciona em material impresso, mas aplicar literalmente aqui significaria **60% da tela em verde escuro `#006633`**.

O 2ag é um sistema clínico: o paciente passa minutos preenchendo formulário de 15 itens, e a prescritora lê prontuário e gráfico de evolução. Fundo escuro em 60% da tela prejudica leitura longa e contraste de texto.

A leitura aplicada foi: a **primária domina a identidade** (cabeçalho, barra de navegação, botões principais, arte do login, elementos de marca), e o corpo de leitura usa um neutro quase sem cor, derivado da própria primária.

Ou seja: nenhuma cor da marca serve de fundo de tela. A primária porque escureceria demais, e a palha porque já tentamos e ficou pesada.

**Vale confirmar com quem fez o manual.** É a mesma natureza das perguntas de [`perguntas-para-a-clinica.md`](./extensao/perguntas-para-a-clinica.md): a resposta muda o resultado e não está no meu alcance decidir. Se a resposta for "aplique literal", o que muda é só `--color-background` e `--color-surface-alt`.

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
