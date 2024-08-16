package org.reactome.server.tools.indexer.deleted.impl;

import org.reactome.server.graph.domain.model.Deleted;
import org.reactome.server.graph.domain.model.DeletedInstance;
import org.reactome.server.graph.domain.result.DatabaseObjectLike;
import org.reactome.server.tools.indexer.deleted.model.DeletedDocument;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeletedDocumentBuilder {

    public List<DeletedDocument> createDocuments(Deleted deleted) {

        return deleted.getDeletedInstance().stream()
                .map(deletedInstance -> DeletedDocument.builder()
                        .dbId(deletedInstance.getDeletedInstanceDbId())
                        .stId(deletedInstance.getDeletedStId())
                        .reason(deleted.getReason() != null ? deleted.getReason().getDisplayName() : null)
                        .explanation(getExplanation(deleted, deletedInstance))
                        .name(deletedInstance.getName())
                        .date(deleted.getCreated() != null ? deleted.getCreated().getDateTime() : null)
                        .exactType(deletedInstance.getClazz())
                        .replacementDbIds(deleted.getReplacementInstances().stream().map(DatabaseObjectLike::getDbId).collect(Collectors.toList()))
                        .replacementStIds(deleted.getReplacementInstances().stream().map(DatabaseObjectLike::getStId).collect(Collectors.toList()))
                        .build()
                )
                .filter(deletedDocument -> deletedDocument.getName() != null)
                .collect(Collectors.toList());

    }

    private String getExplanation(Deleted deleted, DeletedInstance deletedInstance) {
        if (deletedInstance.getExplanation() != null) return deletedInstance.getExplanation();
        if (deleted.getExplanation() != null) return deleted.getExplanation();
        if (deleted.getReason() == null) return null;
        if (deleted.getReason().getExplanation() != null) return deleted.getReason().getExplanation();
        if (deleted.getReason().getDefinition() != null) return deleted.getReason().getDefinition();
        return null;
    }
}
