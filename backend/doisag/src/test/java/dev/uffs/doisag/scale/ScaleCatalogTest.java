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

    // no papel a pergunta 7 acaba em "para lhe ajudar", a 8 vem sem o "com" e a 7, a 8 e a 9 n fecham com
    // interrogacao. a tela corrige e fica diferente do papel de proposito (decisao da dona, novembro de 2026)
    // o nome curto e as opcoes continuam os msm
    @Test
    void asPerguntas7A9DoPittsburghVemComOPortuguesCorrigido() {
        ScaleDefinition pittsburgh = catalog.definitionOf(ScaleType.ESCALA_PITTSBURGH);

        ScaleItem question7 = pittsburgh.itemOf("freqMedicacao").orElseThrow();
        assertThat(question7.question()).isEqualTo("Durante o último mês, com que frequência você tomou "
                + "medicamento (prescrito ou “por conta própria”) para lhe ajudar a dormir?");
        assertThat(question7.label()).isEqualTo("Tomou medicamento para ajudar a dormir");

        ScaleItem question8 = pittsburgh.itemOf("freqDificuldadeAcordado").orElseThrow();
        assertThat(question8.question()).isEqualTo("No último mês, com que frequência você teve dificuldade para "
                + "ficar acordado enquanto dirigia, comia ou participava de uma atividade social (festa, reunião "
                + "de amigos)?");

        ScaleItem question9 = pittsburgh.itemOf("dificuldadeEntusiasmo").orElseThrow();
        assertThat(question9.question()).isEqualTo("Durante o último mês, quão problemático foi pra você manter o "
                + "entusiasmo (ânimo) para fazer as coisas (suas atividades habituais)?");

        assertThat(question7.options()).isEqualTo(question8.options());
        assertThat(question7.options()).extracting("label").containsExactly("Nenhuma no último mês",
                "Menos de uma vez por semana", "Uma ou duas vezes por semana", "Três ou mais vezes na semana");
        assertThat(question9.options()).extracting("label").containsExactly("Nenhuma dificuldade",
                "Um problema leve", "Um problema razoável", "Um grande problema");
    }
}
