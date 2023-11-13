package org.example.D_MappingGeneration.FormatSpecific;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.example.D_MappingGeneration.Matches;
import org.example.util.Annotations;
import org.example.util.Ontology;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.example.B_InputDatasetProcessing.DICOM.DICOMUtil.*;
import static org.example.util.Ontology.getLocalName;

public class DICOMspecificRules implements FormatSpecificRules {

    public DICOMspecificRules() {}
    private String basePOprefix;
    private String baseDOprefix;

    // add additional matches to existing elements (their maps are included in the json template)
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
                        + "         ?tableClass rdfs:subClassOf <" + prefix + Annotations.TABLE_CLASS +  "> , \n"
                        + "                                      ?topClassWithTheRestriction ."
                        + "   } union { \n"
                        + "             ?tableClass rdfs:subClassOf <" + prefix + Annotations.TABLE_CLASS + "> , \n"
                        + "                                [a owl:Restriction ; owl:onProperty ?pureObjProp] .\n"
                        + "   }\n"
                        + "    ?columnClass rdfs:subClassOf <" + prefix + Annotations.TABLE_CLASS + "> ; \n"
                        + "                 skos:prefLabel ?label . \n"
                        + "    ?pureObjProp a owl:ObjectProperty ; \n"
                        + "                 rdfs:subPropertyOf <" + prefix + Annotations.PURE_OBJ_PROPERTY + "> ; \n"
                        + "                 rdfs:range ?columnClass . \n"
                        + "}";
        System.out.println(queryString);
        String[] vars = new String[] {"colClassURI", "tagName"};
        tech.tablesaw.api.Table table = dicomPO.runQuery(queryString, vars);                                                System.out.println(table);

        table.forEach(row -> {

            String colClassURI = row.getString("colClassURI");
            String tagName = row.getString("tagName");                                                                  System.out.println("colClassURI: " + colClassURI + ", tagName: " + tagName);

            ArrayList<String> path = new ArrayList<>(Collections.singleton(hasItemURI));
            String item = dicomDO.getBasePrefix() + tagName.replaceAll(" ", "") + "Item";                           System.out.println(item);

            if(dicomDO.getOntClass(item) != null) {                                                                                 System.out.println("exists");
                path.add(item);
            }else {                                                                                                                 System.out.println("not exists");
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
                basePOprefix + hasInformationEntity,
                baseDOprefix + hasInformationEntity,
                1.0
        );
    }



//==================================================================================================================

    // add matches for new elements (the json template didn't include maps for these elements before)
    // add these as new tables
    @Override
    public ArrayList<Table> getNewMappings() {
        ArrayList<Table> newMappings = new ArrayList<>();
        hasItemNewMappings(newMappings);
        topClassesMappings(newMappings);
        return newMappings;
    }


    private void hasItemNewMappings(ArrayList<Table> newMappings){
        for(OntResource newEl : newElements)
            if(newEl.canAs(OntClass.class)) {

                Table newTable = new Table(getLocalName(newEl.getURI()), null, null);
                newTable.setMapping(
                        new Mapping(
                                Annotations.CLASS_SUFFIX,
                                newEl.getURI(),
                                Collections.singletonList(URI.create(SequenceItemURI)),
                                null
                        ));
                newMappings.add(newTable);

            }else if(newEl.canAs(OntProperty.class)){
                //TODO
            }
    }

    private void topClassesMappings(ArrayList<Table> newMappings) {
        for(String topClass : new String[]{InformationEntity, InformationObjectDefinition}) {
            Table newTable = new Table(topClass, null, null);
            newTable.setMapping(
                    new Mapping(
                            Annotations.CLASS_SUFFIX,
                            basePOprefix + topClass,
                            Collections.singletonList(URI.create(baseDOprefix + topClass)),
                            null
                    ));
            newMappings.add(newTable);
        }
    }

//==================================================================================================================

    @Override
    public void modifyMappings(List<Table> tablesList) {}

}














