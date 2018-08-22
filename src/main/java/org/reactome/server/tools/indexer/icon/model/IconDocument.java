package org.reactome.server.tools.indexer.icon.model;

import org.apache.solr.client.solrj.beans.Field;
import org.reactome.server.tools.indexer.model.IndexDocument;

import java.util.List;
import java.util.Set;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

public class IconDocument extends IndexDocument {

    @Field
    private String iconName;
    @Field
    private String iconGroup;
    @Field
    private String iconCuratorName;
    @Field
    private String iconCuratorOrcidId;
    @Field
    private String iconCuratorUrl;
    @Field
    private String iconDesignerName;
    @Field
    private String iconDesignerOrcidId;
    @Field
    private String iconDesignerUrl;
    @Field
    private List<String> iconCVTerms;
    @Field
    private List<String> iconReferences;
    @Field
    private Set<String> iconStIds;
    @Field
    private List<String> iconEhlds;

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public String getIconGroup() {
        return iconGroup;
    }

    public void setIconGroup(String iconGroup) {
        this.iconGroup = iconGroup;
    }

    public String getIconCuratorName() {
        return iconCuratorName;
    }

    public void setIconCuratorName(String iconCuratorName) {
        this.iconCuratorName = iconCuratorName;
    }

    public String getIconCuratorOrcidId() {
        return iconCuratorOrcidId;
    }

    public void setIconCuratorOrcidId(String iconCuratorOrcidId) {
        this.iconCuratorOrcidId = iconCuratorOrcidId;
    }

    public String getIconCuratorUrl() {
        return iconCuratorUrl;
    }

    public void setIconCuratorUrl(String iconCuratorUrl) {
        this.iconCuratorUrl = iconCuratorUrl;
    }

    public String getIconDesignerName() {
        return iconDesignerName;
    }

    public void setIconDesignerName(String iconDesignerName) {
        this.iconDesignerName = iconDesignerName;
    }

    public String getIconDesignerOrcidId() {
        return iconDesignerOrcidId;
    }

    public void setIconDesignerOrcidId(String iconDesignerOrcidId) {
        this.iconDesignerOrcidId = iconDesignerOrcidId;
    }

    public String getIconDesignerUrl() {
        return iconDesignerUrl;
    }

    public void setIconDesignerUrl(String iconDesignerUrl) {
        this.iconDesignerUrl = iconDesignerUrl;
    }

    public List<String> getIconCVTerms() {
        return iconCVTerms;
    }

    public void setIconCVTerms(List<String> iconCVTerms) {
        this.iconCVTerms = iconCVTerms;
    }

    public List<String> getIconReferences() {
        return iconReferences;
    }

    public void setIconReferences(List<String> iconReferences) {
        this.iconReferences = iconReferences;
    }

    public Set<String> getIconStIds() {
        return iconStIds;
    }

    public void setIconStIds(Set<String> iconStIds) {
        this.iconStIds = iconStIds;
    }

    public List<String> getIconEhlds() {
        return iconEhlds;
    }

    public void setIconEhlds(List<String> iconEhlds) {
        this.iconEhlds = iconEhlds;
    }
}
