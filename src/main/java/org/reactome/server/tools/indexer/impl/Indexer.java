package org.reactome.server.tools.indexer.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.reactome.server.graph.domain.model.*;
import org.reactome.server.graph.exception.CustomQueryException;
import org.reactome.server.graph.service.AdvancedDatabaseObjectService;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.graph.service.SchemaService;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class is responsible for establishing connection to Solr
 * and the Graph Database. It iterates through the collection of
 * nodes, create IndexDocuments and add them to the Solr Server.
 *
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 * @author Florian Korninger <fkorn@ebi.ac.uk>
 * @version 2.0
 */
@Service
public class Indexer {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private static final String EBEYE_NAME = "Reactome";
    private static final String EBEYE_DESCRIPTION = "Reactome is a free, open-source, curated and peer reviewed pathway " +
            "database. Our goal is to provide intuitive bioinformatics tools for the visualization, interpretation and " +
            "analysis of pathway knowledge to support basic research, genome analysis, modeling, systems biology and " +
            "education.";

    private SchemaService schemaService;
    private GeneralService generalService;
    private AdvancedDatabaseObjectService advancedDatabaseObjectService;

    // Creating SolR Document querying the Graph in Transactional execution
    private DocumentBuilder documentBuilder;
    private InteractorDocumentBuilder interactorDocumentBuilder;

    private SolrClient solrClient;
    private Marshaller marshaller;

    private Boolean xml = false;
    private long total;

    public int index() throws IndexerException {
        long start = System.currentTimeMillis();
        int entriesCount = 0;

        totalCount();

        try {
            if (xml) {
                marshaller = new Marshaller(new File("ebeye.xml"), EBEYE_NAME, EBEYE_DESCRIPTION);
                int releaseNumber = 0;
                try {
                    releaseNumber = generalService.getDBVersion();
                } catch (Exception e) {
                    logger.error("An error occurred when trying to retrieve the release number from the database.");
                }
                marshaller.writeHeader(releaseNumber);
            }

            cleanSolrIndex();

            entriesCount += indexBySchemaClass(PhysicalEntity.class, entriesCount);
            commitSolrServer();
            cleanNeo4jCache();

            entriesCount += indexBySchemaClass(Event.class, entriesCount);
            commitSolrServer();
            cleanNeo4jCache();

            entriesCount += indexBySchemaClass(Regulation.class, entriesCount);
            commitSolrServer();
            cleanNeo4jCache();

            if (xml) {
                marshaller.writeFooter(entriesCount);
            }

            logger.info("Started importing Interactors data to SolR");
            entriesCount += indexInteractors();
            commitSolrServer();
            logger.info("Entries total: " + entriesCount);

            long end = System.currentTimeMillis() - start;
            logger.info("Full indexing took " + end + " .ms");

            System.out.println("\nData Import finished with " + entriesCount + " entries imported.");

            return entriesCount;
        } catch (Exception e) {
            logger.error("An error occurred during the data import", e);
            e.printStackTrace();
            throw new IndexerException(e);
        } finally {
            closeSolrServer();
        }
    }

    /**
     * @param clazz class to be Indexed
     * @return total of indexed items
     */
    private int indexBySchemaClass(Class<? extends DatabaseObject> clazz, int previousCount) throws IndexerException {
        long start = System.currentTimeMillis();

        logger.info("Getting all simple objects of class " + clazz.getSimpleName());
        Collection<Long> allOfGivenClass = schemaService.getDbIdsByClass(clazz);
        logger.info("[" + allOfGivenClass.size() + "] " + clazz.getSimpleName());

        final int addInterval = 1000;
        int numberOfDocuments = 0;
        int count = 0;
        List<IndexDocument> allDocuments = new ArrayList<>();
        List<Long> missingDocuments = new ArrayList<>();
        for (Long dbId : allOfGivenClass) {

            IndexDocument document = documentBuilder.createSolrDocument(dbId); // transactional
            if (document != null) {
                if (xml) marshaller.writeEntry(document);
                allDocuments.add(document);
            } else {
                missingDocuments.add(dbId);
            }

            numberOfDocuments++;
            if (numberOfDocuments % addInterval == 0 && !allDocuments.isEmpty()) {
                addDocumentsToSolrServer(allDocuments);
                allDocuments.clear();

                if (xml) {
                    try {
                        marshaller.flush();
                    } catch (IOException e) {
                        logger.error("An error occurred when trying to flush to XML", e);
                    }
                }
                logger.info(numberOfDocuments + " " + clazz.getSimpleName() + " have now been added to SolR");
            }

            count = previousCount + numberOfDocuments;
            if (count % 100 == 0) updateProgressBar(count);
            if (numberOfDocuments % 10000 == 0) cleanNeo4jCache();
        }

        // Add to Solr the remaining documents
        if (!allDocuments.isEmpty()) addDocumentsToSolrServer(allDocuments);

        long end = System.currentTimeMillis() - start;
        logger.info("Elapsed time for " + clazz.getSimpleName() + " is " + end + "ms.");

        if (!missingDocuments.isEmpty()) logger.info("\nMissing documents for:\n\t" + StringUtils.join(missingDocuments, "\n\t"));

        updateProgressBar(count); // done

        return numberOfDocuments;
    }

    /**
     * Save a document containing an interactor that IS NOT in Reactome and a List of Interactions
     * with Reactome proteins
     */
    private int indexInteractors() {
        logger.info("Start indexing interactors into Solr");

        int numberOfDocuments = 0;
        List<IndexDocument> collection = new ArrayList<>();

        System.out.println("\n[Interactors] Started adding to SolR");

        Collection<ReferenceEntity> interactors = getInteractors();

        logger.info("Preparing SolR documents for Interactors [" + interactors.size() + "]");
        total = interactors.size();
        int preparingSolrDocuments = 0;
        for (ReferenceEntity interactorRE : interactors) {
            // Create index document based on interactor A and the summary based on Interactor B.
            IndexDocument indexDocument = interactorDocumentBuilder.createInteractorSolrDocument(interactorRE);
            collection.add(indexDocument);

            numberOfDocuments++;

            preparingSolrDocuments++;
            if (preparingSolrDocuments % 1000 == 0) {
                logger.info("  >> preparing interactors SolR Documents [" + preparingSolrDocuments + "]");
            }
            if (preparingSolrDocuments % 100 == 0) {
                updateProgressBar(preparingSolrDocuments);
            }
        }

        logger.info("  >> preparing interactors SolR Documents [" + preparingSolrDocuments + "]");

        // Save the indexDocument into Solr.
        addDocumentsToSolrServer(collection);

        logger.info(numberOfDocuments + " Interactor(s) have now been added to SolR");

        updateProgressBar(preparingSolrDocuments);

        return numberOfDocuments;
    }

    /**
     * Interactors (ReferenceEntities) that ARE NOT in Reactome but
     * interact with proteins/chemicals that ARE in Reactome
     *
     * @return interactor
     */
    private Collection<ReferenceEntity> getInteractors(){
        Collection<ReferenceEntity> rtn;

        String query = "" +
                "MATCH (in:ReferenceEntity)<-[:interactor]-(:Interaction)-[:interactor]->(re:ReferenceEntity) " +
                "WHERE (:ReactionLikeEvent)-[:input|output|catalystActivity|entityFunctionalStatus|physicalEntity|regulatedBy|regulator|referenceEntity*]->(re) AND " +
                "      NOT (:PhysicalEntity)-[:referenceEntity]->(in) " +
                "RETURN DISTINCT in";

        try {
            rtn = advancedDatabaseObjectService.customQueryForDatabaseObjects(ReferenceEntity.class, query, null);
        } catch (CustomQueryException e) {
            logger.error(e.getMessage(), e);
            rtn = new ArrayList<>();
        }

        return rtn;
    }

    /**
     * Count how many instances we are going to index.
     * This is going to be applied in the progress bar
     */
    private void totalCount() {
        logger.info("Counting all entries for Event, PhysicalEntities and Regulation");
        total = schemaService.countEntries(Event.class);
        total += schemaService.countEntries(PhysicalEntity.class);
        total += schemaService.countEntries(Regulation.class);
    }

    /**
     * Cleaning Solr Server (removes all current Data)
     *
     * @throws IndexerException not cleaning the indexer means the indexer will failed.
     */
    private void cleanSolrIndex() throws IndexerException {
        try {
            logger.info("Cleaning solr index");
            solrClient.deleteByQuery("*:*");
            commitSolrServer();
            logger.info("Solr index has been cleaned");
        } catch (SolrServerException | IOException e) {
            logger.error("an error occurred while cleaning the SolrServer", e);
            throw new IndexerException("an error occurred while cleaning the SolrServer", e);
        }
    }

    /**
     * Closes connection to Solr Server
     */
    private void closeSolrServer() {
        try {
            solrClient.close();
            logger.info("SolrServer shutdown");
        } catch (IOException e) {
            logger.error("an error occurred while closing the SolrServer", e);
        }
    }

    /**
     * Commits Data that has been added till now to Solr Server
     *
     * @throws IndexerException not committing could mean that this Data will not be added to Solr
     */
    private void commitSolrServer() throws IndexerException {
        try {
            solrClient.commit();
            logger.info("Solr index has been committed and flushed to disk");
        } catch (Exception e) {
            logger.error("Error occurred while committing", e);
            throw new IndexerException("Could not commit", e);
        }
    }

    /**
     * Safely adding Document Bean to Solr Server
     *
     * @param documents List of Documents that will be added to Solr
     *                  <p>
     *                  REMOTE_SOLR_EXCEPTION is a Runtime Exception
     */
    private void addDocumentsToSolrServer(List<IndexDocument> documents) {
        if (documents != null && !documents.isEmpty()) {
            try {
                solrClient.addBeans(documents);
                logger.debug(documents.size() + " Documents successfully added to SolR");
            } catch (IOException | SolrServerException | HttpSolrClient.RemoteSolrException e) {
                for (IndexDocument document : documents) {
                    try {
                        solrClient.addBean(document);
                        logger.debug("A single document was added to Solr");
                    } catch (IOException | SolrServerException | HttpSolrClient.RemoteSolrException e1) {
                        logger.error("Could not add document", e);
                        logger.error("Document DBID: " + document.getDbId() + " Name " + document.getName());
                    }
                }
                logger.error("Could not add document", e);
            }
        } else {
            logger.error("Solr Documents are null or empty");
        }
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public Boolean getXml() {
        return xml;
    }

    public void setXml(Boolean xml) {
        this.xml = xml;
    }

    /**
     * Simple method that prints a progress bar to command line
     *
     * @param done Number of entries added
     */
    private void updateProgressBar(int done) {
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

    private void cleanNeo4jCache() {
        generalService.clearCache();
    }

    @Autowired
    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Autowired
    public void setGeneralService(GeneralService generalService) {
        this.generalService = generalService;
    }

    @Autowired
    public void setAdvancedDatabaseObjectService(AdvancedDatabaseObjectService advancedDatabaseObjectService) {
        this.advancedDatabaseObjectService = advancedDatabaseObjectService;
    }

    @Autowired
    public void setDocumentBuilder(DocumentBuilder documentBuilder) {
        this.documentBuilder = documentBuilder;
    }

    @Autowired
    public void setInteractorDocumentBuilder(InteractorDocumentBuilder interactorDocumentBuilder) {
        this.interactorDocumentBuilder = interactorDocumentBuilder;
    }
}

