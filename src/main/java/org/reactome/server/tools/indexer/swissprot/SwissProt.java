package org.reactome.server.tools.indexer.swissprot;

import java.util.List;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class SwissProt {
    private String id;
    private List<String> accession;
    private String geneName;
    private List<String> synonyms;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getAccession() {
        return accession;
    }

    public void setAccession(List<String> accession) {
        this.accession = accession;
    }

    public String getGeneName() {
        return geneName;
    }

    public void setGeneName(String geneName) {
        this.geneName = geneName;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    public void setSynonyms(List<String> synonyms) {
        this.synonyms = synonyms;
    }
}