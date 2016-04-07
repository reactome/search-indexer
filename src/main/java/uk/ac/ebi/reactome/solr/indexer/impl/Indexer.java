package uk.ac.ebi.reactome.solr.indexer.impl;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import uk.ac.ebi.reactome.solr.indexer.exception.IndexerException;
import uk.ac.ebi.reactome.solr.indexer.model.IndexDocument;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class is responsible for establishing connection to Solr
 * and the MySQL adapter. It iterates through the collection of
 * GkInstances returned by the MySQL adapter for a given SchemaClass
 * and adds IndexDocuments in batches to the Solr Server
 *
 * @author Florian Korninger (fkorn@ebi.ac.uk)
 * @version 1.0
 */
public class Indexer {

    private static SolrClient solrClient;
    private static MySQLAdaptor dba;
    private Converter converter;
    private Marshaller marshaller;

    private int addInterval;
    private Boolean verbose;
    private Boolean xml;
    private static final String CONTROLLED_VOCABULARY= "controlledvocabulary.csv";
    private static final String EBEYE_NAME = "Reactome";
    private static final String EBEYE_DESCRIPTION = "Reactome is a free, open-source, curated and peer reviewed pathway " +
            "database. Our goal is to provide intuitive bioinformatics tools for the visualization, interpretation and " +
            "analysis of pathway knowledge to support basic research, genome analysis, modeling, systems biology and " +
            "education.";

    private static final Logger logger = Logger.getLogger(Indexer.class);

    public Indexer(MySQLAdaptor dba, SolrClient solrClient, int addInterval, File ebeye, String release, Boolean verbose) {

        logger.setLevel(Level.INFO);
        Indexer.dba = dba;
        Indexer.solrClient = solrClient;
        converter = new Converter(CONTROLLED_VOCABULARY);

        this.addInterval = addInterval;
        this.verbose = verbose;
        this.xml = ebeye != null;
        if (xml) {
            marshaller = new Marshaller(ebeye, EBEYE_NAME, EBEYE_DESCRIPTION, release);
        }
    }

    public void index() throws IndexerException {

        try {
            cleanSolrIndex();
            if (xml) {
                marshaller.writeHeader();
            }
            int entriesCount = 0;
            entriesCount += indexSchemaClass(ReactomeJavaConstants.Event);
            commitSolrServer();
            entriesCount += indexSchemaClass(ReactomeJavaConstants.PhysicalEntity);
            commitSolrServer();
            entriesCount += indexSchemaClass(ReactomeJavaConstants.Regulation);
            if (xml) {
                marshaller.writeFooter(entriesCount);
            }
            commitSolrServer();

        } catch (Exception e) {
            logger.error(e);
            throw new IndexerException(e);
        } finally {
            closeSolrServer();
        }

    }

    /**
     * Iterates of a Collection of GkInstances, each Instance will be converted
     * to a IndexDocument by the Converter, The IndexDocuments will be added to
     * Solr and marshaled to a xml file.
     * @param className Name of the SchemaClass that should be indexed
     * @return number of Documents processed
     * @throws IndexerException
     */
    private int indexSchemaClass(String className) throws IndexerException {

        Collection<?> instances;
        try {
            instances = dba.fetchInstancesByClass(className);
        } catch (Exception e) {
            logger.error("Fetching Instances by ClassName from the Database caused an errer", e);
            throw new IndexerException("Fetching Instances by ClassName from the Database caused an errer", e);
        }
        int numberOfDocuments = 0;
        List<IndexDocument> collection = new ArrayList<>();
        for (Object object : instances) {
            GKInstance instance = (GKInstance) object;
            IndexDocument document = converter.buildDocumentFromGkInstance(instance);
            collection.add(document);
            if (xml) {
                marshaller.writeEntry(document);
            }
            numberOfDocuments++;
            if (numberOfDocuments % addInterval == 0 && !collection.isEmpty()) {
                addDocumentsToSolrServer(collection);
                collection.clear();
                if (xml) {
                    try {
                        marshaller.flush();
                    } catch (IOException e) {
                        logger.error("An error occurred when trying to flush to XML", e);
                    }
                }
                logger.info(numberOfDocuments + " " + className + " have now been added to Solr");
                if (verbose) {
                    System.out.println(numberOfDocuments + " " + className + " have now been added to Solr");
                }
            }
        }
        if (!collection.isEmpty()) {
            addDocumentsToSolrServer(collection);
            logger.info(numberOfDocuments + " " + className + " have now been added to Solr");
            if (verbose) {
                System.out.println(numberOfDocuments + " " + className + " have now been added to Solr");
            }
        }
        return numberOfDocuments;
    }

    /**
     * Safely adding Document Bean to Solr Server
     * @param documents List of Documents that will be added to Solr
     *
     * !!!!!!!!!!!!!!!!!!!!!! REMOTE_SOLR_EXCEPTION is a Runtime Exception !!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    private void addDocumentsToSolrServer(List<IndexDocument> documents) {

        if (documents != null && !documents.isEmpty()) {
            try {
                solrClient.addBeans(documents);
                logger.info(documents.size() + " Documents succsessfully added to Sorl");
            } catch (IOException|SolrServerException|HttpSolrClient.RemoteSolrException e) {
                for (IndexDocument document : documents) {
                    try {
                        solrClient.addBean(document);
                        logger.info("A single document was added to Solr");
                    } catch (IOException|SolrServerException|HttpSolrClient.RemoteSolrException e1) {
                        logger.error("Could not add document", e);
                        logger.error("Document DBID: " + document.getDbId() + " Name " + document.getName());
                    }
                }
                logger.error("Could not add documenst", e);
            }
        } else {
            logger.error("Solr Documents are null or empty");
        }
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
        } catch (SolrServerException|IOException e) {
            logger.error("an error occured while cleaning the SolrServer", e);
            throw new IndexerException("an error occured while cleaning the SolrServer", e);
        }
    }

    /**
     * Closes connection to Solr Server
     */
    public static void closeSolrServer()  {
        try {
            solrClient.close();
            logger.info("SolrServer shutdown");
        } catch (IOException e) {
            logger.error("an error occured while closing the SolrServer", e);
        }
    }

    /**
     * Commits Data that has been added till now to Solr Server
     * @throws IndexerException not comiting could mean that this Data will not be added to Solr
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
}


