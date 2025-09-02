package org.reactome.server.tools.indexer.icon.model;

import jakarta.xml.bind.annotation.*;
import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

@XmlRootElement(name = "metadata")
@XmlType(propOrder={"categories","person","name","description","info","references","synonyms","skip"})
@Data
public class Icon {
    private String id;
    private String stId;
    private String name;
    private String type = "Icon";
    private String species;
    private String description;
    private String info = "https://reactome.org/icon-info";
    private List<Category> categories;
    private List<Reference> references;
    private List<Person> person;
    private List<String> ehlds;
    private List<Synonym> synonyms;
    private Boolean skip;

    @XmlTransient
    public void setId(String id) {
        this.id = id;
    }

    @XmlTransient
    public void setStId(String stId) {
        this.stId = stId;
    }

    @XmlTransient
    public String getType() {
        return type;
    }

    @XmlTransient
    public String getSpecies() {
        return species;
    }

    @XmlElement
    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement
    public void setInfo(String info) {
        this.info = info;
    }

    @XmlElementWrapper(name = "categories")
    @XmlElement(name = "category")
    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    @XmlElementWrapper(name = "references")
    @XmlElement(name = "reference")
    public void setReferences(List<Reference> references) {
        this.references = references;
    }

    @XmlElement(name = "person")
    public void setPerson(List<Person> person) {
        this.person = person;
    }

    @XmlTransient
    public List<String> getEhlds() {
        return ehlds;
    }

    @XmlElementWrapper(name = "synonyms")
    @XmlElement(name = "synonym")
    public void setSynonyms(List<Synonym> synonyms) {
        this.synonyms = synonyms;
    }

    @XmlElement
    public Boolean isSkip() {
        return skip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Icon icon = (Icon) o;
        return Objects.equals(getStId(), icon.getStId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getStId());
    }
}
