package org.reactome.server.tools.indexer.impl;

import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.util.SolrUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@Log4j2
@NoArgsConstructor
@Setter(onMethod_ = {@Autowired})
@Getter
public abstract class AbstractIndexer<Document> {

    protected SolrClient solrClient;
    protected GeneralService generalService;

    @Setter
    protected String solrCollection;
    @Setter
    protected long total;

    public abstract int index() throws IndexerException;

    /**
     * Safely adding Document Bean to Solr Server
     *
     * @param documents List of Documents that will be added to Solr
     *                  <p>
     *                  REMOTE_SOLR_EXCEPTION is a Runtime Exception
     */
    protected void addDocumentsToSolrServer(List<Document> documents) {
        if (documents != null && !documents.isEmpty()) {
            try {
                solrClient.addBeans(solrCollection, documents);
                solrClient.commit(solrCollection);
                log.debug("{} Documents successfully added to SolR", documents.size());
            } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
                for (Document document : documents) {
                    try {
                        solrClient.addBean(solrCollection, document);
                        log.debug("A single document was added to Solr");
                    } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e1) {
                        log.error("Could not add document", e);
                    }
                }
                log.error("Could not add document", e);
            }
        } else {
            log.error("Solr Documents are null or empty");
        }
    }

    /**
     * Simple method that prints a progress bar to command line
     *
     * @param done Number of entries added
     */
    protected void updateProgressBar(int done) {
        final int width = 55;

        String format = "\r%3d%% %s %c";
        char[] rotators = {'|', '/', '—', '\\'};
        double percent = (double) done / total;
        StringBuilder progress = new StringBuilder(width);
        progress.append('|');
        int i = 0;
        for (; i < (int) (percent * width); i++)
            progress.append("=");
        for (; i < width; i++)
            progress.append(" ");
        progress.append('|');
        System.out.printf(format, (int) (percent * 100), progress, rotators[((done - 1) % (rotators.length * 100)) / 100]);
    }



    protected void cleanNeo4jCache() {
        generalService.clearCache();
    }
}
