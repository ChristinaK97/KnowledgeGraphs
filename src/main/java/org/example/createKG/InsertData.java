package org.example.createKG;

import com.google.gson.Gson;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.example.other.JSONFormatClasses;
import org.example.other.JSONFormatClasses.Table;
import org.example.other.JSONFormatClasses.Column;
import org.example.other.JSONFormatClasses.Mapping;

import java.io.FileReader;
import java.net.URI;
import java.util.List;

public class InsertData {

    private List<Table> tablesMaps;
    private OntModel pModel;

    public InsertData() {
        //pModel = loadPutativeOntology();
        readMapJSON();
    }

    private void readMapJSON() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("EFS_mappings.json")) {
            // Convert JSON file to Java object
            tablesMaps = gson.fromJson(reader, JSONFormatClasses.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }



    private void extractMappingPaths() {
        /*for(Table tableMaps : tablesMaps) {
            String tableName = tableMaps.getTable();
            URI tableClass = tableMaps.getMapping().getOntoElURI();

            for(Column col : tableMaps.getColumns()) {
                Mapping objMap   = col.getObjectPropMapping();
                Mapping classMap = col.getClassPropMapping();
                Mapping dataMap  = col.getDataPropMapping();


                if(objMap.getPathURIs() != null) {

                    List<String> path = objMap.getPathResources();
                    if(Character.isUpperCase(path.get(0).charAt(0)))
                        bl.append(getPropertyNodeString(objMap.getOntoElResource()));

                    for (String pathNode : path)
                        if (Character.isLowerCase(pathNode.charAt(0)))
                            bl.append(getPropertyNodeString(pathNode));
                        else
                            bl.append(getClassNodeString(pathNode));
                }

                if(objMap.hasMatch())
                    bl.append(getPropertyNodeString(objMap.getMatchResource()));
                else if (classMap.hasMatch())
                    bl.append(getPropertyNodeString(objMap.getOntoElResource()));


                if(classMap.hasMatch())
                    bl.append(getClassNodeString(classMap.getMatchResource()));


                if(dataMap.getPathURIs() != null) {

                    List<String> path = dataMap.getPathResources();
                    if(Character.isUpperCase(path.get(0).charAt(0)))
                        bl.append(getPropertyNodeString(dataMap.getOntoElResource()));

                    for (String pathNode : path)
                        if (Character.isLowerCase(pathNode.charAt(0)))
                            bl.append(getPropertyNodeString(pathNode));
                        else
                            bl.append(getClassNodeString(pathNode));
                }

                if(dataMap.hasMatch())
                    bl.append(getPropertyNodeString(dataMap.getMatchResource()));
                else if (classMap.hasMatch())
                    bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


                if(! (objMap.hasMatch() || classMap.hasMatch() || dataMap.hasMatch()))
                    bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


                bl.append(getClassNodeString("VALUE"));

                System.out.println(bl);
                matchCol.append(bl.toString());

            }


        }*/
    }
}



















