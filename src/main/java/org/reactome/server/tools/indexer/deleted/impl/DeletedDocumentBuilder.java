package org.reactome.server.tools.indexer.deleted.impl;

import org.apache.solr.common.util.SimpleOrderedMap;
import org.reactome.server.graph.domain.model.*;
import org.reactome.server.graph.domain.result.DatabaseObjectLike;
import org.reactome.server.tools.indexer.deleted.model.DeletedDocument;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeletedDocumentBuilder {
    public static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Map<String, String> exactTypeToType = new HashMap<>();

    static {
        Map<Class<? extends DatabaseObject>, String> classToType = new LinkedHashMap<>();
        classToType.put(GenomeEncodedEntity.class, "Genes and Transcripts");
        classToType.put(EntityWithAccessionedSequence.class, "Entity");
        classToType.put(EntitySet.class, "Set");
        classToType.put(Complex.class, "Complex");
        classToType.put(ReactionLikeEvent.class, "Reaction");
        classToType.put(Pathway.class, "Pathway");

        Reflections reflections = new Reflections("org.reactome.server.graph.domain.model");
        classToType.forEach((key, value) -> {
            exactTypeToType.put(key.getSimpleName(), value);
            reflections.getSubTypesOf(key).forEach(type -> exactTypeToType.put(type.getSimpleName(), value));
        });
    }

    public List<DeletedDocument> createDocuments(Deleted deleted) {

        return deleted.getDeletedInstance().stream()
                .filter(deletedInstance -> deletedInstance.getDeletedStId() != null)
                .map(deletedInstance -> DeletedDocument.builder()
                        .dbId(deletedInstance.getDeletedInstanceDbId())
                        .stId(deletedInstance.getDeletedStId())
                        .type(getType(deletedInstance))
                        .reason(deleted.getReason() != null ? deleted.getReason().getDisplayName() : null)
                        .explanation(getExplanation(deleted))
                        .name(deletedInstance.getName())
                        .date(getDate(deleted))
                        .exactType(deletedInstance.getClazz())
                        .replacementDbIds(deleted.getReplacementInstances().stream().map(DatabaseObjectLike::getDbId).collect(Collectors.toList()))
                        .replacementStIds(deleted.getReplacementInstances().stream().map(DatabaseObjectLike::getStId).collect(Collectors.toList()))
                        .build()
                )
                .filter(deletedDocument -> deletedDocument.getName() != null)
                .collect(Collectors.toList());

    }

    private String getExplanation(Deleted deleted) {
        if (deleted.getReason() == null) return null;
        if (deleted.getReason().getDefinition() != null) return deleted.getReason().getDefinition();
        if (deleted.getCuratorComment() != null) return deleted.getCuratorComment();
        return null;
    }

    private String getType(DeletedInstance deletedInstance) {
        return exactTypeToType.getOrDefault(deletedInstance.getClazz(), deletedInstance.getClazz());
    }

    private String getDate(Deleted deleted) {
        if (deleted.getCreated() == null) return null;
        // Parse the input string to LocalDateTime
        LocalDateTime dateTime = LocalDateTime.parse(deleted.getCreated().getDateTime(), INPUT_FORMATTER);
        // Convert LocalDateTime to Instant (assuming UTC as the zone offset)
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);

        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
