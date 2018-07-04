package org.reactome.server.tools.indexer.icon.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

@XmlRootElement(name = "metadata")
public class Icon {
    private Long id;
    private String name;
    private String group;
    private String type = "Icon";
    private String species;
    private String description;
    private List<CVTerm> terms;
    private List<String> xrefs;
    private List<Person> person;
    private List<String> ehlds;
    private List<String> stIds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
        setSpecies(group);
    }

    public String getDescription() {
        return description;
    }

    @XmlElement
    public void setDescription(String description) {
        this.description = description;
    }

    public List<Person> getPerson() {
        return person;
    }

    @XmlElement(name="person")
    public void setPerson(List<Person> person) {
        this.person = person;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSpecies() {
        return species;
    }

    /**
     * Species are set programatically based on the given group
     * @param group the folder that contains the icon
     */
    private void setSpecies(String group) {
        if (group == null || group.isEmpty()) throw new IllegalArgumentException("Could assign species. Invalid group");
        this.species = "Homo sapiens";
        if (group.equalsIgnoreCase("arrows") || group.equalsIgnoreCase("compounds")) {
            this.species = "Entries without species";
        }
    }

    public List<CVTerm> getTerms() {
        return terms;
    }


    @XmlElementWrapper(name="cvterms")
    @XmlElement(name="cvterm")
    public void setTerms(List<CVTerm> terms) {
        this.terms = terms;
    }

    public List<String> getXrefs() {
        return xrefs;
    }

    public void setXrefs(List<String> xrefs) {
        this.xrefs = xrefs;
    }

    public List<String> getEhlds() {
        return ehlds;
    }

    public void setEhlds(List<String> ehlds) {
        this.ehlds = ehlds;
    }

    public List<String> getStIds() {
        return stIds;
    }

    public void setStIds(List<String> stIds) {
        this.stIds = stIds;
    }
}
