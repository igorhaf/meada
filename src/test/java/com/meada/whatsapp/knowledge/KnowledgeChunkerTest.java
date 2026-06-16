package com.meada.whatsapp.knowledge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste unitário PURO do {@link KnowledgeChunker} (sem Spring). chunkSize=800, overlap=100
 * (mesmos defaults da config).
 */
class KnowledgeChunkerTest {

    private final KnowledgeChunker chunker = new KnowledgeChunker(800, 100);

    @Test
    @DisplayName("texto vazio/branco → lista vazia")
    void emptyText() {
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   \n\n  ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    @DisplayName("parágrafo curto → 1 chunk com o texto")
    void shortParagraph() {
        List<String> chunks = chunker.chunk("Uma resposta curta sobre horário de atendimento.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("horário de atendimento");
    }

    @Test
    @DisplayName("vários parágrafos curtos → agrupados até ~chunkSize")
    void shortParagraphsGrouped() {
        // 10 parágrafos de ~50 chars cada (~500 total) cabem em 1 chunk (<800).
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("Paragrafo numero ").append(i).append(" com algum conteudo.\n\n");
        }
        List<String> chunks = chunker.chunk(sb.toString());
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Paragrafo numero 0").contains("Paragrafo numero 9");
    }

    @Test
    @DisplayName("texto longo → múltiplos chunks, cada um <= chunkSize+margem")
    void longTextMultipleChunks() {
        // ~30 parágrafos de 100 chars = ~3000 chars → vários chunks.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("Este e o paragrafo ").append(i)
              .append(" com um conteudo razoavelmente longo para forcar varias quebras de chunk no teste. ")
              .append("\n\n");
        }
        List<String> chunks = chunker.chunk(sb.toString());
        assertThat(chunks.size()).isGreaterThan(1);
        // cada chunk respeita o teto (com folga do overlap prefixado).
        for (String c : chunks) {
            assertThat(c.length()).isLessThanOrEqualTo(800 + 100);
        }
    }

    @Test
    @DisplayName("parágrafo único gigante → sub-quebra por sentença")
    void hugeParagraphSplitBySentence() {
        // 1 parágrafo (sem \n\n) com muitas sentenças, total > 800 chars.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("Esta e a sentenca numero ").append(i).append(". ");
        }
        List<String> chunks = chunker.chunk(sb.toString());
        assertThat(chunks.size()).isGreaterThan(1);
        for (String c : chunks) {
            assertThat(c.length()).isLessThanOrEqualTo(800 + 100);
        }
    }

    @Test
    @DisplayName("overlap: o início de um chunk repete a cauda do anterior")
    void overlapBetweenChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("Bloco de texto ").append(i)
              .append(" suficientemente longo para gerar mais de um chunk distinto aqui. ")
              .append("\n\n");
        }
        List<String> chunks = chunker.chunk(sb.toString());
        assertThat(chunks.size()).isGreaterThan(1);
        // O 2º chunk começa com parte do fim do 1º (overlap > 0). Verifica que há
        // sobreposição textual: algum sufixo do chunk[0] aparece no começo do chunk[1].
        String tail = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 100));
        // pelo menos um pedaço de 20 chars da cauda aparece no início do próximo
        String head = chunks.get(1).substring(0, Math.min(120, chunks.get(1).length()));
        boolean overlaps = false;
        for (int w = 20; w <= tail.length(); w++) {
            if (head.contains(tail.substring(tail.length() - w))) {
                overlaps = true;
                break;
            }
        }
        assertThat(overlaps).as("chunk[1] deve conter overlap da cauda de chunk[0]").isTrue();
    }
}
