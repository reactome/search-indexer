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
        document.setName(icon.getName()); // as it is named in the file
        document.setSummation(icon.getDescription());
        document.setIconName(icon.getName().replaceAll("_", " ")); // human readable name
        document.setIconGroup(icon.getGroup());
        document.setType(icon.getType());
        document.setExactType(icon.getType());
        document.setSpecies(Collections.singletonList(icon.getSpecies()));
        document.setIconEhlds(icon.getEhlds());
        document.setIconXRefs(icon.getXrefs());

        // SET STIDS
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
            } else {
                int i = 10;
                System.out.println(i);
                // TODO what todo ?!
            }
        }

        return document;
    }
}
