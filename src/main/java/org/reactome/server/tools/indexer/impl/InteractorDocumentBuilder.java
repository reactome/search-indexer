package org.reactome.server.tools.indexer.impl;

import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.ReferenceEntity;
import org.reactome.server.graph.domain.model.ReferenceIsoform;
import org.reactome.server.graph.domain.model.Species;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class InteractorDocumentBuilder {
    private static final String TYPE = "Interactor";

    IndexDocument createInteractorSolrDocument(ReferenceEntity interactor) {
        IndexDocument document = new IndexDocument();
        document.setDbId(interactor.getIdentifier()); // For interactors, dbId is the accession.
        document.setName(getName(interactor));
        document.setType(TYPE);
        document.setExactType(TYPE);
        document.setSynonyms(getSecondaryIdentifier(interactor));
        document.setReferenceIdentifiers(Collections.singletonList(interactor.getIdentifier()));
        document.setReferenceURL(interactor.getUrl());
        document.setDatabaseName(interactor.getDatabaseName());
        document.setSpecies(Collections.singletonList(getSpeciesName(interactor)));

        if (interactor instanceof ReferenceIsoform) {
            String variantIdentifier = getVariantIdentifier(interactor);
            if (variantIdentifier != null && !variantIdentifier.isEmpty()) {
                // For interactors, dbId is the accession, if Isoform then get variantIdentifier
                document.setDbId(variantIdentifier);
                document.setReferenceIdentifiers(Collections.singletonList(variantIdentifier));
            }
        }
        return document;
    }

    @SuppressWarnings("unchecked")
    private List<String> getSecondaryIdentifier(DatabaseObject databaseObject) {
        try {
            return (List<String>) databaseObject.getClass().getMethod("getSecondaryIdentifier").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getGeneName(DatabaseObject databaseObject) {
        try {
            return (List<String>) databaseObject.getClass().getMethod("getGeneName").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String getVariantIdentifier(DatabaseObject databaseObject) {
        try {
            // in cases where databaseObject is instanceof ReferenceIsoform, use variantIdentifier as the main identifier
            return (String) databaseObject.getClass().getMethod("getVariantIdentifier").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String getSpeciesName(DatabaseObject databaseObject) {
        try {
            return ((Species) databaseObject.getClass().getMethod("getSpecies").invoke(databaseObject)).getDisplayName();
        } catch (NullPointerException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private String getName(ReferenceEntity interactor) {
        String ret = "";
        List<String> geneName = getGeneName(interactor);
        if (geneName == null || geneName.isEmpty()) {
            List<String> names = interactor.getName();
            if (names == null || names.isEmpty()) {
                // IntAct entries - those starting with EBI- or isoforms
                ret = interactor.getIdentifier();
            }
        } else {
            ret = geneName.get(0);
        }
        return ret;
    }
}
