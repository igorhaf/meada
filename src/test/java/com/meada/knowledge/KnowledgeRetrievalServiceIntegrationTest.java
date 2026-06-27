package com.meada.knowledge;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do retrieval semântico (pgvector real). Os embeddings são vetores
 * canônicos controlados (one-hot em posições distintas) — assim a similaridade cosine é
 * determinística: a query com one-hot na posição P casa exatamente o chunk com one-hot em
 * P (similarity 1.0) e é ortogonal aos demais (similarity 0, abaixo do threshold 0.65).
 *
 * <p>O fake EmbeddingProvider devolve o vetor configurado em {@link FakeQueryEmbedding}
 * para a próxima chamada de embed(QUERY).
 */
@Import(KnowledgeRetrievalServiceIntegrationTest.TestConfig.class)
class KnowledgeRetrievalServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb");

    @Autowired
    private KnowledgeRetrievalService retrievalService;
    @Autowired
    private FakeQueryEmbedding fakeQueryEmbedding;

    /** Vetor one-hot 384-dim com 1.0 na posição p. */
    private static float[] oneHot(int p) {
        float[] v = new float[384];
        v[p] = 1.0f;
        return v;
    }

    private static String literal(float[] v) {
        return VectorLiterals.toVectorLiteral(v);
    }

    private UUID seedDocument(UUID company, String title, boolean active) {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?) "
            + "on conflict (id) do nothing", company, "Co " + company, "co-" + company);
        UUID docId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into knowledge_documents (id, company_id, title, storage_path, status, active) "
                + "values (?, ?, ?, ?, 'ready', ?)",
            docId, company, title, "pending:x.pdf", active);
        return docId;
    }

    private void seedChunk(UUID docId, UUID company, int index, String content, float[] emb) {
        jdbcTemplate.update(
            "insert into knowledge_chunks (document_id, company_id, chunk_index, content, embedding) "
                + "values (?, ?, ?, ?, ?::vector)",
            docId, company, index, content, literal(emb));
    }

    @BeforeEach
    void seed() {
        // Empresa A: 1 doc ativo com 3 chunks em posições one-hot 5, 6, 7.
        UUID docA = seedDocument(COMPANY_A, "Doc A", true);
        seedChunk(docA, COMPANY_A, 0, "conteudo do chunk cinco", oneHot(5));
        seedChunk(docA, COMPANY_A, 1, "conteudo do chunk seis", oneHot(6));
        seedChunk(docA, COMPANY_A, 2, "conteudo do chunk sete", oneHot(7));
    }

    @Test
    @DisplayName("query casa o chunk mais similar acima do threshold; ortogonais ficam fora")
    void retrieve_returnsTopMatchAboveThreshold() {
        // query one-hot na posição 6 → casa exatamente o chunk índice 1 (similarity 1.0).
        fakeQueryEmbedding.next.set(oneHot(6));

        List<RetrievedChunk> result = retrievalService.retrieve(COMPANY_A, "qualquer pergunta");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("conteudo do chunk seis");
        assertThat(result.get(0).similarity()).isGreaterThan(0.99);
        assertThat(result.get(0).documentTitle()).isEqualTo("Doc A");
    }

    @Test
    @DisplayName("documento inativo é excluído do retrieval")
    void retrieve_inactiveDocument_excluded() {
        // Cria um doc INATIVO com um chunk one-hot na posição 8.
        UUID inactive = seedDocument(COMPANY_A, "Doc Inativo", false);
        seedChunk(inactive, COMPANY_A, 0, "conteudo secreto inativo", oneHot(8));

        fakeQueryEmbedding.next.set(oneHot(8));   // casaria o chunk inativo, se ele entrasse
        List<RetrievedChunk> result = retrievalService.retrieve(COMPANY_A, "pergunta");

        assertThat(result).noneMatch(c -> c.content().contains("inativo"));
    }

    @Test
    @DisplayName("isolamento por tenant: empresa B não vê chunks da empresa A")
    void retrieve_companyIsolation() {
        // Empresa B tem o próprio doc com chunk one-hot 6 (mesma posição da A).
        UUID docB = seedDocument(COMPANY_B, "Doc B", true);
        seedChunk(docB, COMPANY_B, 0, "conteudo da empresa B", oneHot(6));

        fakeQueryEmbedding.next.set(oneHot(6));
        List<RetrievedChunk> result = retrievalService.retrieve(COMPANY_B, "pergunta");

        // Só o chunk da B (não o da A, que também tem one-hot 6).
        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("conteudo da empresa B");
    }

    /** Guarda o próximo vetor de query a ser devolvido pelo fake. */
    static class FakeQueryEmbedding {
        final AtomicReference<float[]> next = new AtomicReference<>(new float[384]);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        FakeQueryEmbedding fakeQueryEmbedding() {
            return new FakeQueryEmbedding();
        }

        @Bean
        @Primary
        EmbeddingProvider fakeEmbeddingProvider(FakeQueryEmbedding holder) {
            return (texts, kind) -> List.of(holder.next.get());
        }
    }
}
