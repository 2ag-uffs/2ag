# identidade visual

O que a marca 2ag define e o que ainda falta aplicar na interface. Os arquivos originais estão em [`docs/identidade-visual/`](./identidade-visual/).

**Ainda não foi implementado.** Este documento é o registro do que precisa ser feito.

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

A Yaldevi **já está no projeto** (`frontend/public/fonts/`, seis pesos). A Belleza não.

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

## o que diverge hoje

### a cor primária da interface não é a da marca

```css
/* frontend/src/styles/colors.css */
--color-primary: #193e21;   /* a marca é #006633 */
```

E os **logotipos em uso já são `#006633`** — então hoje o logo e a interface usam verdes diferentes na mesma tela.

### o resto da paleta

| Token atual | Valor | Situação |
| :--- | :--- | :--- |
| `--color-primary` | `#193e21` | ❌ deveria ser `#006633` |
| `--color-accent-agree` | `#77954f` | ✅ é a secundária da marca |
| `--color-accent-disagree` | `#bf663f` | ✅ é a terracota |
| `--color-background` | `#f5f8f0` | ❌ fora da paleta (`#FFEEDA` é a palha da marca) |
| `--color-neutral` | `#adb5bd` | ❌ cinza genérico, fora da paleta |
| — | `#F8BF7D` | ❌ pêssego não existe no CSS |
| — | `#1D1B1B` | ❌ preto neutro não existe no CSS |

### os SVGs em uso são exports antigos

Os de `frontend/public/images/` têm `clip-path`, `viewBox` maior e proporção diferente dos oficiais. A cor está certa, o resto não. Trocar muda a proporção (3,87 para 4,10 no horizontal), então mexe no layout de todas as telas que exibem o logo.

---

## uma decisão de design antes de aplicar

O sistema 60‑30‑10 diz que a **primária ocupa 60% e serve de fundo**. Isso funciona em material impresso, mas aplicar literalmente aqui significaria **60% da tela em verde escuro `#006633`**.

O 2ag é um sistema clínico: o paciente passa minutos preenchendo formulário de 15 itens, e a prescritora lê prontuário e gráfico de evolução. Fundo escuro em 60% da tela prejudica leitura longa e contraste de texto.

A leitura que faz sentido para interface é: a **primária domina a identidade** (cabeçalho, barra de navegação, botões principais, elementos de marca), e o corpo de leitura usa o neutro claro da paleta — a palha `#FFEEDA`, que já é cor da marca.

**Vale confirmar com quem fez o manual** antes de aplicar. É a mesma natureza das perguntas de [`perguntas-para-a-clinica.md`](./extensao/perguntas-para-a-clinica.md): a resposta muda o resultado e não está no meu alcance decidir.

---

## ordem sugerida quando for implementar

1. Trocar `--color-primary` para `#006633` e conferir contraste do texto sobre ele (WCAG AA pede 4.5:1 para texto normal)
2. Acrescentar os tokens que faltam: pêssego, palha e preto neutro
3. Extrair os subtons do PDF e transformar em tokens — são eles que resolvem estados como hover, desabilitado e faixas de gráfico
4. Adicionar a Belleza para títulos
5. Trocar os SVGs pelos oficiais e ajustar o dimensionamento nas telas que exibem o logo
6. Revisar a tela de progresso: as cores do gráfico devem sair da paleta, não do padrão da biblioteca
