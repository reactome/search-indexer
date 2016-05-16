package org.reactome.server.tools.indexer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that holds information coming from Reactome
 * It means a list of ReactomeStableIdentifier and the Name
 * This is needed when we associate an accession that interacts with a
 * protein in Reactome, then we have the StId and name
 *
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class ReactomeSummary {

    private List<String> reactomeId;
    private List<String> reactomeName;

    public ReactomeSummary() {}

    public List<String> getReactomeName() {
        return reactomeName;
    }

    public List<String> getReactomeId() {
        return reactomeId;
    }

    public void addId(String id){
        if(reactomeId == null){
            reactomeId = new ArrayList<>();
        }
        reactomeId.add(id);
    }

    public void addName(String name){
        if(reactomeName == null){
            reactomeName = new ArrayList<>();
        }
        reactomeName.add(name);
    }
}
