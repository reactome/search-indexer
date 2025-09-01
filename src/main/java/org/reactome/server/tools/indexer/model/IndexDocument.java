package org.reactome.server.tools.indexer.model;

import lombok.Data;
import org.apache.solr.client.solrj.beans.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JAVA BEAN representing the Document that is going to be Indexed into Solr
 * Field marks a Solr Field
 *
 * @author Florian Korninger (fkorn@ebi.ac.uk) on 4/29/14.
 * @author Guilherme Viteri (gviteri@ebi.ac.uk)
 */
@SuppressWarnings("unused")
@Data
public class IndexDocument {

    @Field
    private String dbId;
    @Field
    private String stId;
    @Field
    private String oldStId;
    @Field
    private String name;
    @Field
    private String type;
    @Field
    private String exactType;
    @Field
    private Boolean isDisease;
    @Field
    private Boolean isReferenceSummary;
    @Field
    private Boolean hasReferenceEntity;
    @Field
    private Boolean hasEHLD;

    @Field
    private List<String> diseaseId;
    @Field
    private List<String> diseaseName;
    @Field
    private List<String> diseaseSynonyms;

    @Field
    private List<String> species;
    @Field
    private List<String> relatedSpecies;

    // Not in Solr, but used in the ebeye.xml file
    private List<String> taxId;

    @Field
    private List<String> synonyms;
    @Field
    private String summation;

    @Field
    private String inferredSummation;
    @Field
    private List<String> compartmentName;
    @Field
    private List<String> compartmentAccession;

    @Field
    private List<String> literatureReferenceTitle;
    @Field
    private List<String> literatureReferenceAuthor;
    @Field
    private List<String> literatureReferencePubMedId;
    @Field
    private List<String> literatureReferenceIsbn;

    @Field
    private String goBiologicalProcessName;
    @Field
    private List<String> goBiologicalProcessAccessions;

    @Field
    private String goCellularComponentName;
    @Field
    private List<String> goCellularComponentAccessions;

    @Field
    private List<String> goMolecularFunctionName;
    @Field
    private List<String> goMolecularFunctionAccession;

    // Reference Entity
    @Field
    private List<String> keywords;

    @Field
    private List<String> crossReferences;

    @Field
    private List<String> referenceCrossReferences;

    private List<CrossReference> allCrossReferences;

    @Field
    private String referenceName;
    @Field
    private List<String> referenceIdentifiers;
    @Field
    private List<String> referenceDNAIdentifiers;
    @Field
    private List<String> referenceRNAIdentifiers;
    @Field
    private String referenceURL;
    @Field
    private String databaseName;

    @Field
    private List<String> referenceSynonyms;

    @Field
    private List<String> referenceOtherIdentifier;
    @Field
    private List<String> referenceSecondaryIdentifier;
    @Field
    private List<String> referenceGeneNames;
    @Field
    private Set<String> fragmentModification;

    @Field
    private Set<String> fireworksSpecies;

    @Field
    private List<String> physicalEntitiesDbId;

    @Field
    private List<String> diagrams;

    @Field
    private List<String> diagramsWithInteractor;

    @Field
    private List<String> occurrences;

    @Field
    private List<String> occurrencesWithInteractor;


    @Field
    private List<String> llps; // lower level pathways ( for flagging in the Fireworks

    @Field
    private String orcidId;

    @Field
    private Long authoredPathways;

    @Field
    private Long authoredReactions;

    @Field
    private Long reviewedPathways;

    @Field
    private Long reviewedReactions;

    private boolean covidRelated;

    public void setAllCrossReferences(List<CrossReference> allCrossReferences) {
        if (this.allCrossReferences == null) {
            this.allCrossReferences = allCrossReferences;
        } else {
            this.allCrossReferences.addAll(allCrossReferences);
        }
    }

    public void addGoMolecularFunctionName(String gmfName) {
        if (this.goMolecularFunctionName == null) {
            this.goMolecularFunctionName = new ArrayList<>();
        }
        this.goMolecularFunctionName.add(gmfName);
    }

    public void addGoMolecularFunctionAccession(String gmfAccession) {
        if (this.goMolecularFunctionAccession == null) {
            this.goMolecularFunctionAccession = new ArrayList<>();
        }
        this.goMolecularFunctionAccession.add(gmfAccession);
    }
}