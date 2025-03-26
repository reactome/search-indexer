package org.reactome.server;


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.reactome.server.graph.aop.LazyFetchAspect;
import org.reactome.server.graph.config.GraphCoreNeo4jConfig;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.graph.utils.ReactomeGraphCore;
import org.reactome.server.tools.indexer.config.IndexerNeo4jConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest
@ContextConfiguration(classes = {IndexerNeo4jConfig.class})
@ComponentScan(basePackages = "org.reactome.server")
@ExtendWith(SpringExtension.class)
public abstract class BaseTest {

    protected final Logger logger = LoggerFactory.getLogger("testLogger");


    @Autowired
    protected LazyFetchAspect lazyFetchAspect;

    @AfterAll
    public static void tearDownClass() {
    }

    @BeforeAll
    public static void setUpStatic(@Value("${spring.neo4j.uri}") String uri,
                                   @Value("${spring.neo4j.authentication.username}") String user,
                                   @Value("${spring.neo4j.authentication.password}") String pass,
                                   @Value("${spring.data.neo4j.database}") String db) {
        ReactomeGraphCore.initialise(uri, user, pass, db);
    }

}
