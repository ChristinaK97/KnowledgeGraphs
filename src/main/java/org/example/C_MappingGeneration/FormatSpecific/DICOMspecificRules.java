package org.example.C_MappingGeneration.FormatSpecific;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.example.C_MappingGeneration.Matches;
import org.example.util.Ontology;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.util.ArrayList;
import java.util.Collections;

import static org.example.util.Ontology.getLocalName;

public class DICOMspecificRules implements FormatSpecificRules {

    public DICOMspecificRules() {}
    private String basePOprefix;
    private String baseDOprefix;

    @Override
    public void addAdditionalMatches(Ontology dicomPO, Ontology dicomDO, Matches matches) {
        this.basePOprefix = dicomPO.getBasePrefix();
        this.baseDOprefix = dicomDO.getBasePrefix();
        addHasItemPaths(dicomPO, dicomDO, matches);
        hasInformationEntityMatch(matches);
    }

    private void addHasItemPaths(Ontology dicomPO, Ontology dicomDO, Matches matches) {
        String prefix = dicomPO.getBasePrefix();
        String queryString =
                Ontology.swPrefixes()
                        + "\nSELECT (?columnClass as ?colClassURI)  (?label as ?tagName) WHERE {\n"
                        + "   {     ?topClassWithTheRestriction rdfs:subClassOf [a owl:Restriction ; owl:onProperty ?pureObjProp] .\n"
                        + "         ?tableClass rdfs:subClassOf <" + prefix + "TableClass> , \n"
                        + "                                      ?topClassWithTheRestriction ."
                        + "   } union { \n"
                        + "             ?tableClass rdfs:subClassOf <" + prefix + "TableClass> , \n"
                        + "                                [a owl:Restriction ; owl:onProperty ?pureObjProp] .\n"
                        + "   }\n"
                        + "    ?columnClass rdfs:subClassOf <" + prefix + "TableClass> ; \n"
                        + "                 skos:prefLabel ?label . \n"
                        + "    ?pureObjProp a owl:ObjectProperty ; \n"
                        + "                 rdfs:subPropertyOf <" + prefix + "PureProperty> ; \n"
                        + "                 rdfs:range ?columnClass . \n"
                        + "}";
        System.out.println(queryString);
        String[] vars = new String[] {"colClassURI", "tagName"};
        tech.tablesaw.api.Table table = dicomPO.runQuery(queryString, vars);
        System.out.println(table);

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
                newElements.add(
                        dicomPO.createClass(
                                newItemClass ,
                                tagName + " Item",
                                "Unknown Sequence Tag Item"
                        ));
                path.add(newItemClass);
            }
            matches.setPath(colClassURI, path);
        });
    }

    private void hasInformationEntityMatch(Matches matches) {
        matches.addMatch(
                basePOprefix + "hasInformationEntity",
                baseDOprefix + "hasInformationEntity",
                1.0
        );
    }



//==================================================================================================================

    @Override
    public ArrayList<Table> getNewMappings() {
        ArrayList<Table> newMappings = new ArrayList<>();
        hasItemNewMappings(newMappings);
        topClassesMappings (newMappings);
        return newMappings;
    }


    private void hasItemNewMappings(ArrayList<Table> newMappings){
        for(OntResource newEl : newElements)
            if(newEl.canAs(OntClass.class)) {

                Table newTable = new Table(getLocalName(newEl.getURI()));
                newTable.setMapping(
                        new Mapping(
                                "Class",
                                newEl.getURI(),
                                "http://semantic-dicom.org/seq#SequenceItem",
                                true,
                                null
                        ));
                newMappings.add(newTable);

            }else if(newEl.canAs(OntProperty.class)){
                //TODO
            }
    }

    private void topClassesMappings(ArrayList<Table> newMappings) {
        for(String topClass : new String[]{"InformationEntity", "InformationObjectDefinition"}) {
            Table newTable = new Table(topClass);
            newTable.setMapping(
                    new Mapping(
                            "Class",
                            basePOprefix + topClass,
                            baseDOprefix + topClass,
                            true,
                            null
                    ));
            newMappings.add(newTable);
        }
    }



}














