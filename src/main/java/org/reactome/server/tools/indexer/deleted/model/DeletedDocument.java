package org.reactome.server.tools.indexer.deleted.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.apache.solr.client.solrj.beans.Field;

import java.util.List;

@Data
@Builder
@ToString
public class DeletedDocument {
    @Field
    private String stId;
    @Field
    private Integer dbId;
    @Field
    private String name;
    @Field
    @Builder.Default
    private String type = "deleted";
    @Field
    private String exactType;
    @Field
    private String reason;
    @Field
    private String explanation;
    @Field
    private String date;
    @Field
    private List<Long> replacementDbIds;
    @Field
    private List<String> replacementStIds;
}
