package org.reactome.server.tools.indexer.config;

import org.reactome.server.graph.config.GraphCoreNeo4jConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;


/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Configuration
@ComponentScan(basePackages = {"org.reactome.server"})
@EnableTransactionManagement
@EnableNeo4jRepositories(basePackages = {"org.reactome.server.graph.repository"})
public class IndexerNeo4jConfig extends GraphCoreNeo4jConfig {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");


}
