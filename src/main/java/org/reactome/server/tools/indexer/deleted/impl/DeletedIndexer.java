package org.reactome.server.tools.indexer.deleted.impl;

import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.reactome.server.graph.domain.model.Deleted;
import org.reactome.server.graph.service.DatabaseObjectService;
import org.reactome.server.graph.service.SchemaService;
import org.reactome.server.tools.indexer.deleted.model.DeletedDocument;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.impl.AbstractIndexer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.reactome.server.tools.indexer.util.SolrUtility.cleanSolrIndex;

@Service
@Log4j2
@Setter(onMethod_ = {@Autowired})
public class DeletedIndexer extends AbstractIndexer<DeletedDocument> {


    private DeletedDocumentBuilder builder;
    private SchemaService schemaService;
    private DatabaseObjectService databaseObjectService;


    public int index() throws IndexerException {
        log.info("[{}] Start indexing deleted into Solr", solrCollection);
        cleanSolrIndex(solrCollection, solrClient, "{!term f=deleted}true");

        List<DeletedDocument> batch = new ArrayList<>();
        List<Long> missingDocuments = new ArrayList<>();
        final int addInterval = 1000;
        int totalDocuments = 0;

        int numberOfDeletedTreated = 0;

        Collection<Long> dbIds = schemaService.getDbIdsByClass(Deleted.class);
        setTotal(dbIds.size());
        for (Long dbId : dbIds) {
            Deleted deleted = databaseObjectService.findById(dbId);
            if (deleted != null) {
                List<DeletedDocument> documents = builder.createDocuments(deleted);
                batch.addAll(documents);
            } else {
                missingDocuments.add(dbId);
            }

            numberOfDeletedTreated++;

            if (batch.size() % addInterval == 0 && !batch.isEmpty()) {
                addDocumentsToSolrServer(batch);
                log.info("{} Deleted instances have now been added to SolR", batch.size());
                totalDocuments += batch.size();
                batch.clear();
            }


            if (numberOfDeletedTreated % 100 == 0) updateProgressBar(numberOfDeletedTreated);
            if (numberOfDeletedTreated % 10000 == 0) cleanNeo4jCache();
        }

        if (!batch.isEmpty()) {
            addDocumentsToSolrServer(batch);
            log.info("{} Deleted instances have now been added to SolR", batch.size());
            totalDocuments += batch.size();
        }

        if (!missingDocuments.isEmpty())
            log.info("\nMissing documents for:\n\t{}", StringUtils.join(missingDocuments, "\n\t"));

        return totalDocuments;

    }




}
