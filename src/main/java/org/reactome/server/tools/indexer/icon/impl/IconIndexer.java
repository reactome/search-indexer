package org.reactome.server.tools.indexer.icon.impl;

import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.log4j.Log4j2;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.icon.model.Icon;
import org.reactome.server.tools.indexer.icon.model.IconDocument;
import org.reactome.server.tools.indexer.icon.parser.MetadataParser;
import org.reactome.server.tools.indexer.impl.AbstractIndexer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.reactome.server.tools.indexer.util.SolrUtility.cleanSolrIndex;
import static org.reactome.server.tools.indexer.util.SolrUtility.commitSolrServer;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
@Log4j2
@Getter
@Setter
public class IconIndexer extends AbstractIndexer<IconDocument> {
    private String iconsDir;
    private String ehldsDir;

    public int index() throws IndexerException {
        log.info("[{}] Start indexing icons into Solr", solrCollection);
        cleanSolrIndex(solrCollection, solrClient, "{!term f=type}icon");
        List<IconDocument> collection = new ArrayList<>();
        log.info("[{}]  Started adding to SolR", solrCollection);
        MetadataParser parser = MetadataParser.getInstance(iconsDir, ehldsDir);
        List<Icon> icons = parser.getIcons();
        log.info("[{}] Preparing SolR documents for icons [{}]", solrCollection, icons.size());
        IconDocumentBuilder iconDocumentBuilder = new IconDocumentBuilder(solrCollection, solrClient);
        icons.forEach(icon -> collection.add(iconDocumentBuilder.createIconSolrDocument(icon)));
        addDocumentsToSolrServer(collection);
        commitSolrServer(solrCollection, solrClient);
        return collection.size();
    }
}
