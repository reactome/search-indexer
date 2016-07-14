package org.reactome.server.tools.indexer.impl;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.Event;
import org.reactome.server.graph.domain.model.PhysicalEntity;
import org.reactome.server.graph.domain.model.Regulation;
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
public class NewIndexer {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private SolrClient solrClient;
    private Marshaller marshaller;

    /**
     * GRAPH DB Services
     */
    @Autowired
    private SchemaService schemaService;
    @Autowired
    private GeneralService generalService;

    /**
     * Creating SolR Document querying the Graph in Transactioal execution
     */
    @Autowired
    private DocumentBuilder documentBuilder;

    private static final String EBEYE_NAME = "Reactome";
    private static final String EBEYE_DESCRIPTION = "Reactome is a free, open-source, curated and peer reviewed pathway " +
            "database. Our goal is to provide intuitive bioinformatics tools for the visualization, interpretation and " +
            "analysis of pathway knowledge to support basic research, genome analysis, modeling, systems biology and " +
            "education.";

//    private static InteractorService interactorService;
//    private static InteractionService interactionService;

    private Boolean xml = false;

    private final int addInterval = 1000;

    // TODO: Create the progress bar
    private final int width = 50;
    private long total;


    /**
     * Collection that holds accessions from IntAct that are not in Reactome Data.
     * This collection will be used to keep interactions to those accession not in Reactome.
     */
//    private static final Set<String> accessionsNoReactome = new HashSet<>();

    /**
     * Reactome Ids and names (ReactomeSummary) and their reference Entity accession identifier
     */
//    private final Map<String, ReactomeSummary> accessionMap = new HashMap<>();
//    private final Map<Integer, String> taxonomyMap = new HashMap<>();

    /**
     *
     */
    public void totalCount() {
        total = schemaService.countEntries(Event.class);
        total += schemaService.countEntries(PhysicalEntity.class);
        total += schemaService.countEntries(Regulation.class);
    }

    /**
     *
     */
    public void index() throws IndexerException {
        long start = System.currentTimeMillis();
        int entriesCount = 0;

        totalCount();

        if (xml) {
            int releaseNumber = 0;
            try {
                releaseNumber = generalService.getDBVersion();
            } catch (Exception e) {
                logger.error("An error occurred when trying to retrieve the release number from the database.");
            }
            marshaller.writeHeader(releaseNumber);
        }

        cleanSolrIndex();
        entriesCount += indexBySchemaClass(Event.class);
        commitSolrServer();
        entriesCount += indexBySchemaClass(PhysicalEntity.class);
        commitSolrServer();
        entriesCount += indexBySchemaClass(Regulation.class);
        commitSolrServer();

        System.out.println("Entries total: " + entriesCount);

        long end = System.currentTimeMillis() - start;

        logger.info("Full indexing took " + end + " .ms");

    }

    /**
     * @param clazz
     * @return
     */
    private int indexBySchemaClass(Class<? extends DatabaseObject> clazz) throws IndexerException {
        long start = System.currentTimeMillis();

        logger.info("Getting all simple objects of class " + clazz.getSimpleName());
        Collection<Long> allOfGivenClass = schemaService.getDbIdsByClass(clazz);
        logger.info("[" + allOfGivenClass.size() + "] " + clazz.getSimpleName());

        int numberOfDocuments = 0;
        List<IndexDocument> allDocuments = new ArrayList<>();

        for (Long dbId : allOfGivenClass) {
            IndexDocument document = documentBuilder.createSolrDocument(dbId); // transactional

            if (xml) {
                marshaller.writeEntry(document);
            }

            allDocuments.add(document);

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
                logger.info(numberOfDocuments + " " + clazz.getSimpleName() + " have now been added to Solr");
            }
        }

        /**
         * Add to Solr the remaining documents
         */
        if (!allDocuments.isEmpty()) {
            addDocumentsToSolrServer(allDocuments);
            logger.info(numberOfDocuments + " " + clazz.getSimpleName() + " have now been added to Solr");
        }

        long end = System.currentTimeMillis() - start;
        logger.info("Elapsed time for " + clazz.getSimpleName() + " is " + end + "ms.");

        return numberOfDocuments;
    }

    /**
     * Cleaning Solr Server (removes all current Data)
     *
     * @throws IndexerException
     */
    private void cleanSolrIndex() throws IndexerException {
        try {
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
                logger.info(documents.size() + " Documents successfully added to Solr");
            } catch (IOException | SolrServerException | HttpSolrClient.RemoteSolrException e) {
                for (IndexDocument document : documents) {
                    try {
                        solrClient.addBean(document);
                        logger.info("A single document was added to Solr");
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

    public SolrClient getSolrClient() {
        return solrClient;
    }

    public void setSolrClient(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public Marshaller getMarshaller() {
        return marshaller;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public Boolean getXml() {
        return xml;
    }

    public void setXml(Boolean xml) {
        this.xml = xml;
        if (xml) {
            marshaller = new Marshaller(new File("ebeye.xml"), EBEYE_NAME, EBEYE_DESCRIPTION);
        }
    }

//    public void setInteractorsDatabase(InteractorsDatabase interactorsDatabase) {
//        this.interactorsDatabase = interactorsDatabase;
//    }
}

