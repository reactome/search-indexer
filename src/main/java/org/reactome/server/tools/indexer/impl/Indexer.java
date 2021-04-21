package org.reactome.server.tools.indexer.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.Event;
import org.reactome.server.graph.domain.model.PhysicalEntity;
import org.reactome.server.graph.domain.model.ReferenceEntity;
import org.reactome.server.graph.domain.result.PersonAuthorReviewer;
import org.reactome.server.graph.exception.CustomQueryException;
import org.reactome.server.graph.service.AdvancedDatabaseObjectService;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.graph.service.PersonService;
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

import static org.reactome.server.tools.indexer.util.SolrUtility.cleanSolrIndex;
import static org.reactome.server.tools.indexer.util.SolrUtility.commitSolrServer;

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
    private PersonService personService;

    // Creating SolR Document querying the Graph in Transactional execution
    private DocumentBuilder documentBuilder;
    private InteractorDocumentBuilder interactorDocumentBuilder;
    private PersonDocumentBuilder personDocumentBuilder;

    private SolrClient solrClient;
    private String solrCollection;
    private Marshaller marshaller;
    private Marshaller covidMarshaller;

    private Boolean ebeyeXml = false;
    private Boolean ebeyeCovidXml = false;
    private int releaseNumber;
    private long total;
    private int covidEntriesCount;

    public int index() throws IndexerException {
        long start = System.currentTimeMillis();
        int entriesCount = 0;

        totalCount();
        queryReleaseNumber();

        try {
            initialiseXmlOutputFiles();

            cleanSolrIndex(solrCollection, solrClient);

            entriesCount += indexBySchemaClass(PhysicalEntity.class, entriesCount);
            commitSolrServer(solrCollection, solrClient);
            cleanNeo4jCache();

            entriesCount += indexBySchemaClass(Event.class, entriesCount);
            commitSolrServer(solrCollection, solrClient);
            cleanNeo4jCache();

            finaliseXmlOutputFiles(entriesCount, covidEntriesCount);

            logger.info("Started importing Interactors data to SolR");
            entriesCount += indexInteractors();
            commitSolrServer(solrCollection, solrClient);
            logger.info("Entries total: " + entriesCount);
            cleanNeo4jCache();

            logger.info("Started importing Person records to SolR");
            entriesCount += indexPeople();
            commitSolrServer(solrCollection, solrClient);
            logger.info("Entries total: " + entriesCount);
            cleanNeo4jCache();

            long end = System.currentTimeMillis() - start;
            logger.info("Full indexing took " + end + " .ms");

            System.out.println("\nData Import finished with " + entriesCount + " entries imported.");

            return entriesCount;
        } catch (Exception e) {
            logger.error("An error occurred during the data import", e);
            e.printStackTrace();
            throw new IndexerException(e);
        }
    }

    private void initialiseXmlOutputFiles() throws IndexerException {
        if (ebeyeXml) {
            marshaller = new Marshaller(new File("ebeye.xml"), EBEYE_NAME, EBEYE_DESCRIPTION);
            marshaller.writeHeader(releaseNumber);
        }

        if (ebeyeCovidXml) {
            covidMarshaller = new Marshaller(new File("ebeyecovid.xml"), EBEYE_NAME, EBEYE_DESCRIPTION);
            covidMarshaller.writeHeader(releaseNumber);
        }
    }

    private void finaliseXmlOutputFiles(int entriesCount, int covidEntriesCount) {
        if (ebeyeXml) {
            try {
                marshaller.writeFooter(entriesCount);
            } catch (IndexerException e) {
                e.printStackTrace();
            }
        }

        if (ebeyeCovidXml) {
            try {
                covidMarshaller.writeFooter(covidEntriesCount);
            } catch (IndexerException e) {
                e.printStackTrace();
            }
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
                if (ebeyeXml) marshaller.writeEntry(document);
                if (ebeyeCovidXml && document.isCovidRelated()) {
                    covidMarshaller.writeEntry(document);
                    covidEntriesCount++;
                }

                allDocuments.add(document);
            } else {
                missingDocuments.add(dbId);
            }

            numberOfDocuments++;
            if (numberOfDocuments % addInterval == 0 && !allDocuments.isEmpty()) {
                addDocumentsToSolrServer(allDocuments);
                allDocuments.clear();

                closeXmlFiles();
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

        if (!missingDocuments.isEmpty())
            logger.info("\nMissing documents for:\n\t" + StringUtils.join(missingDocuments, "\n\t"));

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

    private int indexPeople() {
        logger.info("Start indexing people into Solr");

        int numberOfDocuments = 0;
        List<IndexDocument> collection = new ArrayList<>();

        System.out.println("\n[People] Started adding to SolR");

        Collection<PersonAuthorReviewer> personAuthorReviewers = personService.getAuthorsReviewers();

        logger.info("Preparing SolR documents for people [" + personAuthorReviewers.size() + "]");
        total = personAuthorReviewers.size();
        int preparingSolrDocuments = 0;
        for (PersonAuthorReviewer par : personAuthorReviewers) {
            IndexDocument indexDocument = personDocumentBuilder.createPersonSolrDocument(par);
            collection.add(indexDocument);
            numberOfDocuments++;
            preparingSolrDocuments++;
            if (preparingSolrDocuments % 100 == 0) {
                updateProgressBar(preparingSolrDocuments);
            }
        }
        logger.info("  >> preparing person SolR Documents [" + preparingSolrDocuments + "]");
        // Save the indexDocument into Solr.
        addDocumentsToSolrServer(collection);
        logger.info(numberOfDocuments + " people have now been added to SolR");
        updateProgressBar(preparingSolrDocuments);
        return numberOfDocuments;
    }

    /**
     * Interactors (ReferenceEntities) that ARE NOT in Reactome but
     * interact with proteins/chemicals that ARE in Reactome
     *
     * @return interactor
     */
    private Collection<ReferenceEntity> getInteractors() {
        Collection<ReferenceEntity> rtn;

        String query = "" +
                "MATCH (in:ReferenceEntity)<-[:interactor]-(:Interaction)-[:interactor]->(re:ReferenceEntity) " +
                "WHERE (:ReactionLikeEvent)-[:input|output|catalystActivity|physicalEntity|entityFunctionalStatus|diseaseEntity|regulatedBy|regulator|referenceEntity*]->(re) AND " +
                "      NOT (:PhysicalEntity)-[:referenceEntity]->(in) " +
                "RETURN DISTINCT in";

        try {
            rtn = advancedDatabaseObjectService.getCustomQueryResults(ReferenceEntity.class, query, null);
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
        logger.info("Counting all entries for Event, PhysicalEntities");
        total = schemaService.countEntries(Event.class);
        total += schemaService.countEntries(PhysicalEntity.class);
    }

    private void queryReleaseNumber() {
        try {
            releaseNumber = generalService.getDBInfo().getVersion();
        } catch (Exception e) {
            logger.error("An error occurred when trying to retrieve the release number from the database.");
        }
    }

    private void closeXmlFiles() {
        if (ebeyeXml) {
            try {
                marshaller.flush();
            } catch (IOException e) {
                logger.error("An error occurred when trying to flush to XML", e);
            }
        }

        if (ebeyeCovidXml) {
            try {
                covidMarshaller.flush();
            } catch (IOException e) {
                logger.error("An error occurred when trying to flush to XML", e);
            }
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
                solrClient.addBeans(solrCollection, documents);
                logger.debug(documents.size() + " Documents successfully added to SolR");
            } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
                for (IndexDocument document : documents) {
                    try {
                        solrClient.addBean(solrCollection, document);
                        logger.debug("A single document was added to Solr");
                    } catch (IOException | SolrServerException | BaseHttpSolrClient.RemoteSolrException e1) {
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

    public void setSolrCollection(String solrCollection) {
        this.solrCollection = solrCollection;
    }

    public Boolean getEbeyeXml() {
        return ebeyeXml;
    }

    public void setEbeyeXml(Boolean ebeyeXml) {
        this.ebeyeXml = ebeyeXml;
    }

    public void setEbeyeCovidXml(boolean ebeyeCovidXml) {
        this.ebeyeCovidXml = ebeyeCovidXml;
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
    public void setPersonService(PersonService personService) {
        this.personService = personService;
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

    @Autowired
    public void setPersonDocumentBuilder(PersonDocumentBuilder personDocumentBuilder) {
        this.personDocumentBuilder = personDocumentBuilder;
    }

}

