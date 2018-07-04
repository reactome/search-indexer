package org.reactome.server.tools.indexer.icon.model;

import javax.xml.bind.annotation.XmlElement;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class CVTerm {
    private String db;
    private String term;

    public String getDb() {
        return db;
    }

    @XmlElement
    public void setDb(String db) {
        this.db = db;
    }

    public String getTerm() {
        return term;
    }

    @XmlElement
    public void setTerm(String term) {
        this.term = term;
    }

    @Override
    public String toString() {
        return String.join(":", db, term);
    }
}

