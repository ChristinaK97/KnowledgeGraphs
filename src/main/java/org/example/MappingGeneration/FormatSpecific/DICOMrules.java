package org.example.MappingGeneration.FormatSpecific;

import org.example.MappingGeneration.Matches;
import org.example.MappingGeneration.Ontology;
import tech.tablesaw.api.Table;

import java.util.ArrayList;
import java.util.Collections;

public class DICOMrules extends FormatSpecificRules {

    public DICOMrules() {}

    public void addAdditionalMatches(Ontology dicomPO, Ontology dicomDO, Matches matches) {
        String prefix = dicomPO.getBasePrefix();
        String queryString =
                Ontology.swPrefixes()
                + "SELECT (?columnClass as ?colClassURI)  (?label as ?tagName) WHERE {\n"
                + "    ?tableClass rdfs:subClassOf <" + prefix + "TableClass> , \n"
                + "                                [a owl:Restriction ; owl:onProperty ?pureObjProp] .\n"
                + "    ?columnClass rdfs:subClassOf <" + prefix + "TableClass> ; \n"
                + "                 skos:altLabel ?label . \n"
                + "    ?pureObjProp a owl:ObjectProperty ; \n"
                + "                 rdfs:subPropertyOf <" + prefix + "FKProperty> ; \n"
                + "                 rdfs:range ?columnClass . \n"
                + "}";
        System.out.println(queryString);
        String[] vars = new String[] {"colClassURI", "tagName"};
        Table table = dicomPO.runQuery(queryString, vars);

        table.forEach(row -> {

            String colClassURI = row.getString("colClassURI");
            String tagName = row.getString("tagName");
            System.out.println("colClassURI: " + colClassURI + ", tagName: " + tagName);

            ArrayList<String> path = new ArrayList<>(Collections.singleton("http://semantic-dicom.org/seq#hasItem"));
            String item = dicomDO.getBasePrefix() + tagName.replaceAll(" ", "") + "Item";

            System.out.println(item);

            if(dicomDO.getOntClass(item) != null) {
                System.out.println("exists");
                path.add(item);
            }else {
                System.out.println("not exists");
                String newItemClass = colClassURI + "Item";
                dicomPO.createClass(
                        newItemClass ,
                        tagName + " Item",
                        "Unknown Sequence Tag Item"
                );
                path.add(newItemClass);
            }
            matches.setPath(colClassURI, path);
        });
    }
}
