package org.reactome.server.tools.indexer.impl;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.Event;
import org.reactome.server.graph.domain.model.PhysicalEntity;
import org.reactome.server.graph.domain.model.Regulation;
import org.reactome.server.graph.service.DatabaseObjectService;
import org.reactome.server.graph.service.GeneralService;
import org.reactome.server.graph.service.SchemaService;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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

    /**
     *
     */
//    private static ApplicationContext neo4jContext;

    private SolrClient solrClient;
    // private static Converter converter;
    private Marshaller marshaller;

    /**
     * GRAPH DB Services
     */
    @Autowired
    private SchemaService schemaService;

    @Autowired
    private DatabaseObjectService databaseObjectService;

    @Autowired
    private GeneralService generalService;

    @Autowired
    private AspectService aspectService;

    private static final String CONTROLLED_VOCABULARY = "controlledVocabulary.csv";
    private static final String EBEYE_NAME = "Reactome";
    private static final String EBEYE_DESCRIPTION = "Reactome is a free, open-source, curated and peer reviewed pathway " +
            "database. Our goal is to provide intuitive bioinformatics tools for the visualization, interpretation and " +
            "analysis of pathway knowledge to support basic research, genome analysis, modeling, systems biology and " +
            "education.";

//    private static InteractorService interactorService;
//    private static InteractionService interactionService;

    private Boolean xml = false;

    private final int addInterval = 1000;
    private final int width = 50;
    private long total;

    private List<String> keywords;

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
     * Spring is autowiring this constructor
     **/
    public NewIndexer() {
//        keywords = loadFile(CONTROLLED_VOCABULARY);
//        if (keywords == null) {
//            logger.error("No keywords available");
//        }
    }


//    public NewIndexer(SolrClient solrClient, Boolean xml) {
//        NewIndexer.neo4jContext = applicationContext;
//        this.solrClient = solrClient;
//        this.xml = xml;
//        interactorService = new InteractorService(interactorsDatabase);
//        interactionService = new InteractionService(interactorsDatabase);

//        if (xml) {
//            marshaller = new Marshaller(new File("ebeye.xml"), EBEYE_NAME, EBEYE_DESCRIPTION);
//        }

//        keywords = loadFile(CONTROLLED_VOCABULARY);
//        if (keywords == null) {
//            logger.error("No keywords available");
//        }

//        schemaService = applicationContext.getBean(SchemaService.class);
//        databaseObjectService = applicationContext.getBean(DatabaseObjectService.class);
//        generalService = applicationContext.getBean(GeneralService.class);
//    }

//    @Transactional(propagation = Propagation.NESTED)
    public void totalCount() {
        total = schemaService.countEntries(Event.class);
        total += schemaService.countEntries(PhysicalEntity.class);
        total += schemaService.countEntries(Regulation.class);
    }

    /**
     *
     */
//    @Transactional
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
//        Collection<SimpleDatabaseObject> allOfGivenClass = schemaService.getSimpleDatabaseObjectByClass(clazz);
        Collection<Long> allOfGivenClass = schemaService.getDbIdsByClass(clazz);
        logger.info("[" + allOfGivenClass.size() + "] " + clazz.getSimpleName());

        int numberOfDocuments = 0;
        List<IndexDocument> allDocuments = new ArrayList<>();

        for (Long dbId : allOfGivenClass) {
            IndexDocument document = aspectService.testingTransactional(dbId);

            /** Keyword uses the document.getName. Name is set in the document by calling setNameAndSynonyms **/
//            setKeywords(document);

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

//    public IndexDocument createDocument(Long dbId) {
//        IndexDocument document = new IndexDocument();
//        /**
//         * Query the Graph and load only Primitives and no Relations attributes. Lazy-loading will load them on demand.
//         */
//        DatabaseObject databaseObject = databaseObjectService.findByIdNoRelations(dbId);
//
//        /** Setting common attributes **/
//        document.setDbId(databaseObject.getDbId().toString());
//        document.setStId(databaseObject.getStId());
//        document.setOldStId(databaseObject.getOldStId());
//
//        document.setType(getType(databaseObject));
//        document.setExactType(databaseObject.getSchemaClass());
//
//        if (databaseObject instanceof PhysicalEntity) {
//            PhysicalEntity physicalEntity = (PhysicalEntity) databaseObject;
//
//            /** GENERAL ATTRIBUTES **/
//            setNameAndSynonyms(document, physicalEntity, physicalEntity.getName());
//            setLiteratureReference(document, physicalEntity.getLiteratureReference());
//            setSummation(document, physicalEntity.getSummation());
//            setDiseases(document, physicalEntity.getDisease());
//            setCompartment(document, physicalEntity.getCompartment());
//            setCrossReference(document, physicalEntity.getCrossReference());
//            setSpecies(document, physicalEntity);
//
//            /** SPECIFIC FOR PHYSICAL ENTITIES **/
//            setGoTerms(document, physicalEntity.getGoCellularComponent());
//            setReferenceEntity(document, physicalEntity);
//
//        } else if (databaseObject instanceof Event) {
//            Event event = (Event) databaseObject;
//
//            /** GENERAL ATTRIBUTES **/
//            setNameAndSynonyms(document, event, event.getName());
//            setLiteratureReference(document, event.getLiteratureReference());
//            setSummation(document, event.getSummation());
//            setDiseases(document, event.getDisease());
//            setCompartment(document, event.getCompartment());
//            setCrossReference(document, event.getCrossReference());
//            setSpecies(document, event);
//
//            /** SPECIFIC FOR EVENT **/
//            setGoTerms(document, event.getGoBiologicalProcess());
//            if (event instanceof ReactionLikeEvent) {
//                ReactionLikeEvent reactionLikeEvent = (ReactionLikeEvent) event;
//                setCatalystActivities(document, reactionLikeEvent.getCatalystActivity());
//            }
//
//        } else if (databaseObject instanceof Regulation) {
//            Regulation regulation = (Regulation) databaseObject;
//
//            /** GENERAL ATTRIBUTES **/
//            setNameAndSynonyms(document, regulation, regulation.getName());
//            setLiteratureReference(document, regulation.getLiteratureReference());
//            setSummation(document, regulation.getSummation());
//            setSpecies(document, regulation);
//
//            /** SPECIFIC FOR REGULATIONS **/
//            setRegulatedEntity(document, regulation.getRegulatedEntity());
//            setRegulator(document, regulation.getRegulator());
//
//        }
//
//        return document;
//    }

    /**
//     * @param document
//     * @param databaseObject
//     * @param name
//     */
//    private void setNameAndSynonyms(IndexDocument document, DatabaseObject databaseObject, List<String> name) {
//        if (name == null || name.isEmpty()) {
//            /** some regulations do not have name **/
//            document.setName(databaseObject.getDisplayName());
//            return;
//        }
//
//        Iterator<String> iterator = name.iterator();
//        // name is the first one of this list
//        document.setName(iterator.next());
//        // remove it and assign the other as synonyms.
//        iterator.remove();
//
//        if (!name.isEmpty()) {
//            document.setSynonyms(name);
//        }
//    }
//
//    /**
//     * @param document
//     * @param databaseObject
//     * @param referenceName
//     */
//    private void setReferenceNameAndSynonyms(IndexDocument document, DatabaseObject databaseObject, List<String> referenceName) {
//        if (referenceName == null || referenceName.isEmpty()) {
//            document.setReferenceName(databaseObject.getDisplayName());
//            return;
//        }
//
//        Iterator<String> iterator = referenceName.iterator();
//        // name is the first one of this list
//        document.setReferenceName(iterator.next());
//        // remove it and assign the other as synonyms.
//        iterator.remove();
//
//        if (!referenceName.isEmpty()) {
//            document.setReferenceSynonyms(referenceName);
//        }
//    }
//
//    /**
//     * Set literatureReference in SolR document based on the list of Publication
//     *
//     * @param document            is the SolR document.
//     * @param literatureReference is the list of Publication
//     */
//    private void setLiteratureReference(IndexDocument document, List<Publication> literatureReference) {
//        if (literatureReference == null) return;
//
//        IndexerMapSet<String, String> mapSet = new IndexerMapSet<>();
//        for (Publication publication : literatureReference) {
//            mapSet.add("title", publication.getTitle());
//            if (publication instanceof LiteratureReference) {
//                mapSet.add("pubMedIdentifier", ((LiteratureReference) publication).getPubMedIdentifier() + "");
//            } else if (publication instanceof Book) {
//                Book book = (Book) publication;
//                if (StringUtils.isNotEmpty(book.getISBN())) {
//                    mapSet.add("ISBN", book.getISBN());
//                }
//            }
//
//            // authorsList has duplicated values -> in order to replicate the previous Indexer, I am adding the DB_ID and
//            // later on I will remove it. For the indexer, actually, it won't make difference indexing non potential duplicated
//            // fields.
//            mapSet.add("author", publication.getAuthor().stream().map(i -> i.getDbId() + "#" + i.getDisplayName()).collect(Collectors.toList()));
//        }
//
//        document.setLiteratureReferenceTitle(new ArrayList<>(mapSet.getElements("title")));
//        document.setLiteratureReferencePubMedId(new ArrayList<>(mapSet.getElements("pubMedIdentifier")));
//        document.setLiteratureReferenceIsbn(new ArrayList<>(mapSet.getElements("ISBN")));
//
//        // This split by # is temporary here and may cause issues. Even getting the DB_ID won't give us the same as the previous
//        // e.g The Reaction R-GGA-573294 has 2 LiteratureReference and 8 authors + 3 authors (11 authors according to gk_instance)
//        // but those 3 we have 2 existing authors having the same DB_ID + 1 new author. At the end I have 9 authors. Which still
//        // does not match 11. But ok... // TODO DISCUSS it.
//        document.setLiteratureReferenceAuthor(mapSet.getElements("author").stream().map(i -> i.split("#")[1]).collect(Collectors.toList()));
//    }
//
//    /**
//     * @param document
//     * @param summations
//     */
//    private void setSummation(IndexDocument document, List<Summation> summations) {
//        if (summations == null) return;
//
//        /** Creating a report - We should have only one summation **/
//        if (summations.size() >= 2) {
//            logger.info("[SUMMATION] - " + document.getDbId());
//        }
//
//        String summationText = "";
//        boolean first = true;
//        for (Summation summation : summations) {
//            if (first) {
//                summationText = summation.getText();
//                first = false;
//            } else {
//                summationText = summationText + "<br>" + summation.getText();
//            }
//        }
//
//        if (!summationText.contains("computationally inferred")) {
//            document.setSummation(summationText);
//        } else {
//            document.setInferredSummation(summationText);
//        }
//    }
//
//    /**
//     * @param document
//     * @param diseases
//     */
//    private void setDiseases(IndexDocument document, List<? extends ExternalOntology> diseases) {
//        if (diseases == null || diseases.isEmpty()) {
//            document.setIsDisease(false);
//            return;
//        }
//
//        List<String> diseasesId = diseases.stream().map(ExternalOntology::getIdentifier).collect(Collectors.toList());
//        diseasesId.addAll(diseases.stream().map(d -> "doid:" + d.getIdentifier()).collect(Collectors.toList()));
//
//        document.setDiseaseId(diseasesId);
//        document.setDiseaseName(diseases.stream().flatMap(e -> e.getName().stream()).collect(Collectors.toList()));
//        // TODO: Create a report for those diseases which synonym is null
//        document.setDiseaseSynonyms(diseases.stream().filter(f -> f.getSynonym() != null).flatMap(e -> e.getSynonym().stream()).collect(Collectors.toList()));
//        document.setIsDisease(true);
//    }
//
//    /**
//     * @param document
//     * @param compartments
//     */
//    private void setCompartment(IndexDocument document, List<? extends Compartment> compartments) {
//        if (compartments == null || compartments.isEmpty()) return;
//
//        document.setCompartmentName(compartments.stream().map(DatabaseObject::getDisplayName).collect(Collectors.toList()));
//        document.setCompartmentAccession(compartments.stream().map(Compartment::getAccession).collect(Collectors.toList()));
//    }
//
//    /**
//     * @param document
//     * @param crossReferences
//     */
//    private void setCrossReference(IndexDocument document, List<DatabaseIdentifier> crossReferences) {
//        if (crossReferences == null || crossReferences.isEmpty()) return;
//
//        // CrossReferencesIds add displayName which is <DB>:<ID> and also the Identifier
//        List<String> crossReferencesInfo = new ArrayList<>();
//
//        // allCrossReferences are used in the Marshaller (ebeye.xml)
//        List<CrossReference> allXRefs = new ArrayList<>();
//        for (DatabaseIdentifier databaseIdentifier : crossReferences) {
//            crossReferencesInfo.add(databaseIdentifier.getDisplayName());
//            crossReferencesInfo.add(databaseIdentifier.getIdentifier());
//
//            CrossReference crossReference = new CrossReference();
//            crossReference.setId(databaseIdentifier.getIdentifier());
//            crossReference.setDbName(databaseIdentifier.getDatabaseName());
//            allXRefs.add(crossReference);
//        }
//
//        document.setCrossReferences(crossReferencesInfo);
//        document.setAllCrossReferences(allXRefs);
//    }
//
//    /**
//     * @param document
//     * @param goTerm
//     */
//    private void setGoTerms(IndexDocument document, GO_Term goTerm) {
//        /** The "GoTerm" field is a list - We add the plain value and the constant 'go:' concatenated to the plain value **/
//        if (goTerm == null) return;
//
//        if (goTerm instanceof GO_BiologicalProcess) {
//            GO_BiologicalProcess gbp = (GO_BiologicalProcess) goTerm;
//            document.setGoBiologicalProcessAccessions(Arrays.asList("go:".concat(gbp.getAccession()), gbp.getAccession()));
//            document.setGoBiologicalProcessName(gbp.getDisplayName());
//        } else if (goTerm instanceof GO_CellularComponent) {
//            GO_CellularComponent gcc = (GO_CellularComponent) goTerm;
//            document.setGoCellularComponentAccessions(Arrays.asList("go:".concat(gcc.getAccession()), gcc.getAccession()));
//            document.setGoCellularComponentName(gcc.getDisplayName());
//        } else if (goTerm instanceof GO_MolecularFunction) {
//            GO_MolecularFunction gmf = (GO_MolecularFunction) goTerm;
//            document.addGoMolecularFunctionName(gmf.getDisplayName());
//            document.addGoMolecularFunctionAccession("go:".concat(gmf.getAccession()));
//            document.addGoMolecularFunctionAccession(gmf.getAccession());
//        }
//    }
//
//    /**
//     * @param document
//     * @param databaseObject
//     */
//    private void setSpecies(IndexDocument document, DatabaseObject databaseObject) {
//        Collection<? extends Taxon> speciesCollection = null;
//        if (databaseObject instanceof GenomeEncodedEntity) {
//            GenomeEncodedEntity genomeEncodedEntity = (GenomeEncodedEntity) databaseObject;
//            if (genomeEncodedEntity.getSpecies() != null) {
//                speciesCollection = Collections.singletonList(genomeEncodedEntity.getSpecies());
//            }
//        } else if (databaseObject instanceof EntitySet) {
//            EntitySet entitySet = (EntitySet) databaseObject;
//            speciesCollection = entitySet.getSpecies();
//        } else if (databaseObject instanceof Complex) {
//            Complex complex = (Complex) databaseObject;
//            speciesCollection = complex.getSpecies();
//        } else if (databaseObject instanceof SimpleEntity) {
//            SimpleEntity simpleEntity = (SimpleEntity) databaseObject;
//            if (simpleEntity.getSpecies() != null) {
//                speciesCollection = Collections.singletonList(simpleEntity.getSpecies());
//            }
//        } else if (databaseObject instanceof Polymer) {
//            Polymer polymer = (Polymer) databaseObject;
//            speciesCollection = polymer.getSpecies();
//        } else if (databaseObject instanceof Event) {
//            Event event = (Event) databaseObject;
//            speciesCollection = event.getSpecies();
//
//            if (event.getRelatedSpecies() != null) {
//                document.setRelatedSpecies(event.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
//            }
//        }
//
//        if (speciesCollection == null || speciesCollection.isEmpty()) {
//            //TODO: What about change  "Entries without species" default name ?
//            document.setSpecies("Entries without species");
//            return;
//        }
//
//        // TODO: SPECIES IS A COLLECTION IN THE DATA MODEL BUT SOLR HAS IT AS SPECIES ONLY
//        // TODO: AS PART OF THE UPDATE TO THE NEW VERSION, I'LL KEEP THE ONLY THE FIRST ONE IN THE LIST. NOT HUNDRED PERCENT CONFIDENT HERE.
////        for (Taxon species : speciesCollection) {
////            document.setSpecies(species.getDisplayName());
////            document.setTaxId(species.getTaxId());
////        }
//
//        document.setSpecies(speciesCollection.iterator().next().getDisplayName());
//        document.setTaxId(speciesCollection.iterator().next().getTaxId());
//    }
//
//    /**
//     * @param document
//     * @param databaseObject
//     */
//    private void setReferenceEntity(IndexDocument document, DatabaseObject databaseObject) {
//        if (databaseObject == null) return;
//
//        ReferenceEntity referenceEntity = null;
//        String identifier;
//
//        if (databaseObject instanceof EntityWithAccessionedSequence) {
//            EntityWithAccessionedSequence ewas = (EntityWithAccessionedSequence) databaseObject;
//            referenceEntity = ewas.getReferenceEntity();
//        } else if (databaseObject instanceof OpenSet) {
//            OpenSet openSet = (OpenSet) databaseObject;
//            referenceEntity = openSet.getReferenceEntity();
//        } else if (databaseObject instanceof SimpleEntity) {
//            SimpleEntity simpleEntity = (SimpleEntity) databaseObject;
//            referenceEntity = simpleEntity.getReferenceEntity();
//        }
//
//        if (referenceEntity != null) {
//            identifier = referenceEntity.getIdentifier();
//
//            if (referenceEntity instanceof ReferenceSequence) {
//                ReferenceSequence referenceSequence = (ReferenceSequence) referenceEntity;
//                document.setReferenceGeneNames(referenceSequence.getGeneName());
//                document.setReferenceSecondaryIdentifier(referenceSequence.getSecondaryIdentifier());
//
//                // variant Identifier has to be set in case is ReferenceIsoform
//                if (referenceSequence instanceof ReferenceIsoform) {
//                    ReferenceIsoform referenceIsoform = (ReferenceIsoform) referenceSequence;
//                    if (StringUtils.isNotEmpty(referenceIsoform.getVariantIdentifier())) {
//                        identifier = referenceIsoform.getVariantIdentifier();
//                    }
//                }
//            }
//
//            /** Setting TYPE and EXACT TYPE for the given PhysicalEntity **/
//            document.setType(getReferenceTypes(referenceEntity));
//            document.setExactType(referenceEntity.getSchemaClass());
//
//            if (referenceEntity.getName() != null && !referenceEntity.getName().isEmpty()) {
//                setReferenceNameAndSynonyms(document, referenceEntity, referenceEntity.getName());
//            }
//
//            document.setReferenceOtherIdentifier(referenceEntity.getOtherIdentifier());
//
//            setReferenceCrossReference(document, referenceEntity.getCrossReference());
//
//            if (identifier != null) {
//                List<String> referenceIdentifiers = new LinkedList<>();
//                referenceIdentifiers.add(identifier);
//                referenceIdentifiers.add(referenceEntity.getReferenceDatabase().getDisplayName() + ":" + identifier);
//                document.setReferenceIdentifiers(referenceIdentifiers);
//                document.setDatabaseName(referenceEntity.getReferenceDatabase().getDisplayName());
//
//                String url = referenceEntity.getReferenceDatabase().getAccessUrl();
//                if (StringUtils.isNotEmpty(url)) {
//                    document.setReferenceURL(url.replace("###ID###", identifier));
//                }
//            }
//        }
//    }
//
//    /**
//     * @param referenceEntity
//     * @return
//     */
//    private String getReferenceTypes(ReferenceEntity referenceEntity) {
//        if (referenceEntity instanceof ReferenceGeneProduct) {
//            return "Protein";
//        } else if (referenceEntity instanceof ReferenceDNASequence) {
//            return "DNA Sequence";
//        } else if (referenceEntity instanceof ReferenceRNASequence) {
//            return "RNA Sequence";
//        } else if (referenceEntity instanceof ReferenceMolecule || referenceEntity instanceof ReferenceGroup) {
//            return "Chemical Compound";
//        } else {
//            return referenceEntity.getSchemaClass();
//        }
//    }
//
//    /**
//     * @param document
//     * @param referenceCrossReferences
//     */
//    private void setReferenceCrossReference(IndexDocument document, List<DatabaseIdentifier> referenceCrossReferences) {
//        if (referenceCrossReferences == null || referenceCrossReferences.isEmpty()) return;
//
//        /** CrossReferencesIds add displayName which is <DB>:<ID> and also the Identifier **/
//        List<String> crossReferencesInfo = new ArrayList<>();
//
//        /** allCrossReferences are used in the Marshaller (ebeye.xml) **/
//        List<CrossReference> allXRefs = new ArrayList<>();
//        for (DatabaseIdentifier databaseIdentifier : referenceCrossReferences) {
//            crossReferencesInfo.add(databaseIdentifier.getIdentifier());
//
//            CrossReference crossReference = new CrossReference();
//            crossReference.setId(databaseIdentifier.getIdentifier());
//            crossReference.setDbName(databaseIdentifier.getReferenceDatabase().getDisplayName());
//            allXRefs.add(crossReference);
//        }
//
//        document.setReferenceCrossReferences(crossReferencesInfo);
//        document.setAllCrossReferences(allXRefs);
//    }
//
//    /**
//     * @param document
//     * @param catalystActivities
//     */
//    private void setCatalystActivities(IndexDocument document, List<CatalystActivity> catalystActivities) {
//        if (catalystActivities == null) return;
//
//        for (CatalystActivity catalystActivity : catalystActivities) {
//            setGoTerms(document, catalystActivity.getActivity());
//        }
//    }
//
//    /**
//     * Generic Type, the one we have in the facets.
//     *
//     * @param databaseObject
//     * @return type
//     */
//    private String getType(DatabaseObject databaseObject) {
//        if (databaseObject instanceof EntitySet) {
//            /** Any instance of CandidateSet, DefinedSet, OpenSet **/
//            return "Set";
//        } else if (databaseObject instanceof GenomeEncodedEntity) {
//            /** Any instance of GenomeEncodedEntity is setting the type based on its Reference **/
//            return "Genes and Transcripts";
//        } else if (databaseObject instanceof Pathway) {
//            /** Also covering TopLevelPathway **/
//            return "Pathway";
//        } else if (databaseObject instanceof ReactionLikeEvent) {
//            /** Also covering BlackBoxEvent, (De)Polymerisation, (Failed)Reaction **/
//            return "Reaction";
//        } else if (databaseObject instanceof Regulation) {
//            /** Also covering PositiveRegulation, NegativeRegulation, Requirement **/
//            return "Regulation";
//        } else {
//            return databaseObject.getSchemaClass();
//        }
//    }
//
//    /**
//     * @param document
//     * @param regulatedEntity
//     */
//    private void setRegulatedEntity(IndexDocument document, DatabaseObject regulatedEntity) {
//        if (regulatedEntity == null) return;
//
//        // TODO: Set name as the displayName, then we avoid querying for a physical entity and get the regulated entity.
//        List<String> names = new ArrayList<>();
//        if (regulatedEntity instanceof CatalystActivity) {
//            CatalystActivity ca = (CatalystActivity) regulatedEntity;
//
//            // TODO: getPhysicalEntity is wrong. We should invoke List - getCatalizedEvents(). DISCUSS.
//            names = ca.getPhysicalEntity().getName();
//        } else if (regulatedEntity instanceof Event) {
//            Event ev = (Event) regulatedEntity;
//            names = ev.getName();
//        } else {
//            names.add(regulatedEntity.getDisplayName());
//        }
//
//        if (names != null && !names.isEmpty()) document.setRegulatedEntity(names.get(0));
//
//        if (StringUtils.isNotEmpty(regulatedEntity.getStId())) {
//            document.setRegulatedEntityId(regulatedEntity.getStId());
//        } else {
//            document.setRegulatedEntityId(regulatedEntity.getDbId().toString());
//        }
//    }
//
//    /**
//     * @param document
//     * @param regulator
//     */
//    private void setRegulator(IndexDocument document, DatabaseObject regulator) {
//        if (regulator == null) return;
//
//        List<String> names = new ArrayList<>();
//        if (regulator instanceof CatalystActivity) {
//            CatalystActivity ca = (CatalystActivity) regulator;
//            names = ca.getPhysicalEntity().getName();
//        } else if (regulator instanceof Event) {
//            Event ev = (Event) regulator;
//            names = ev.getName();
//        } else if (regulator instanceof PhysicalEntity) {
//            PhysicalEntity pe = (PhysicalEntity) regulator;
//            names = pe.getName();
//        } else {
//            names.add(regulator.getDisplayName());
//        }
//
//        if (names != null && !names.isEmpty()) document.setRegulator(names.get(0));
//
//        if (StringUtils.isNotEmpty(regulator.getStId())) {
//            document.setRegulatorId(regulator.getStId());
//        } else {
//            document.setRegulatorId(regulator.getDbId().toString());
//        }
//    }
//
//    /**
//     * Keyword rely on document.getName. Make sure you are invoking document.setName before setting the keywords
//     */
//    private void setKeywords(IndexDocument document) {
//        if (keywords == null) return;
//
//        // TODO. Discuss. Flo says the way it is implemented is not nice
//        document.setKeywords(keywords.stream().filter(keyword -> document.getName().toLowerCase().contains(keyword.toLowerCase())).collect(Collectors.toList()));
//    }
//
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

