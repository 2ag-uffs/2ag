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

    // so o pittsburgh e o tea tem a pergunta da parte. a dor tbm junta itens no papel, mas a pergunta
    // dela ja eh a ajuda de cada item e ficava repetida
    @Test
    void soAsEscalasQueJuntamItensNoPapelTemAPerguntaDaParte() {
        Set<ScaleType> withSection = catalog.all().stream()
                .filter(definition -> definition.items().stream().anyMatch(item -> item.section() != null))
                .map(ScaleDefinition::type)
                .collect(Collectors.toSet());

        assertThat(withSection).containsExactlyInAnyOrder(ScaleType.ESCALA_PITTSBURGH, ScaleType.REGISTRO_TEA);
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
