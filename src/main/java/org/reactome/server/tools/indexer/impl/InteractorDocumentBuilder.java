package org.reactome.server.tools.indexer.impl;

import org.reactome.server.graph.domain.model.*;
import org.reactome.server.tools.indexer.model.IndexDocument;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@Service
class InteractorDocumentBuilder {
    private static final String TYPE = "Interactor";

    IndexDocument createInteractorSolrDocument(ReferenceEntity interactor) {
        IndexDocument document = new IndexDocument();
        document.setDbId(interactor.getIdentifier()); // For interactors, dbId is the accession.
        document.setName(getName(interactor)); // is mandatory
        document.setType(TYPE);
        document.setExactType(TYPE);

        document.setReferenceName(getReferenceName(interactor));
        document.setReferenceGeneNames(getGeneName(interactor));
        document.setReferenceSynonyms(getSecondaryIdentifier(interactor));
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

        setFireworksSpecies(document, interactor);

        return document;
    }

    @SuppressWarnings("unchecked")
    private List<String> getSecondaryIdentifier(DatabaseObject databaseObject) {
        try {
            return (List<String>) databaseObject.getClass().getMethod("getSecondaryIdentifier").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> getGeneName(DatabaseObject databaseObject) {
        try {
            return (List<String>) databaseObject.getClass().getMethod("getGeneName").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getVariantIdentifier(DatabaseObject databaseObject) {
        try {
            // in cases where databaseObject is instanceof ReferenceIsoform, use variantIdentifier as the main identifier
            return (String) databaseObject.getClass().getMethod("getVariantIdentifier").invoke(databaseObject);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getSpeciesName(DatabaseObject databaseObject) {
        try {
            return ((Species) databaseObject.getClass().getMethod("getSpecies").invoke(databaseObject)).getDisplayName();
        } catch (NullPointerException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // Nothing here
        }
        return "Entries without species";
    }

    private String getName(ReferenceEntity interactor) {
        List<String> names = interactor.getName();
        if (names != null && !names.isEmpty()) return names.get(0);

        return interactor.getIdentifier();
    }

    private String getReferenceName(ReferenceEntity interactor) {
        List<String> names = interactor.getName();
        if (names != null && !names.isEmpty()) return names.get(0);

        return null;
    }

    @SuppressWarnings("Duplicates")
    private void setFireworksSpecies(IndexDocument document, ReferenceEntity interactor) {
        Set<String> fireworksSpecies = new HashSet<>();
        try {
            Method getSpecies = interactor.getClass().getMethod("getSpecies");
            Object species = getSpecies.invoke(interactor);
            // some cases like DefinedSet it has species as an attribute but it does not have value in it.
            if (species != null) {
                if (species instanceof Collection) {
                    //noinspection Convert2streamapi,unchecked
                    for (Taxon t : (Collection<? extends Taxon>) species) {
                        fireworksSpecies.add(t.getDisplayName());
                    }
                } else {
                    Taxon t = (Taxon) species;
                    fireworksSpecies.add(t.getDisplayName());
                }
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            //Nothing here
        }

        document.setFireworksSpecies(fireworksSpecies.isEmpty() ? null : fireworksSpecies);
    }
}
