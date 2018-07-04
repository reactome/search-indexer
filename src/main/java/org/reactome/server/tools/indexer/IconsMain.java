package org.reactome.server.tools.indexer;

import org.apache.solr.client.solrj.SolrClient;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.icon.impl.IconIndexer;
import org.reactome.server.tools.indexer.icon.parser.MetadataParser;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.reactome.server.tools.indexer.util.SolrUtility.closeSolrServer;
import static org.reactome.server.tools.indexer.util.SolrUtility.getSolrClient;

/**
 * Simple main class only to index targets
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Component
public class IconsMain {
    private static final String DEF_SOLR_URL = "http://localhost:8983/solr";

    public static void main(String[] args) throws IndexerException {
        SolrClient solrClient = getSolrClient("admin", "admin", DEF_SOLR_URL);
        IconIndexer indexer = new IconIndexer(solrClient, "/Users/reactome/Dev/icons/icon-lib", "/Users/reactome/Dev/icons/ehld");
        int i = indexer.indexIcons();
        System.out.println(i);
        List<String> messages = MetadataParser.getInstance().getMessages();
        if (!messages.isEmpty()) {
            messages.forEach(System.out::println);
        }
        closeSolrServer(solrClient);
    }
}
