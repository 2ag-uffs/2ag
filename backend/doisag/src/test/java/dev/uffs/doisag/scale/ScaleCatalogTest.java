package dev.uffs.doisag.scale;

import dev.uffs.doisag.enums.ScaleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// a pergunta q junta varios itens no papel (o section do item) so serve pra tela, o escore n olha pra ela
//
// n sobe o spring de proposito, como o ScaleScorerTest
class ScaleCatalogTest {

    private final ScaleCatalog catalog = new ScaleCatalog();

    // o pittsburgh, a dor e o tea juntam itens embaixo de uma pergunta no papel
    @Test
    void soAsEscalasQueJuntamItensNoPapelTemAPerguntaDaParte() {
        Set<ScaleType> withSection = catalog.all().stream()
                .filter(definition -> definition.items().stream().anyMatch(item -> item.section() != null))
                .map(ScaleDefinition::type)
                .collect(Collectors.toSet());

        assertThat(withSection).containsExactlyInAnyOrder(ScaleType.ESCALA_PITTSBURGH, ScaleType.REGISTRO_DOR,
                ScaleType.REGISTRO_TEA);
    }

    // a pergunta inteira do papel (question) e a frase de antes do item (intro) so entraram no pittsburgh, na dor
    // e no tea. o hamilton, o MEEM, a ficha semanal e o diario do sono ficaram como estavam
    @Test
    void soOPittsburghADorEOTeaTemAPerguntaInteiraDoPapel() {
        Set<ScaleType> withQuestion = catalog.all().stream()
                .filter(definition -> definition.items().stream()
                        .anyMatch(item -> item.question() != null || item.intro() != null))
                .map(ScaleDefinition::type)
                .collect(Collectors.toSet());

        assertThat(withQuestion).containsExactlyInAnyOrder(ScaleType.ESCALA_PITTSBURGH, ScaleType.REGISTRO_DOR,
                ScaleType.REGISTRO_TEA);
    }

    // a tela mostra a pergunta no lugar do nome curto, entao a msm frase n pode vir tbm na ajuda nem no titulo
    // da parte, senao o paciente le duas vezes
    @Test
    void aPerguntaDoPapelNaoRepeteAAjudaNemOTituloDaParte() {
        for (ScaleDefinition definition : catalog.all()) {
            for (ScaleItem item : definition.items()) {
                if (item.question() == null) {
                    continue;
                }
                assertThat(item.question()).as(item.key()).isNotEqualTo(item.label())
                        .isNotEqualTo(item.help()).isNotEqualTo(item.section());
            }
        }
    }

    // a tela mostra a pergunta uma vez em cima dos itens dela, entao eles vem um atras do outro
    @Test
    void osItensDaMesmaPerguntaVemUmAtrasDoOutro() {
        for (ScaleDefinition definition : catalog.all()) {
            List<String> sectionsInOrder = new ArrayList<>();
            String lastSection = null;
            for (ScaleItem item : definition.items()) {
                if (item.section() != null && !item.section().equals(lastSection)) {
                    assertThat(sectionsInOrder).as(definition.title()).doesNotContain(item.section());
                    sectionsInOrder.add(item.section());
                }
                lastSection = item.section();
            }
        }
    }
}
