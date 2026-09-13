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

Quatro valores são derivação nossa, porque a cartela não tem tom neutro: `--color-border`, `--color-border-light`, `--color-primary-soft` (realce de seleção) e `--color-neutral`. Estão marcados como tal no arquivo.

Nenhuma tela tem cor escrita à mão: o bundle de produção só contém valores da paleta. As três exceções são as faixas azul, amarela e vermelha da escala de dor, que copiam o formulário impresso que a clínica já usa.

### contraste

| Combinação | Razão | WCAG |
| :--- | :--- | :--- |
| branco sobre `#006633` | 7,1:1 | AAA |
| palha `#FFEEDA` sobre `#006633` | 6,3:1 | AA |
| `#1D1B1B` sobre a palha `#FFEEDA` | 15,1:1 | AAA |
| `#006633` sobre a palha `#FFEEDA` | 6,3:1 | AA |
| branco sobre `#77954F` | 3,4:1 | ❌ reprova |
| `#1D1B1B` sobre `#77954F` | 5,1:1 | AA |
| `#77954F` como texto sobre branco | 3,4:1 | ❌ reprova |
| `#637D40` como texto sobre branco | 4,6:1 | AA |
| `#BF663F` como texto sobre branco | 4,0:1 | ❌ reprova |
| `#A44819` como texto sobre branco | 6,0:1 | AA |

O verde claro e a terracota do manual **reprovam como cor de texto**, nos dois sentidos: branco em cima deles e eles em cima de branco. Isso não contraria o manual — ele descreve a secundária como "detalhes principais, grafismos", não como texto.

A regra que ficou: onde o verde claro é fundo, o texto vai escuro (`--color-text-on-secondary`); onde ele era texto, virou o tom 3 da família (`#637D40`); e a terracota de texto e de fundo virou o tom 3 (`#A44819`), que é também a `--color-error`. Os tons 1 continuam na paleta, em preenchimento, borda e ícone.

### superfícies

O fundo das telas é a palha `#FFEEDA` e o cartão é branco. A separação entre os dois é fraca (1,14:1), então cartão continua dependendo da sombra que já tinha. `--color-surface-alt` é o tom 2 da palha, para bloco destacado dentro do cartão — é o fundo da caixa de pontuação das escalas.

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

A leitura aplicada foi: a **primária domina a identidade** (cabeçalho, barra de navegação, botões principais, arte do login, elementos de marca), e o corpo de leitura usa a palha `#FFEEDA`.

Vale registrar que isso também estica o manual do outro lado: a palha está listada entre as cores de destaque, limitadas a 10%, e aqui ela é o fundo de todas as telas.

**Vale confirmar com quem fez o manual.** É a mesma natureza das perguntas de [`perguntas-para-a-clinica.md`](./extensao/perguntas-para-a-clinica.md): a resposta muda o resultado e não está no meu alcance decidir. Se a resposta for "aplique literal", o que muda é só `--color-background` e `--color-surface`.
