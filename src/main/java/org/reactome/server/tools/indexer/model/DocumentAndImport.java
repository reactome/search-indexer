package org.reactome.server.tools.indexer.model;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class DocumentAndImport {
    @Nullable
    public final IndexDocument document;
    public final boolean needsImport;
}
