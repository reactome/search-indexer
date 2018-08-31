package org.reactome.server.tools.indexer.icon.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.reactome.server.tools.indexer.icon.model.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class IconDocumentBuilder {

    private String solrCore;
    private SolrClient solrClient;

    IconDocumentBuilder(String solrCore, SolrClient solrClient) {
        this.solrCore = solrCore;
        this.solrClient = solrClient;
    }

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

        if (icon.getReferences() != null) {
            // adding Ids only
            List<String> refIds = icon.getReferences().stream().map(Reference::getId).collect(Collectors.toList());
            // then adding db:Id. This is how referenceIdentifiers are stored
            refIds.addAll(icon.getReferences().stream().map(Reference::toString).collect(Collectors.toList()));
            document.setIconReferences(refIds);
            document.setIconPhysicalEntity(getIconPhysicalEntities(icon.getReferences()));
        }

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

    /**
     * Based on the annotated references in the XMLs
     * we will search them in the Reactome core and retrieve the ST_ID, NAME, EXACT_TYPE, COMPARTMENT.
     *
     * @return stId(s) mapping the icon and the physical entities
     */
    private Set<String> getIconPhysicalEntities(List<Reference> references) {
        Set<String> ret = new HashSet<>();
        SolrQuery query = new SolrQuery();
        query.setRequestHandler("/iconPEStId");
        query.setQuery(StringUtils.join(references, " OR "));
        query.setRows(300);
        query.setFields("stId, name, exactType, compartmentName");
        try {
            QueryResponse response = solrClient.query(solrCore, query);
            SolrDocumentList solrDocument = response.getResults();
            if (solrDocument != null && !solrDocument.isEmpty()) {
                /* if query returns more than 300 docs, query all of them */
                if (solrDocument.getNumFound() > 300) {
                    query.setRows(((Long) solrDocument.getNumFound()).intValue());
                    response = solrClient.query(solrCore, query);
                    solrDocument = response.getResults();
                }
                solrDocument.forEach(doc -> {
                    String stId = (String) doc.getFieldValue("stId");
                    String name = (String) doc.getFieldValue("name");
                    String type = (String) doc.getFieldValue("exactType");
                    String compartments = "";
                    Collection<Object> compartmentsList = doc.getFieldValues("compartmentName");
                    if (compartmentsList != null && !compartmentsList.isEmpty()) {
                        compartments = compartmentsList.stream().map(Object::toString).collect(Collectors.joining(", "));
                    }

                    // stIds is multivalued field, additionally we add name and type.
                    ret.add(stId + "#" + type + "#" + name + "#" + compartments);
                });
            }
        } catch (SolrServerException | IOException e) {
            // nothing here
        }
        return ret;
    }
}
