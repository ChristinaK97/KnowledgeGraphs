package org.example.MappingGeneration.FormatSpecific;

import org.apache.jena.ontology.OntResource;
import org.example.MappingGeneration.Matches;
import org.example.util.Ontology;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.util.ArrayList;

public interface FormatSpecificRules {

    ArrayList<OntResource> newElements = new ArrayList<>();

    void addAdditionalMatches(Ontology srcOnto, Ontology trgOnto, Matches matches);

    ArrayList<Table> getNewMappings();
}
