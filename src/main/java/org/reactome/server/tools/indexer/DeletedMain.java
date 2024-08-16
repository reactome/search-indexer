package org.reactome.server.tools.indexer;

import com.martiansoftware.jsap.*;
import org.apache.solr.client.solrj.SolrClient;
import org.reactome.server.graph.service.DatabaseObjectService;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.graph.service.SchemaService;
import org.reactome.server.graph.utils.ReactomeGraphCore;
import org.reactome.server.tools.indexer.config.IndexerNeo4jConfig;
import org.reactome.server.tools.indexer.deleted.impl.DeletedIndexer;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.icon.exporter.IconsExporter;
import org.reactome.server.tools.indexer.icon.impl.IconIndexer;
import org.reactome.server.tools.indexer.target.impl.TargetIndexer;

import static org.reactome.server.tools.indexer.util.SolrUtility.closeSolrServer;
import static org.reactome.server.tools.indexer.util.SolrUtility.getSolrClient;

/**
 * Simple main class only to index targets
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class DeletedMain {
    private static final String DEF_SOLR_URL = "http://localhost:8983/solr";
    private static final String DEF_SOLR_CORE = "reactome";

    public static void main(String[] args) throws IndexerException, JSAPException {
        try {
            SimpleJSAP jsap = new SimpleJSAP(IconsMain.class.getName(), "A tool for generating a Solr Index for Icons.",
                    new Parameter[]{
                            new FlaggedOption("neo4jHost", JSAP.STRING_PARSER, "bolt://localhost", JSAP.NOT_REQUIRED, 'a', "neo4jHost", "The neo4j host"),
                            new FlaggedOption("neo4jPort", JSAP.STRING_PARSER, "7687", JSAP.NOT_REQUIRED, 'b', "neo4jPort", "The neo4j port"),
                            new FlaggedOption("neo4jUser", JSAP.STRING_PARSER, "neo4j", JSAP.NOT_REQUIRED, 'c', "neo4jUser", "The neo4j user"),
                            new FlaggedOption("neo4jDatabase", JSAP.STRING_PARSER, "graph.db", JSAP.REQUIRED, 'd', "neo4jDatabase", "The neo4j database"),
                            new FlaggedOption("neo4jPw", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'e', "neo4jPw", "The neo4j password"),
                            new FlaggedOption("solrUrl", JSAP.STRING_PARSER, DEF_SOLR_URL, JSAP.REQUIRED, 'f', "solrUrl", "Url of the running Solr server"),
                            new FlaggedOption("solrCollection", JSAP.STRING_PARSER, DEF_SOLR_CORE, JSAP.REQUIRED, 'g', "solrCollection", "The Reactome solr collection"),
                            new FlaggedOption("solrUser", JSAP.STRING_PARSER, "admin", JSAP.NOT_REQUIRED, 'h', "solrUser", "The Solr user"),
                            new FlaggedOption("solrPw", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'i', "solrPw", "The Solr password"),
                    }
            );

            JSAPResult config = jsap.parse(args);
            if (jsap.messagePrinted()) System.exit(1);

            SolrClient solrClient = getSolrClient(config.getString("solrUser"), config.getString("solrPw"), config.getString("solrUrl"));
            ReactomeGraphCore.initialise(config.getString("neo4jHost") + ":" + config.getString("neo4jPort"), config.getString("neo4jUser"), config.getString("neo4jPw"), config.getString("neo4jDatabase"), IndexerNeo4jConfig.class);


            DeletedIndexer indexer = ReactomeGraphCore.getService(DeletedIndexer.class);
            indexer.setSolrCollection(config.getString("solrCollection"));

            indexer.index();
            closeSolrServer(solrClient);
        } catch (Error | Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
