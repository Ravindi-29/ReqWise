package com.reqwise.service.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingServiceTest {

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService();
    }

    @Test
    @DisplayName("Generates 1536-dimensional L2-normalized dense vector")
    void shouldGenerateUnitVector() {
        String text = "The system shall authenticate users via OAuth 2.0 with MFA.";
        float[] vector = embeddingService.generateEmbedding(text);

        assertThat(vector).hasSize(EmbeddingService.VECTOR_DIMENSION);

        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += v * v;
        }
        assertThat(sumSq).isBetween(0.99, 1.01);
    }

    @Test
    @DisplayName("Cosine similarity of identical text is exactly 1.0")
    void shouldHaveMaxSimilarityForIdenticalText() {
        String text = "The application shall encrypt all data at rest using AES-256.";
        float[] v1 = embeddingService.generateEmbedding(text);
        float[] v2 = embeddingService.generateEmbedding(text);

        double sim = embeddingService.cosineSimilarity(v1, v2);
        assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Semantically related texts have higher similarity than unrelated texts")
    void shouldReflectSemanticRelatedness() {
        float[] vAuth1 = embeddingService.generateEmbedding("The system shall require multi-factor authentication for user login.");
        float[] vAuth2 = embeddingService.generateEmbedding("User authentication shall support MFA and password credentials.");
        float[] vBackup = embeddingService.generateEmbedding("The database shall execute incremental backups daily at midnight.");

        double simRelated = embeddingService.cosineSimilarity(vAuth1, vAuth2);
        double simUnrelated = embeddingService.cosineSimilarity(vAuth1, vBackup);

        assertThat(simRelated).isGreaterThan(simUnrelated);
    }

    @Test
    @DisplayName("Serializes float vector to string and parses back accurately")
    void shouldSerializeAndParseVector() {
        float[] original = embeddingService.generateEmbedding("Test vector serialization string.");
        String str = embeddingService.vectorToString(original);
        float[] parsed = embeddingService.stringToVector(str);

        assertThat(parsed).hasSize(EmbeddingService.VECTOR_DIMENSION);
        double sim = embeddingService.cosineSimilarity(original, parsed);
        assertThat(sim).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }
}
