package org.reactome.server.tools.indexer.util;

import org.reactome.server.interactors.util.MapSet;

import java.util.Collections;
import java.util.Set;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class IndexerMapSet<K, V> extends MapSet<K, V> {

    @Override
    public Set<V> getElements(K identifier) {
        Set<V> v = this.map.get(identifier);
        if (v == null) {
            return Collections.emptySet();
        }
        return v;
    }
}
