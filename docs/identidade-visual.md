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

Os **subtons** (variações tonais das cores principais) estão em `tons-e-subtons.pdf` e são pensados justamente para web. Só existem como imagem no PDF — alguém precisa extrair os valores.

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

`frontend/src/styles/colors.css` é a única fonte de cor do frontend. As cinco cores da marca estão lá com o nome que o manual usa, mais os subtons derivados delas para estados como hover, borda e faixa de gráfico.

Nenhuma tela tem cor escrita à mão: o bundle de produção só contém valores da paleta. As três exceções são as faixas azul, amarela e vermelha da escala de dor, que copiam o formulário impresso que a clínica já usa.

### contraste

| Combinação | Razão | WCAG |
| :--- | :--- | :--- |
| branco sobre `#006633` | 7,1:1 | AAA |
| palha `#FFEEDA` sobre `#006633` | 6,3:1 | AA |
| `#1D1B1B` sobre o fundo `#FDF8F2` | 16,2:1 | AAA |
| `#006633` sobre o fundo `#FDF8F2` | 6,7:1 | AA |
| branco sobre `#77954F` | 3,4:1 | ❌ reprova |
| `#1D1B1B` sobre `#77954F` | 5,1:1 | AA |
| `#9C4F2F` sobre branco | 5,9:1 | AA |
| `#BF663F` sobre branco | 4,0:1 | ❌ reprova |

Por isso o texto sobre a secundária vai escuro (`--color-text-on-secondary`) e a terracota de erro é uma versão escurecida da cor da marca (`--color-error`), não a `#BF663F` original. A `#BF663F` continua na paleta, mas em preenchimento e ícone, não em texto pequeno.

### logotipo

Os três SVGs oficiais substituíram os exports antigos em `frontend/public/images/`. As proporções do símbolo e da versão vertical são idênticas às anteriores; o horizontal ficou 6% mais largo na mesma altura, e como o cabeçalho dimensiona por altura, nenhuma tela precisou mudar.

O manual exige logotipo negativo sobre fundo escuro. Como o SVG oficial é `#006633` sólido e o cabeçalho também, existem duas variantes em palha (`logotipo-icon-claro.svg` e `logotipo-vertical-claro.svg`) usadas no cabeçalho e na arte lateral do login e do cadastro. A versão verde continua no fundo branco.

### gráfico de progresso

A tela de progresso lê `--color-chart-line` e `--color-chart-grid` do CSS em tempo de execução, porque o Recharts precisa da cor como valor e não aceita `var()`.

---

## o que falta

1. **Subtons do manual.** `tons-e-subtons.pdf` só existe como imagem, sem texto extraível. Os subtons que estão no CSS hoje foram derivados por conta própria a partir das cores principais. Quando alguém extrair os valores oficiais, é só trocar.
2. **Confirmar a leitura do 60‑30‑10** (abaixo).

---

## uma decisão de design que vale confirmar

O sistema 60‑30‑10 diz que a **primária ocupa 60% e serve de fundo**. Isso funciona em material impresso, mas aplicar literalmente aqui significaria **60% da tela em verde escuro `#006633`**.

O 2ag é um sistema clínico: o paciente passa minutos preenchendo formulário de 15 itens, e a prescritora lê prontuário e gráfico de evolução. Fundo escuro em 60% da tela prejudica leitura longa e contraste de texto.

A leitura aplicada foi: a **primária domina a identidade** (cabeçalho, barra de navegação, botões principais, arte do login, elementos de marca), e o corpo de leitura usa um neutro derivado da palha (`#FDF8F2`), que é o que o manual manda fazer com os subtons para web.

**Vale confirmar com quem fez o manual.** É a mesma natureza das perguntas de [`perguntas-para-a-clinica.md`](./extensao/perguntas-para-a-clinica.md): a resposta muda o resultado e não está no meu alcance decidir. Se a resposta for "aplique literal", o que muda é só `--color-background` e `--color-surface`.
