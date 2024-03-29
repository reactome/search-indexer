package org.reactome.server.tools.indexer.icon.impl;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.icon.model.Icon;
import org.reactome.server.tools.indexer.icon.model.IconDocument;
import org.reactome.server.tools.indexer.icon.parser.MetadataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.reactome.server.tools.indexer.util.SolrUtility.cleanSolrIndex;
import static org.reactome.server.tools.indexer.util.SolrUtility.commitSolrServer;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class IconIndexer {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");
    private String solrCollection;
    private SolrClient solrClient;
    private String iconDir;
    private String ehldsDir;

    /**
     * Index possible target proteins for Reactome.
     *
     * @param solrClient holds solr connection for target core
     */
    public IconIndexer(SolrClient solrClient, String solrCollection, String iconDir, String ehldsDir) {
        this.solrClient = solrClient;
        this.solrCollection = solrCollection;
        this.iconDir = iconDir;
        this.ehldsDir = ehldsDir;
    }

    public int indexIcons() throws IndexerException {
        logger.info("["+ solrCollection +"] Start indexing icons into Solr");
        cleanSolrIndex(solrCollection, solrClient, "{!term f=type}icon");
        List<IconDocument> collection = new ArrayList<>();
        logger.info("["+ solrCollection +"]  Started adding to SolR");
        MetadataParser parser = MetadataParser.getInstance(iconDir, ehldsDir);
        List<Icon> icons = parser.getIcons();
        logger.info("["+ solrCollection +"] Preparing SolR documents for icons [" + icons.size() + "]");
        IconDocumentBuilder iconDocumentBuilder = new IconDocumentBuilder(solrCollection, solrClient);
        icons.forEach(icon -> collection.add(iconDocumentBuilder.createIconSolrDocument(icon)));
        addDocumentsToSolrServer(collection);
        commitSolrServer(solrCollection, solrClient);
        return collection.size();
    }

    /**
     * Safely adding Document Bean to Solr Server
     *
     * @param documents List of Documents that will be added to Solr
     *                  <p>
     *                  REMOTE_SOLR_EXCEPTION is a Runtime Exception
     */
    private void addDocumentsToSolrServer(List<IconDocument> documents) {
        if (documents != null && !documents.isEmpty()) {
            try {
                solrClient.addBeans(solrCollection, documents);
                logger.debug(documents.size() + " Documents successfully added to SolR");
            } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
                for (IconDocument document : documents) {
                    try {
                        solrClient.addBean(solrCollection, document);
                        logger.debug("A single document was added to Solr");
                    } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e1) {
                        logger.error("Could not add document", e);
                    }
                }
                logger.error("Could not add document", e);
            }
        } else {
            logger.error("Solr Documents are null or empty");
        }
    }
}
