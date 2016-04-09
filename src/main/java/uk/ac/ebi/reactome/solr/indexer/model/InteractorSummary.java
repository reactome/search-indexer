package uk.ac.ebi.reactome.solr.indexer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class
 *
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class InteractorSummary {

    private ReactomeSummary reactomeSummary;
    private String accession;
    private Double score;
    private List<String> interactionEvidences;

    public ReactomeSummary getReactomeSummary() {
        return reactomeSummary;
    }

    public void setReactomeSummary(ReactomeSummary reactomeSummary) {
        this.reactomeSummary = reactomeSummary;
    }

    public String getAccession() {
        return accession;
    }

    public void setAccession(String accession) {
        this.accession = accession;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public List<String> getInteractionEvidences() {
        return interactionEvidences;
    }

    public void addInteractionEvidences(String name){
        if(interactionEvidences == null){
            interactionEvidences = new ArrayList<>();
        }
        interactionEvidences.add(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InteractorSummary summary = (InteractorSummary) o;

        return !(accession != null ? !accession.equals(summary.accession) : summary.accession != null);

    }

    @Override
    public int hashCode() {
        return accession != null ? accession.hashCode() : 0;
    }
}
