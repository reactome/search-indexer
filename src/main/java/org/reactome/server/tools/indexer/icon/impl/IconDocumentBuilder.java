package org.reactome.server.tools.indexer.icon.impl;

import org.reactome.server.tools.indexer.icon.model.CVTerm;
import org.reactome.server.tools.indexer.icon.model.Icon;
import org.reactome.server.tools.indexer.icon.model.IconDocument;
import org.reactome.server.tools.indexer.icon.model.Person;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class IconDocumentBuilder {

    IconDocument createIconSolrDocument(Icon icon) {
        IconDocument document = new IconDocument();
        document.setDbId(icon.getId().toString());
        document.setName(icon.getName().replaceAll("_", " ")); // using space as separator instead underscore
        document.setSummation(icon.getDescription());
        document.setIconName(icon.getName()); // we rely on the name to build file path and name may have solr highlighting class
        document.setIconGroup(icon.getGroup());
        document.setType(icon.getType());
        document.setExactType(icon.getType());
        document.setSpecies(Collections.singletonList(icon.getSpecies()));
        document.setIconEhlds(icon.getEhlds());
        document.setIconXRefs(icon.getXrefs());

        // TODO SET StIDS for those icons that are already an entry in Reactome.
        // document.setIconStIds(null);

        if (icon.getTerms() != null) {
            document.setIconCVTerms(icon.getTerms().stream().map(CVTerm::toString).collect(Collectors.toList()));
        }

        List<Person> iconPerson = icon.getPerson();
        for (Person person : iconPerson) {
            if (person.getRole().equalsIgnoreCase("curator")) {
                document.setIconCuratorName(person.getName());
                document.setIconCuratorOrcidId(person.getOrcidId());
                document.setIconCuratorUrl(person.getUrl());
            } else if (person.getRole().equalsIgnoreCase("designer")) {
                document.setIconDesignerName(person.getName());
                document.setIconDesignerOrcidId(person.getOrcidId());
                document.setIconDesignerUrl(person.getUrl());
            }
        }

        return document;
    }
}
