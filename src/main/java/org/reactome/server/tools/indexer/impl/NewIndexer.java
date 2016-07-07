package org.reactome.server.tools.indexer.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.reactome.server.graph.domain.model.*;
import org.reactome.server.graph.domain.result.SimpleDatabaseObject;
import org.reactome.server.graph.service.DatabaseObjectService;
import org.reactome.server.graph.service.SchemaService;
import org.reactome.server.interactors.database.InteractorsDatabase;
import org.reactome.server.interactors.service.InteractionService;
import org.reactome.server.interactors.service.InteractorService;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.model.CrossReference;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.reactome.server.tools.indexer.model.ReactomeSummary;
import org.reactome.server.tools.indexer.util.IndexerMapSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for establishing connection to Solr
 * and the Graph Database. It iterates through the collection of
 * nodes, create IndexDocuments and add them to the Solr Server.
 *
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 * @author Florian Korninger <fkorn@ebi.ac.uk>
 * @version 2.0
 */
public class NewIndexer {

    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    /**
     *
     */
    private static ApplicationContext neo4jContext;
    private static SolrClient solrClient;
    // private static Converter converter;
    private static Marshaller marshaller;

    private static final String CONTROLLED_VOCABULARY = "controlledVocabulary.csv";
    private static final String EBEYE_NAME = "Reactome";
    private static final String EBEYE_DESCRIPTION = "Reactome is a free, open-source, curated and peer reviewed pathway " +
            "database. Our goal is to provide intuitive bioinformatics tools for the visualization, interpretation and " +
            "analysis of pathway knowledge to support basic research, genome analysis, modeling, systems biology and " +
            "education.";

    private static InteractorService interactorService;
    private static InteractionService interactionService;

    private static Boolean xml;

    private static final int addInterval = 1000;
    private static final int width = 50;
    private static int total;

    private final List<String> keywords;

    /**
     * Collection that holds accessions from IntAct that are not in Reactome Data.
     * This collection will be used to keep interactions to those accession not in Reactome.
     */
    private static final Set<String> accessionsNoReactome = new HashSet<>();

    /**
     * Reactome Ids and names (ReactomeSummary) and their reference Entity accession identifier
     */
    private final Map<String, ReactomeSummary> accessionMap = new HashMap<>();
    private final Map<Integer, String> taxonomyMap = new HashMap<>();

    public NewIndexer(ApplicationContext neo4jContext, SolrClient solrClient, InteractorsDatabase interactorsDatabase, Boolean xml) {

        NewIndexer.neo4jContext = neo4jContext;
        NewIndexer.solrClient = solrClient;
        NewIndexer.xml = xml;
        //converter = new Converter(CONTROLLED_VOCABULARY);
        interactorService = new InteractorService(interactorsDatabase);
        interactionService = new InteractionService(interactorsDatabase);

        if (xml) {
            marshaller = new Marshaller(new File("ebeye.xml"), EBEYE_NAME, EBEYE_DESCRIPTION);
        }

        keywords = loadFile(CONTROLLED_VOCABULARY);
        if (keywords == null) {
            logger.error("No keywords available");
        }
    }


    /**
     *
     */
    public void index() {
        int entriesCount = 0;

        entriesCount += indexBySchemaClass(Event.class); // (indexed)95395 // graph says of allOfGivenClass=95132
        //commitSolrServer();
//        entriesCount += indexBySchemaClass(PhysicalEntity.class);
        //commitSolrServer();
        //entriesCount += indexBySchemaClass(Regulation.class);

        System.out.println("Entries total: " + entriesCount);

    }

    /**
     * @param clazz
     * @return
     */
    private int indexBySchemaClass(Class<? extends DatabaseObject> clazz) {
        SchemaService schemaService = neo4jContext.getBean(SchemaService.class);
        DatabaseObjectService databaseObjectService = neo4jContext.getBean(DatabaseObjectService.class);
        long start = System.currentTimeMillis();

        logger.info("Getting all simple objects of class " + clazz.getSimpleName());
        Collection<SimpleDatabaseObject> allOfGivenClass = schemaService.getSimpleDatabaseObjectByClass(clazz);
        logger.info("[" + allOfGivenClass.size() + "] " + clazz.getSimpleName());

        List<IndexDocument> allDocuments = new ArrayList<>();
        Set<String> types = new TreeSet<>();
        for (SimpleDatabaseObject simpleDatabaseObject : allOfGivenClass) {
            IndexDocument document = new IndexDocument();

            types.add(simpleDatabaseObject.getSchemaClass());

            if (!simpleDatabaseObject.getSchemaClass().equals(SimpleEntity.class.getSimpleName())) {
               continue;
            }

//            if (simpleDatabaseObject.getDbId() != 162613) {
//                continue;
//            }
            DatabaseObject databaseObject = databaseObjectService.findByIdNoRelations(simpleDatabaseObject.getDbId());
            document.setDbId(databaseObject.getDbId().toString());
            document.setStId(databaseObject.getStId());
            document.setOldStId(databaseObject.getOldStId());

            // TODO create one field fullname and then it will be the display name - discuss!
            document.setName(databaseObject.getDisplayName()); // TODO: keep the name as the displayName ? the name is also used in the keywords... check it

            document.setType(getType(databaseObject)); // reference will set the type properly if there is...
            document.setExactType(databaseObject.getSchemaClass());

            if (keywords != null) {
                document.setKeywords(keywords.stream().filter(keyword -> document.getName().toLowerCase().contains(keyword.toLowerCase())).collect(Collectors.toList()));
            }

            if (databaseObject instanceof PhysicalEntity) {
                PhysicalEntity physicalEntity = (PhysicalEntity) databaseObject;

                setSynonyms(document, physicalEntity.getName(), false);
                setLiteratureReference(document, physicalEntity.getLiteratureReference());
                setSummation(document, physicalEntity.getSummation());
                setDiseases(document, physicalEntity.getDisease());
                setCompartment(document, physicalEntity.getCompartment());
                setCrossReference(document, physicalEntity.getCrossReference());
                setGoTerms(document, physicalEntity.getGoCellularComponent());
                setSpecies(document, physicalEntity);

                setReferenceEntity(document, physicalEntity);


            } else if (databaseObject instanceof Event) {
                Event event = (Event) databaseObject;
                document.setSynonyms(event.getName()); // TODO: remove the first one ? set the first as the name ?

                setLiteratureReference(document, event.getLiteratureReference());
                setSummation(document, event.getSummation());
                setDiseases(document, event.getDisease());
                setCompartment(document, event.getCompartment());
                setCrossReference(document, event.getCrossReference());
                setGoTerms(document, event.getGoBiologicalProcess());
                setSpecies(document, event);

                if (event instanceof ReactionLikeEvent) {
                    ReactionLikeEvent reactionLikeEvent = (ReactionLikeEvent) event;
                    setCatalystActivities(document, reactionLikeEvent.getCatalystActivity());
                }


            } else if (databaseObject instanceof Regulation) {
                Regulation regulation = (Regulation) databaseObject;
                document.setType("Regulation");
                document.setExactType(databaseObject.getSchemaClass());

                setLiteratureReference(document, regulation.getLiteratureReference());
                setSummation(document, regulation.getSummation());

                setRegulatedEntity(document, regulation.getRegulatedEntity());
                setRegulator(document, regulation.getRegulator());


            }


            allDocuments.add(document);

        }

        System.out.println(types);
        long end = System.currentTimeMillis() - start;
        System.out.println("events = " + end);


        return allDocuments.size(); // TODO change return type based on the count.
    }

    /**
     * @param document
     * @param name
     */
    private void setSynonyms(IndexDocument document, List<String> name, boolean referenceSynomym) {
        // TODO: remove the first one ? set the first as the name ?
        if (name == null || name.isEmpty()) return;

        // we are assigning the displayName to document.setName();
        Iterator<String> iterator = name.iterator();
        iterator.next();
        iterator.remove();

        if (!name.isEmpty()) {
            if (referenceSynomym) {
                document.setReferenceSynonyms(name);
            } else {
                document.setSynonyms(name);
            }
        }
    }

    /**
     * Set literatureReference in SolR document based on the list of Publication
     *
     * @param document            is the SolR document.
     * @param literatureReference is the list of Publication
     */
    private void setLiteratureReference(IndexDocument document, List<Publication> literatureReference) {
        if (literatureReference == null) return;

        IndexerMapSet<String, String> mapSet = new IndexerMapSet<>();
        for (Publication publication : literatureReference) {
            mapSet.add("title", publication.getTitle());
            if (publication instanceof LiteratureReference) {
                mapSet.add("pubMedIdentifier", ((LiteratureReference) publication).getPubMedIdentifier() + "");
            } else if (publication instanceof Book) {
                Book book = (Book) publication;
                if (StringUtils.isNotEmpty(book.getISBN())) {
                    mapSet.add("ISBN", book.getISBN());
                }
            }
            mapSet.add("author", publication.getAuthor().stream().map(DatabaseObject::getDisplayName).collect(Collectors.toList()));
        }

        document.setLiteratureReferenceTitle(new ArrayList<>(mapSet.getElements("title")));
        document.setLiteratureReferencePubMedId(new ArrayList<>(mapSet.getElements("pubMedIdentifier")));
        document.setLiteratureReferenceIsbn(new ArrayList<>(mapSet.getElements("ISBN")));
        document.setLiteratureReferenceAuthor(new ArrayList<>(mapSet.getElements("author")));
    }

    /**
     * @param document
     * @param summations
     */
    private void setSummation(IndexDocument document, List<Summation> summations) {
        if (summations == null) return;

        String summationText = "";
        boolean first = true;
        for (Summation summation : summations) {
            if (first) {
                summationText = summation.getText();
                first = false;
            } else {
                summationText = summationText + "<br>" + summation.getText();
            }
        }

        if (!summationText.contains("computationally inferred")) {
            document.setSummation(summationText);
        } else {
            document.setInferredSummation(summationText);
        }
    }

    /**
     * @param document
     * @param diseases
     */
    private void setDiseases(IndexDocument document, List<? extends ExternalOntology> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            document.setIsDisease(false);
            return;
        }

        List<String> diseasesId = diseases.stream().map(ExternalOntology::getIdentifier).collect(Collectors.toList());
        diseasesId.addAll(diseases.stream().map(d -> "doid:" + d.getIdentifier()).collect(Collectors.toList()));

        document.setDiseaseId(diseasesId);
        document.setDiseaseName(diseases.stream().flatMap(e -> e.getName().stream()).collect(Collectors.toList()));
        document.setDiseaseSynonyms(diseases.stream().filter(f -> f.getSynonym() != null).flatMap(e -> e.getSynonym().stream()).collect(Collectors.toList()));
        document.setIsDisease(true);
    }

    /**
     * @param document
     * @param compartments
     */
    private void setCompartment(IndexDocument document, List<? extends Compartment> compartments) {
        if (compartments == null || compartments.isEmpty()) return;

        document.setCompartmentName(compartments.stream().map(DatabaseObject::getDisplayName).collect(Collectors.toList()));
        document.setCompartmentAccession(compartments.stream().map(Compartment::getAccession).collect(Collectors.toList()));
    }

    /**
     * @param document
     * @param crossReferences
     */
    private void setCrossReference(IndexDocument document, List<DatabaseIdentifier> crossReferences) {
        if (crossReferences == null || crossReferences.isEmpty()) return;

        // CrossReferencesIds add displayName which is <DB>:<ID> and also the Identifier
        List<String> crossReferencesInfo = new ArrayList<>();

        // allCrossReferences are used in the Marshaller (ebeye.xml)
        List<CrossReference> allXRefs = new ArrayList<>();
        for (DatabaseIdentifier databaseIdentifier : crossReferences) {
            crossReferencesInfo.add(databaseIdentifier.getDisplayName());
            crossReferencesInfo.add(databaseIdentifier.getIdentifier());

            CrossReference crossReference = new CrossReference();
            crossReference.setId(databaseIdentifier.getIdentifier());
            crossReference.setDbName(databaseIdentifier.getDatabaseName());
            allXRefs.add(crossReference);
        }

        document.setCrossReferences(crossReferencesInfo);
        document.setAllCrossReferences(allXRefs);
    }

    /**
     * @param document
     * @param goTerm
     */
    private void setGoTerms(IndexDocument document, GO_Term goTerm) {
        // The "GoTerm" field is a list - We add the plain value and the constant 'go:' concatenated to the plain value
        if (goTerm == null) return;

        if (goTerm instanceof GO_BiologicalProcess) {
            GO_BiologicalProcess gbp = (GO_BiologicalProcess) goTerm;
            document.setGoBiologicalProcessAccessions(Arrays.asList("go:".concat(gbp.getAccession()), gbp.getAccession()));
            document.setGoBiologicalProcessName(gbp.getDisplayName());
        } else if (goTerm instanceof GO_CellularComponent) {
            GO_CellularComponent gcc = (GO_CellularComponent) goTerm;
            document.setGoCellularComponentAccessions(Arrays.asList("go:".concat(gcc.getAccession()), gcc.getAccession()));
            document.setGoCellularComponentName(gcc.getDisplayName());
        } else if (goTerm instanceof GO_MolecularFunction) {
            GO_MolecularFunction gmf = (GO_MolecularFunction) goTerm;
            document.addGoMolecularFunctionName(gmf.getDisplayName());
            document.addGoMolecularFunctionAccession("go:".concat(gmf.getAccession()));
            document.addGoMolecularFunctionAccession(gmf.getAccession());
        }
    }

    /**
     * @param document
     * @param databaseObject
     */
    private void setSpecies(IndexDocument document, DatabaseObject databaseObject) {
        Collection<? extends Taxon> speciesCollection = null;
        if (databaseObject instanceof GenomeEncodedEntity) {
            GenomeEncodedEntity genomeEncodedEntity = (GenomeEncodedEntity) databaseObject;
            if (genomeEncodedEntity.getSpecies() != null) {
                speciesCollection = Collections.singletonList(genomeEncodedEntity.getSpecies());
            }
        } else if (databaseObject instanceof EntitySet) {
            EntitySet entitySet = (EntitySet) databaseObject;
            speciesCollection = entitySet.getSpecies();
        } else if (databaseObject instanceof Complex) {
            Complex complex = (Complex) databaseObject;
            speciesCollection = complex.getSpecies();
        } else if (databaseObject instanceof SimpleEntity) {
            SimpleEntity simpleEntity = (SimpleEntity) databaseObject;
            if (simpleEntity.getSpecies() != null) {
                speciesCollection = Collections.singletonList(simpleEntity.getSpecies());
            }
        } else if (databaseObject instanceof Polymer) {
            Polymer polymer = (Polymer) databaseObject;
            speciesCollection = polymer.getSpecies();
        } else if (databaseObject instanceof Event) {
            Event event = (Event) databaseObject;
            speciesCollection = event.getSpecies();

            if (event.getRelatedSpecies() != null) {
                document.setRelatedSpecies(event.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
            }
        }

        if (speciesCollection == null || speciesCollection.isEmpty()) {
            document.setSpecies("Entries without species");
            return;
        }

        // TODO: species is a collection ? species in solr is String...
        for (Taxon species : speciesCollection) {
            if (species == null) {
                System.out.println("hi");
            }
            document.setSpecies(species.getDisplayName());
            document.setTaxId(species.getTaxId());
        }

    }

    /**
     * @param document
     * @param databaseObject
     */
    private void setReferenceEntity(IndexDocument document, DatabaseObject databaseObject) {
        if (databaseObject == null) return;

        ReferenceEntity referenceEntity = null;
        String identifier;

        if (databaseObject instanceof EntityWithAccessionedSequence) {
            EntityWithAccessionedSequence ewas = (EntityWithAccessionedSequence) databaseObject;
            referenceEntity = ewas.getReferenceEntity();

        } else if (databaseObject instanceof OpenSet) {
            OpenSet openSet = (OpenSet) databaseObject;
            referenceEntity = openSet.getReferenceEntity();

        } else if (databaseObject instanceof SimpleEntity) {
            SimpleEntity simpleEntity = (SimpleEntity) databaseObject;
            referenceEntity = simpleEntity.getReferenceEntity();

        }

        if (referenceEntity != null) {
            identifier = referenceEntity.getIdentifier();

            if (referenceEntity instanceof ReferenceSequence) {
                ReferenceSequence referenceSequence = (ReferenceSequence) referenceEntity;
                document.setReferenceGeneNames(referenceSequence.getGeneName());
                document.setReferenceSecondaryIdentifier(referenceSequence.getSecondaryIdentifier());

                // variant Identifier has to be set in case is ReferenceIsoform
                if (referenceSequence instanceof ReferenceIsoform) {
                    ReferenceIsoform referenceIsoform = (ReferenceIsoform) referenceSequence;
                    identifier = referenceIsoform.getVariantIdentifier();
                }
            }

            document.setType(getReferenceTypes(referenceEntity));
            document.setExactType(referenceEntity.getSchemaClass());

            if (referenceEntity.getName() != null && !referenceEntity.getName().isEmpty()) {
                document.setReferenceName(referenceEntity.getName().get(0));
                setSynonyms(document, referenceEntity.getName(), true);
            }

            document.setReferenceOtherIdentifier(referenceEntity.getOtherIdentifier());

            setReferenceCrossReference(document, referenceEntity.getCrossReference());

            if (identifier != null) {
                List<String> referenceIdentifiers = new LinkedList<>();
                referenceIdentifiers.add(identifier);
                referenceIdentifiers.add(referenceEntity.getReferenceDatabase().getDisplayName() + ":" + identifier);
                document.setReferenceIdentifiers(referenceIdentifiers);
                document.setDatabaseName(referenceEntity.getReferenceDatabase().getDisplayName());

                String url = referenceEntity.getReferenceDatabase().getAccessUrl();
                document.setReferenceURL(url.replace("###ID###", identifier));
            }
        }
    }

    /**
     * @param referenceEntity
     * @return
     */
    private String getReferenceTypes(ReferenceEntity referenceEntity) {
        if (referenceEntity instanceof ReferenceGeneProduct) {
            return "Protein";
        } else if (referenceEntity instanceof ReferenceDNASequence) {
            return "DNA Sequence";
        } else if (referenceEntity instanceof ReferenceRNASequence) {
            return "RNA Sequence";
        } else if (referenceEntity instanceof ReferenceMolecule || referenceEntity instanceof ReferenceGroup) {
            return "Chemical Compound";
        } else {
            return referenceEntity.getSchemaClass(); // TODO CHECK THIS SCHEMA CLASS
        }
    }

    /**
     * @param document
     * @param referenceCrossReferences
     */
    private void setReferenceCrossReference(IndexDocument document, List<DatabaseIdentifier> referenceCrossReferences) {
        if (referenceCrossReferences == null || referenceCrossReferences.isEmpty()) return;

        // CrossReferencesIds add displayName which is <DB>:<ID> and also the Identifier
        List<String> crossReferencesInfo = new ArrayList<>();

        // allCrossReferences are used in the Marshaller (ebeye.xml)
        List<CrossReference> allXRefs = new ArrayList<>();
        for (DatabaseIdentifier databaseIdentifier : referenceCrossReferences) {
            crossReferencesInfo.add(databaseIdentifier.getIdentifier());

            CrossReference crossReference = new CrossReference();
            crossReference.setId(databaseIdentifier.getIdentifier());
            crossReference.setDbName(databaseIdentifier.getReferenceDatabase().getDisplayName());
            allXRefs.add(crossReference);
        }

        document.setReferenceCrossReferences(crossReferencesInfo);
        document.setAllCrossReferences(allXRefs);
    }

    /**
     * @param document
     * @param catalystActivities
     */
    private void setCatalystActivities(IndexDocument document, List<CatalystActivity> catalystActivities) {
        if (catalystActivities == null) return;

        for (CatalystActivity catalystActivity : catalystActivities) {
            setGoTerms(document, catalystActivity.getActivity());
        }
    }

    /**
     * @param databaseObject
     * @return type
     */
    private String getType(DatabaseObject databaseObject) {
        if (databaseObject instanceof EntitySet) {
            return "Set";
        } else if (databaseObject instanceof GenomeEncodedEntity) { // TODO VERIFY... previously was using isa(..)
            return "Genes and Transcripts";
        } else if (databaseObject instanceof Pathway) {
            return "Pathway";
        } else if (databaseObject instanceof ReactionLikeEvent) {
            return "Reaction";
        }

        return databaseObject.getSchemaClass();

    }

    /**
     * @param document
     * @param regulatedEntity
     */
    private void setRegulatedEntity(IndexDocument document, DatabaseObject regulatedEntity) {
        if (regulatedEntity == null) return;

        document.setRegulatedEntity(regulatedEntity.getDisplayName()); // TODO we don't have the name to set it in first instance
        if (StringUtils.isNotEmpty(regulatedEntity.getStId())) {
            document.setRegulatedEntityId(regulatedEntity.getStId());
        } else {
            document.setRegulatedEntityId(regulatedEntity.getDbId().toString());
        }
    }

    /**
     * @param document
     * @param regulator
     */
    private void setRegulator(IndexDocument document, DatabaseObject regulator) {
        if (regulator == null) return;

        List<String> names = new ArrayList<>();
        if (regulator instanceof CatalystActivity) {
            CatalystActivity ca = (CatalystActivity) regulator;
            names = ca.getPhysicalEntity().getName();
        } else if (regulator instanceof Event) {
            Event ev = (Event) regulator;
            names = ev.getName();
        } else if (regulator instanceof PhysicalEntity) {
            PhysicalEntity pe = (PhysicalEntity) regulator;
            names = pe.getName();
        } else {
            names.add(regulator.getDisplayName());
        }
        if (!names.isEmpty()) document.setRegulator(names.get(0));

        if (StringUtils.isNotEmpty(regulator.getStId())) {
            document.setRegulatorId(regulator.getStId());
        } else {
            document.setRegulatorId(regulator.getDbId().toString());
        }

    }

    /**
     * @param fileName
     * @return
     */
    private List<String> loadFile(String fileName) {
        try {
            List<String> list = new ArrayList<>();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/" + fileName)));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                list.add(line);
            }
            bufferedReader.close();
            logger.debug(list.toString());
            return list;
        } catch (IOException e) {
            logger.error("An error occurred when loading the controlled vocabulary file", e);
        }
        return null;
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
    private static void closeSolrServer() {
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
}

