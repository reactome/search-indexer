package org.reactome.server.tools.indexer.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactome.server.BaseTest;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.springframework.beans.factory.annotation.Autowired;


class IndexerTest extends BaseTest {

    @Autowired
    DocumentBuilder documentBuilder;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    @Test
    void indexDrug() throws JsonProcessingException {
        try {
            IndexDocument document = documentBuilder.createSolrDocument(9649889L);
            logger.info(objectMapper.writeValueAsString(document));
        } catch (Error e) {
            e.printStackTrace();
        }

    }
}