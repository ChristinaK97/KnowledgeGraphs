package org.example.createKG;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.example.other.JSONFormatClasses.Column;
import org.example.other.JSONFormatClasses.Mapping;
import org.example.other.JSONFormatClasses.Table;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InsertData extends JenaOntologyModelHandler {

    // <tableName : <columnName : path of resources>>
    private HashMap<String, HashMap<String, ArrayList<OntResource>>> paths;
    // <tableName : table ontClass>
    private HashMap<String, OntClass> tablesClass;
    String mBasePrefix;

    public InsertData() {
        //TODO add this:
        super("outputOntology.ttl");
        pModel.loadImports();
        mBasePrefix = pModel.getNsPrefixURI("");

        //TODO remove this:
        //super("mergedOutputOntology.ttl");
        //mBasePrefix = "http://www.example.net/ontologies/test_efs.owl/";


        tablesClass = new HashMap<>();
        paths = new HashMap<>();
        extractMappingPaths();
        printPaths();
    }


    private void extractMappingPaths() {
        for(Table tableMaps : tablesMaps) {

            String tableName = tableMaps.getTable();
            String tableClassName = tableMaps.getMapping().getOntoElResource();

            tablesClass.put(tableName, getOntClass(tableMaps.getMapping().getOntoElURI()));
            paths.put(tableName, new HashMap<>());

            for(Column col : tableMaps.getColumns()) {

                ArrayList<OntResource> colPath = new ArrayList<>();
                Mapping objMap   = col.getObjectPropMapping();
                Mapping classMap = col.getClassPropMapping();
                Mapping dataMap  = col.getDataPropMapping();
                boolean onlyDataPropertyWasMaintained = true;

                // COLUMN OBJECT PROPERTY ==============================================================================
                // if object property wasn't deleted, add it to the column's path
                OntResource objPropResource = getOntResource(objMap.getOntoElURI());
                if (objPropResource != null){
                    onlyDataPropertyWasMaintained = false;
                    // append the path of the object property to the column's path
                    specialisePathDOclasses(objMap);
                    addPropertyPathToColumnPath(colPath, objMap, true, tableClassName);
                    // append the column object property to the column's path
                    colPath.add(objPropResource);
                }

                //======================================================================================================
                // COLUMN CLASS ========================================================================================
                // append the column class to the column's path (if it wasn't deleted)
                OntResource classResource = getOntResource(classMap.getOntoElURI());
                if (classResource != null) {
                    onlyDataPropertyWasMaintained = false;
                    colPath.add(classResource);
                }

                //======================================================================================================
                // DATA PROPERTY =======================================================================================
                /* If onlyDataPropertyWasMaintained == true :
                 * table class is directly connected to the first class in the data property's path,
                 * through a new property, as col.objProp and col.class were deleted
                 * (tableClass) -[newProp]-> (firstNode)
                */
                specialisePathDOclasses(dataMap);
                addPropertyPathToColumnPath(colPath, dataMap, onlyDataPropertyWasMaintained, tableClassName);
                // append data property to the column's path (data properties are never deleted)
                colPath.add(getOntResource(dataMap.getOntoElURI()));

                //======================================================================================================
                paths.get(tableName).put(col.getColumn(), colPath);
            }
        }
    }

    private void addPropertyPathToColumnPath(ArrayList<OntResource> colPath, Mapping map, boolean checkFirstNode, String tableClassName) {
        if(map.getPathURIs() != null) {
            List<URI> propPath = map.getPathURIs();

            if(checkFirstNode) {
                OntResource firstNode = getOntResource(getFirstNodeFromPath(propPath));
                // if first node in the path is a class -> a new property (firstProp) was created
                if (firstNode.canAs(OntClass.class)) {
                    String newPropURI = getNewPropertyURI(mBasePrefix, firstNode.asClass(), tableClassName);
                    OntProperty firstProp = getOntProperty(newPropURI);
                    colPath.add(firstProp);
                }
            }
            // append the path elements to the column's list
            for(URI pathElement : propPath)
                colPath.add(getOntResource(pathElement));
        }
    }


    private void printPaths() {
        try {
            PrintWriter pw = new PrintWriter("src/main/java/org/example/temp/paths.txt");
            tablesClass.forEach((tableName, tableClass) -> {
                pw.println(">> " + tableName);
                pw.println("Table class : " + tablesClass.get(tableName));

                paths.get(tableName).forEach((colName, colPath) -> {
                    pw.println("\nCol : " + colName);
                    colPath.forEach(pw::println);
                });
                pw.println("====================");
            });
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }


    public static void main(String[] args) {
        new InsertData();
    }
}



















