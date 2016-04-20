package org.reactome.server.tools.indexer.model;

public class CrossReference {
    private String dbName;
    private String id;

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
