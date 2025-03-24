package org.reactome.server.tools.indexer.impl;

import org.apache.commons.lang3.StringUtils;
import org.reactome.server.graph.domain.model.*;
import org.reactome.server.graph.domain.result.DiagramOccurrences;
import org.reactome.server.graph.exception.CustomQueryException;
import org.reactome.server.graph.service.AdvancedDatabaseObjectService;
import org.reactome.server.graph.service.DatabaseObjectService;
import org.reactome.server.graph.service.DiagramService;
import org.reactome.server.graph.service.PathwaysService;
import org.reactome.server.tools.indexer.model.CrossReference;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.reactome.server.tools.indexer.model.SpeciesResult;
import org.reactome.server.tools.indexer.util.MapSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class DocumentBuilder {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private static final String CONTROLLED_VOCABULARY = "controlledVocabulary.csv";
    private static final String SARS_DOID_MAPPING = "sars_doid_mapping.csv";
    public static final Pattern DB_ID_PATTERN = Pattern.compile("(?<db>\\w+).*:(?<id>.+)");
    public static final Pattern SPACE_PATTERN = Pattern.compile("\\s");

    private DatabaseObjectService databaseObjectService;
    private AdvancedDatabaseObjectService advancedDatabaseObjectService;
    private DiagramService diagramService;
    private PathwaysService pathwaysService;

    private final Map<Long, Set<String>> simpleEntitiesAndDrugSpecies = new HashMap<>();
    private final Collection<String> covid19enties = new ArrayList<>();

    private final List<String> keywords;
    private final List<String> SARSDoid;

    public DocumentBuilder() {
        keywords = loadFile(CONTROLLED_VOCABULARY);
        if (keywords == null) {
            logger.error("No keywords available");
        }

        SARSDoid = loadFile(SARS_DOID_MAPPING);
        if (SARSDoid == null) {
            logger.error("No SARS DOID mapping available");
        }
    }

    @Transactional
    public IndexDocument createSolrDocument(Long dbId) {
        System.out.println("DocumentBuilder.createSolrDocument");
//        synchronized (simpleEntitiesAndDrugSpecies) {
            if (simpleEntitiesAndDrugSpecies.isEmpty()) cacheSimpleEntityAndDrugSpecies();
//        }

//        synchronized (covid19enties) {
            if (covid19enties.isEmpty()) cacheCovid19Entities();
//        }

        IndexDocument document = new IndexDocument();
        System.out.println("IndexDocument created");
        /*
         * Query the Graph and load only Primitives and no Relations attributes.
         * Lazy-loading will load them on demand.
         */
        DatabaseObject databaseObject = databaseObjectService.findById(dbId);
        System.out.println("FindById");

        // Setting common attributes
        document.setDbId(databaseObject.getDbId().toString());
        document.setStId(databaseObject.getStId());
        document.setOldStId(databaseObject.getOldStId());

        document.setType(getType(databaseObject));
        document.setExactType(databaseObject.getSchemaClass());


        if (databaseObject instanceof PhysicalEntity) {
            PhysicalEntity physicalEntity = (PhysicalEntity) databaseObject;

            // GENERAL ATTRIBUTES
            setNameAndSynonyms(document, physicalEntity, physicalEntity.getName());
            setLiteratureReference(document, physicalEntity.getLiteratureReference());
            setSummation(document, physicalEntity.getSummation());
            setDiseases(document, physicalEntity.getDisease());
            setCompartment(document, physicalEntity.getCompartment());
            setCrossReference(document, physicalEntity.getCrossReference());
            setSpecies(document, physicalEntity);

            // SPECIFIC FOR PHYSICAL ENTITIES
            setGoTerms(document, physicalEntity.getGoCellularComponent());
            setReferenceEntity(document, physicalEntity);
        } else if (databaseObject instanceof Event) {
            Event event = (Event) databaseObject;
            // GENERAL ATTRIBUTES
            setNameAndSynonyms(document, event, event.getName());
            setLiteratureReference(document, event.getLiteratureReference());
            setSummation(document, event.getSummation());
            setDiseases(document, event.getDisease());
            setCompartment(document, event.getCompartment());
            setCrossReference(document, event.getCrossReference());
            setSpecies(document, event);

            // SPECIFIC FOR EVENT
            setGoTerms(document, event.getGoBiologicalProcess());
            if (event instanceof ReactionLikeEvent) {
                ReactionLikeEvent reactionLikeEvent = (ReactionLikeEvent) event;
                setCatalystActivities(document, reactionLikeEvent.getCatalystActivity());
            }
        }

        setFireworksSpecies(document, databaseObject);
        setDiagramOccurrences(document, databaseObject);
        setLowerLevelPathways(document, databaseObject);
        // Keyword uses the document.getName. Name is set in the document by calling setNameAndSynonyms
        setKeywords(document);

        System.out.println("Many stuff");


        // A second file is generated for covid19portal containing Reactome data related to COVID
        document.setCovidRelated(covid19enties.contains(document.getStId()));
        System.out.println("Covid stuff");

        return document;
    }

    private void cacheSimpleEntityAndDrugSpecies() {
        logger.info("Caching SimpleEntity and Drug Species");
        //language=cypher
        String query = "" +
                "MATCH (n)<-[:regulatedBy|regulator|physicalEntity|entityFunctionalStatus|catalystActivity|hasMember|hasCandidate|hasComponent|repeatedUnit|input|output|proteinMarker|RNAMarker*]-(:ReactionLikeEvent)-[:species]->(s:Species) " +
                "WHERE (n:SimpleEntity) OR (n:Drug) " +
                "WITH n, collect(DISTINCT s.displayName) AS species " +
                "RETURN n.dbId AS dbId, species";
        try {
            Collection<SpeciesResult> speciesResultList = advancedDatabaseObjectService.getCustomQueryResults(SpeciesResult.class, query);
            for (SpeciesResult speciesResult : speciesResultList) {
                simpleEntitiesAndDrugSpecies.put(speciesResult.getDbId(), new HashSet<>(speciesResult.getSpecies()));
            }
        } catch (CustomQueryException e) {
            logger.error("Could not cache fireworks species");
        }

        logger.info("Caching SimpleEntity Species is done");
    }

    private void cacheCovid19Entities() {
        logger.info("Caching COVID19 Entities");
        //language=cypher
        String query = "" +
                "MATCH (n:DatabaseObject)-[:relatedSpecies|species]-(s:Species) " +
                "WHERE n.stId IS NOT NULL AND " +
                "(s.displayName = 'Human SARS coronavirus' OR s.displayName = 'SARS-CoV-2') " +
                "MATCH (o:DatabaseObject)-[:disease]-(do:Disease) " +
                "WHERE do.identifier IN  $sarsDisease " +
                "WITH n + collect(o) AS final " +
                "UNWIND final AS f " +
                "RETURN DISTINCT f.stId AS ST_ID";
        try {
            Map<String, Object> params = new HashMap<>(SARSDoid.size());
            params.put("viral", SARSDoid.get(0));
            params.put("sarsDisease", SARSDoid.stream().skip(1).collect(Collectors.toList()));
            covid19enties.addAll(advancedDatabaseObjectService.getCustomQueryResults(String.class, query, params));
        } catch (CustomQueryException e) {
            logger.error("Could not cache covid19 entities");
        }

        logger.info("Caching COVID19 Entities is done");
    }

    private void setFireworksSpecies(IndexDocument document, DatabaseObject databaseObject) {
        Set<String> fireworksSpecies = new HashSet<>();
        if ((databaseObject instanceof SimpleEntity || databaseObject instanceof Drug)) {
            fireworksSpecies = simpleEntitiesAndDrugSpecies.get(databaseObject.getDbId());
        } else {
            //noinspection Duplicates
            try {
                Method getSpecies = databaseObject.getClass().getMethod("getSpecies");
                Object species = getSpecies.invoke(databaseObject);
                // some cases like DefinedSet it has species as an attribute but it does not have value in it.
                if (species != null) {
                    if (species instanceof Collection) {
                        //noinspection Convert2streamapi,unchecked
                        for (Taxon t : (Collection<? extends Taxon>) species) {
                            fireworksSpecies.add(t.getDisplayName());
                        }
                    } else {
                        Taxon t = (Taxon) species;
                        fireworksSpecies.add(t.getDisplayName());
                    }
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                //Nothing here
            }
        }

        // Regulation and Other Entities may not have (fireworks)species and solr won't be able to find them
        // in the Fireworks (filter query fireworksSpecies)
        if (fireworksSpecies.isEmpty()) {
            fireworksSpecies.add("Entries without species");
        }

        if (databaseObject instanceof PhysicalEntity) {
            //language=cypher
            String query = "" +
                    "MATCH (s:Species)<-[:species]-(rle:ReactionLikeEvent), " +
                    "     (rle)-[:regulatedBy|regulator|physicalEntity|entityFunctionalStatus|catalystActivity|hasMember|hasCandidate|hasComponent|repeatedUnit|input|output|proteinMarker|RNAMarker*]->(pe:PhysicalEntity) " +
                    "WHERE pe.dbId = $dbId " +
                    "RETURN DISTINCT s.displayName AS identifier";

            try {
                fireworksSpecies.addAll(advancedDatabaseObjectService.getCustomQueryResults(String.class, query, Map.of("dbId", databaseObject.getDbId())));
            } catch (CustomQueryException e) {
                logger.error("Failed to index first pathway species to fill fireworkSpecies of {}", databaseObject.getDbId());
            }
        }

        document.setFireworksSpecies(fireworksSpecies);
    }

    private void setNameAndSynonyms(IndexDocument document, DatabaseObject databaseObject, List<String> name) {
        if (name == null || name.isEmpty()) {
            document.setName(databaseObject.getDisplayName());
            return;
        }

        Iterator<String> iterator = name.iterator();
        // name is the first one of this list
        document.setName(iterator.next());
        // remove it and assign the other as synonyms.
        iterator.remove();

        if (!name.isEmpty()) {
            document.setSynonyms(name);
        }
    }

    private void setReferenceNameAndSynonyms(IndexDocument document, DatabaseObject databaseObject, List<String> referenceName) {
        if (referenceName == null || referenceName.isEmpty()) {
            document.setReferenceName(databaseObject.getDisplayName());
            return;
        }

        Iterator<String> iterator = referenceName.iterator();
        // name is the first one of this list
        document.setReferenceName(iterator.next());
        // remove it and assign the other as synonyms.
        iterator.remove();

        if (!referenceName.isEmpty()) {
            document.setReferenceSynonyms(referenceName);
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

        MapSet<String, String> mapSet = new MapSet<>();
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

            // authorsList has duplicated values -> in order to replicate the previous Indexer, I am adding the DB_ID and
            // later on I will remove it. For the indexer, actually, it won't make difference indexing non potential duplicated
            // fields.
            mapSet.add("author", publication.getAuthor().stream().map(i -> i.getDbId() + "#" + i.getDisplayName()).collect(Collectors.toList()));
        }

        document.setLiteratureReferenceTitle(new ArrayList<>(mapSet.getElements("title")));
        document.setLiteratureReferencePubMedId(new ArrayList<>(mapSet.getElements("pubMedIdentifier")));
        document.setLiteratureReferenceIsbn(new ArrayList<>(mapSet.getElements("ISBN")));

        // This split by # is temporary here and may cause issues. Even getting the DB_ID won't give us the same as the previous
        // e.g The Reaction R-GGA-573294 has 2 LiteratureReference and 8 authors + 3 authors (11 authors according to gk_instance)
        // but those 3 we have 2 existing authors having the same DB_ID + 1 new author. At the end I have 9 authors. Which still
        // does not match 11. But ok... // TODO DISCUSS it.
        document.setLiteratureReferenceAuthor(mapSet.getElements("author").stream().map(i -> i.split("#")[1]).collect(Collectors.toList()));
    }

    private void setSummation(IndexDocument document, List<Summation> summations) {
        if (summations == null) return;

        StringBuilder summationText = new StringBuilder();
        boolean first = true;
        for (Summation summation : summations) {
            if (first) {
                summationText = new StringBuilder(summation.getText());
                first = false;
            } else {
                summationText.append("<br>").append(summation.getText());
            }
        }

        if (!summationText.toString().contains("computationally inferred")) {
            document.setSummation(summationText.toString());
        } else {
            document.setInferredSummation(summationText.toString());
        }
    }

    private void setDiseases(IndexDocument document, List<? extends ExternalOntology> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            document.setIsDisease(false);
            return;
        }

        List<String> diseasesId = diseases.stream().map(ExternalOntology::getIdentifier).collect(Collectors.toList());
        diseasesId.addAll(diseases.stream().map(d -> "doid:" + d.getIdentifier()).collect(Collectors.toList()));

        document.setDiseaseId(diseasesId);
        document.setDiseaseName(diseases.stream().flatMap(e -> e.getName().stream()).collect(Collectors.toList()));
        // TODO: Create a report for those diseases which synonym is null
        document.setDiseaseSynonyms(diseases.stream().filter(f -> f.getSynonym() != null).flatMap(e -> e.getSynonym().stream()).collect(Collectors.toList()));
        document.setIsDisease(true);
    }

    private void setCompartment(IndexDocument document, List<? extends Compartment> compartments) {
        if (compartments == null || compartments.isEmpty()) return;

        document.setCompartmentName(compartments.stream().map(DatabaseObject::getDisplayName).collect(Collectors.toList()));
        document.setCompartmentAccession(compartments.stream().map(Compartment::getAccession).collect(Collectors.toList()));
    }

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
            if (entitySet.getRelatedSpecies() != null && !entitySet.getRelatedSpecies().isEmpty()) {
                document.setRelatedSpecies(entitySet.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
            }
        } else if (databaseObject instanceof Complex) {
            Complex complex = (Complex) databaseObject;
            speciesCollection = complex.getSpecies();
            if (complex.getRelatedSpecies() != null && !complex.getRelatedSpecies().isEmpty()) {
                document.setRelatedSpecies(complex.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
            }
        } else if (databaseObject instanceof SimpleEntity) {
            SimpleEntity simpleEntity = (SimpleEntity) databaseObject;
            if (simpleEntity.getSpecies() != null) {
                speciesCollection = Collections.singletonList(simpleEntity.getSpecies());
            }
        } else if (databaseObject instanceof Polymer) {
            Polymer polymer = (Polymer) databaseObject;
            speciesCollection = polymer.getSpecies();
        } else if (databaseObject instanceof Cell) {
            speciesCollection = ((Cell) databaseObject).getSpecies();
        } else if (databaseObject instanceof Event) {
            Event event = (Event) databaseObject;
            speciesCollection = event.getSpecies();
            if (event.getRelatedSpecies() != null && !event.getRelatedSpecies().isEmpty()) {
                document.setRelatedSpecies(event.getRelatedSpecies().stream().map(Species::getDisplayName).collect(Collectors.toList()));
            }
        }

        if (speciesCollection == null || speciesCollection.isEmpty()) {
            document.setSpecies(Collections.singletonList("Entries without species"));
            return;
        }

        List<String> allSpecies = speciesCollection.stream().map(Taxon::getDisplayName).collect(Collectors.toList());
        document.setSpecies(allSpecies);

        document.setTaxId(speciesCollection.stream().map(Taxon::getTaxId).collect(Collectors.toList()));
    }

    private void setReferenceEntity(IndexDocument document, DatabaseObject databaseObject) {
        if (databaseObject == null) return;

        ReferenceEntity referenceEntity = null;

        if (databaseObject instanceof EntityWithAccessionedSequence) {
            EntityWithAccessionedSequence ewas = (EntityWithAccessionedSequence) databaseObject;
            referenceEntity = ewas.getReferenceEntity();
            setModifiedResidue(document, ewas.getHasModifiedResidue());
        } else if (databaseObject instanceof SimpleEntity) {
            referenceEntity = ((SimpleEntity) databaseObject).getReferenceEntity();
        } else if (databaseObject instanceof Drug) {
            referenceEntity = ((Drug) databaseObject).getReferenceEntity();
        }

        if (referenceEntity != null) {
            String identifier = referenceEntity.getIdentifier();

            if (referenceEntity instanceof ReferenceSequence) {
                ReferenceSequence referenceSequence = (ReferenceSequence) referenceEntity;
                document.setReferenceGeneNames(referenceSequence.getGeneName());
                document.setReferenceSecondaryIdentifier(referenceSequence.getSecondaryIdentifier());

                // variant Identifier has to be set in case is ReferenceIsoform
                if (referenceSequence instanceof ReferenceIsoform) {
                    ReferenceIsoform referenceIsoform = (ReferenceIsoform) referenceSequence;
                    if (StringUtils.isNotEmpty(referenceIsoform.getVariantIdentifier())) {
                        identifier = referenceIsoform.getVariantIdentifier();
                    }
                }
                if (referenceSequence instanceof ReferenceGeneProduct) {
                    ReferenceGeneProduct referenceGeneProduct = (ReferenceGeneProduct) referenceSequence;
                    document.setReferenceDNAIdentifiers(getReferenceIdentifiers(referenceGeneProduct.getReferenceGene()));
                    document.setReferenceRNAIdentifiers(getReferenceIdentifiers(referenceGeneProduct.getReferenceTranscript()));
                }
                if (referenceSequence instanceof ReferenceRNASequence) {
                    ReferenceRNASequence referenceRNASequence = (ReferenceRNASequence) referenceSequence;
                    document.setReferenceDNAIdentifiers(getReferenceIdentifiers(referenceRNASequence.getReferenceGene()));
                }
            }

            // Setting TYPE and EXACT TYPE for the given PhysicalEntity
            document.setType(getReferenceTypes(referenceEntity));
            document.setExactType(referenceEntity.getSchemaClass());

            if (referenceEntity.getName() != null && !referenceEntity.getName().isEmpty()) {
                setReferenceNameAndSynonyms(document, referenceEntity, referenceEntity.getName());
            }

            document.setReferenceOtherIdentifier(referenceEntity.getOtherIdentifier());

            setReferenceOtherIdentifiers(document, referenceEntity.getOtherIdentifier());
            setReferenceCrossReference(document, referenceEntity.getCrossReference());

            if (identifier != null) {
                document.setReferenceIdentifiers(getReferenceIdentifiers(List.of(referenceEntity)));
                document.setDatabaseName(referenceEntity.getReferenceDatabase().getDisplayName());

                String url = referenceEntity.getReferenceDatabase().getAccessUrl();
                if (StringUtils.isNotEmpty(url)) {
                    document.setReferenceURL(url.replace("###ID###", identifier));
                }
            }
        }
    }

    private void setModifiedResidue(IndexDocument document, List<AbstractModifiedResidue> abstractModifiedResidues) {
        if (abstractModifiedResidues == null) return;
        Set<String> fragments = new HashSet<>();
        for (AbstractModifiedResidue amr : abstractModifiedResidues) {
            final ReferenceSequence referenceSequence = amr.getReferenceSequence();
            if (referenceSequence != null) {
                if (referenceSequence.getIdentifier() != null) fragments.add(referenceSequence.getIdentifier());
                if (referenceSequence.getGeneName() != null && !referenceSequence.getGeneName().isEmpty())
                    fragments.addAll(referenceSequence.getGeneName());
                if (referenceSequence.getOtherIdentifier() != null && !referenceSequence.getOtherIdentifier().isEmpty())
                    fragments.addAll(referenceSequence.getOtherIdentifier());
                if (referenceSequence instanceof ReferenceGeneProduct) {
                    ReferenceGeneProduct rgp = (ReferenceGeneProduct) referenceSequence;
                    List<ReferenceDNASequence> referenceGene = rgp.getReferenceGene();
                    if (referenceGene == null) continue;
                    for (ReferenceDNASequence referenceDNASequence : referenceGene) {
                        if (referenceDNASequence.getIdentifier() != null)
                            fragments.add(referenceDNASequence.getIdentifier());
                        if (referenceDNASequence.getGeneName() != null && !referenceDNASequence.getGeneName().isEmpty())
                            fragments.addAll(referenceDNASequence.getGeneName());
                        if (referenceDNASequence.getOtherIdentifier() != null && !referenceDNASequence.getOtherIdentifier().isEmpty())
                            fragments.addAll(referenceDNASequence.getOtherIdentifier());
                    }
                }
            }
        }
        if (!fragments.isEmpty()) document.setFragmentModification(fragments);
    }

    private String getReferenceTypes(ReferenceEntity referenceEntity) {
        if (referenceEntity instanceof ReferenceGeneProduct) {
            return "Protein";
        } else if (referenceEntity instanceof ReferenceDNASequence) {
            return "DNA Sequence";
        } else if (referenceEntity instanceof ReferenceRNASequence) {
            return "RNA Sequence";
        } else if (referenceEntity instanceof ReferenceMolecule || referenceEntity instanceof ReferenceGroup) {
            return "Chemical Compound";
        } else if (referenceEntity instanceof ReferenceTherapeutic) {
            return "Drug";
        } else {
            return referenceEntity.getSchemaClass();
        }
    }

    private List<String> getReferenceIdentifiers(List<? extends ReferenceEntity> referenceEntities) {
        Set<String> identifiers = new TreeSet<>();
        if (referenceEntities == null) return new LinkedList<>();
        for (ReferenceEntity referenceEntity : referenceEntities) {
            Stream.concat(
                    referenceEntity.getReferenceDatabase().getName().stream().filter(s -> !s.contains(" ")),
                    Stream.of(referenceEntity.getSimplifiedDatabaseName())
            ).forEach(dbName -> identifiers.add(dbName + ':' + referenceEntity.getIdentifier()));

            identifiers.add(referenceEntity.getIdentifier());
        }
        return new LinkedList<>(identifiers);
    }

    private void setReferenceOtherIdentifiers(IndexDocument document, List<String> otherIdentifiers) {
        if (otherIdentifiers == null) return;
        Set<String> fullOtherIdentifiers = new TreeSet<>(otherIdentifiers);
        // If other identifier of shape "<db>:<id>", index as well <id>
        for (String otherIdentifier : otherIdentifiers) {
            Matcher matcher = DB_ID_PATTERN.matcher(otherIdentifier);
            if (matcher.find()) fullOtherIdentifiers.add(matcher.group("id"));
        }

        document.setReferenceOtherIdentifier(new LinkedList<>(fullOtherIdentifiers));
    }

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

    private void setCatalystActivities(IndexDocument document, List<CatalystActivity> catalystActivities) {
        if (catalystActivities == null) return;

        for (CatalystActivity catalystActivity : catalystActivities) {
            setGoTerms(document, catalystActivity.getActivity());
        }
    }

    /**
     * Generic Type, the one we have in the facets.
     *
     * @return type
     */
    private String getType(DatabaseObject databaseObject) {
        if (databaseObject instanceof EntitySet) {
            // Any instance of CandidateSet, DefinedSet, OpenSet
            return "Set";
        } else if (databaseObject instanceof GenomeEncodedEntity) {
            // Any instance of GenomeEncodedEntity is setting the type based on its Reference
            return "Genes and Transcripts";
        } else if (databaseObject instanceof Pathway) {
            // Also covering TopLevelPathway
            return "Pathway";
        } else if (databaseObject instanceof ReactionLikeEvent) {
            // Also covering BlackBoxEvent, (De)Polymerisation, (Failed)Reaction
            return "Reaction";
        } else {
            return databaseObject.getSchemaClass();
        }
    }

    private void setDiagramOccurrences(IndexDocument document, DatabaseObject databaseObject) {
        Collection<DiagramOccurrences> dgoc = diagramService.getDiagramOccurrences(databaseObject.getStId());
        if (dgoc == null || dgoc.isEmpty()) return;

        List<String> diagrams = new ArrayList<>();
        List<String> occurrences = new ArrayList<>();
        //noinspection Duplicates
        for (DiagramOccurrences diagramOccurrence : dgoc) {

            diagrams.add(diagramOccurrence.getDiagramStId());

            String occurr = diagramOccurrence.getDiagramStId() + ":" + diagramOccurrence.isInDiagram();
            if (diagramOccurrence.getOccurrences() != null && !diagramOccurrence.getOccurrences().isEmpty()) {
                occurr = occurr + ":" + StringUtils.join(diagramOccurrence.getOccurrences(), ",");
            } else {
                occurr = occurr + ":#"; // no occurrences, using one char so less bytes in the solr index
            }
            if (diagramOccurrence.getInteractsWith() != null && !diagramOccurrence.getInteractsWith().isEmpty()) {
                occurr = occurr + ":" + StringUtils.join(diagramOccurrence.getInteractsWith(), ",");
            } else {
                occurr = occurr + ":#"; // empty interactsWith, using one char so less bytes in the solr index
            }
            occurrences.add(occurr);
        }
        document.setDiagrams(diagrams);
        document.setOccurrences(occurrences);
    }

    private void setLowerLevelPathways(IndexDocument document, DatabaseObject databaseObject) {
        Collection<Pathway> pathways = pathwaysService.getLowerLevelPathwaysIncludingEncapsulation(databaseObject.getStId());
        if (pathways == null || pathways.isEmpty()) return;

        document.setLlps(pathways.stream().map(DatabaseObject::getStId).collect(Collectors.toList()));
    }

    /**
     * Keyword rely on document.getName. Make sure you are invoking document.setName before setting the keywords
     *
     * @param document solr document
     */
    private void setKeywords(IndexDocument document) {
        if (keywords == null) return;

        // TODO: Flo says the way it is implemented is not nice. Right we check into a static file with defined vocabulary. Would be nice if we check which reactions are in a bind reaction e.g and then add it as keyword.
        document.setKeywords(keywords.stream().filter(keyword -> document.getName().toLowerCase().contains(keyword.toLowerCase())).collect(Collectors.toList()));
    }

    /**
     * Load a file that is present in classpath
     *
     * @return the content file in a List of String
     */
    private List<String> loadFile(String fileName) {
        try {
            List<String> list = new ArrayList<>();
            InputStream resourceAsStream = getClass().getResourceAsStream("/" + fileName);
            if (resourceAsStream == null) return null;
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resourceAsStream));
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

    @Autowired
    public void setDatabaseObjectService(DatabaseObjectService databaseObjectService) {
        this.databaseObjectService = databaseObjectService;
    }

    @Autowired
    public void setAdvancedDatabaseObjectService(AdvancedDatabaseObjectService advancedDatabaseObjectService) {
        this.advancedDatabaseObjectService = advancedDatabaseObjectService;
    }

    @Autowired
    public void setDiagramService(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    @Autowired
    public void setPathwaysService(PathwaysService pathwaysService) {
        this.pathwaysService = pathwaysService;
    }
}
