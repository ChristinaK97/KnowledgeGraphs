package org.example.createKG;

import com.google.gson.Gson;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.checkerframework.checker.units.qual.A;
import org.example.other.JSONFormatClasses;
import org.example.other.JSONFormatClasses.Table;
import org.example.other.JSONFormatClasses.Column;
import org.example.other.JSONFormatClasses.Mapping;

import java.io.FileReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InsertData extends JenaOntologyModelHandler {

    // <tableName : <columnName : path of resources>>
    private HashMap<String, HashMap<String, ArrayList<OntResource>>> paths;
    // <tableName : table ontClass>
    private HashMap<String, OntClass> tablesClass;

    private HashMap<OntResource, OntResource> cacheSubElements;
    String mBasePrefix;

    public InsertData() {
        //TODO add this:
        //super("outputOntology.ttl");
        //pModel.loadImports();
        //mBasePrefix = pModel.getNsPrefixURI("");

        //TODO remove this:
        super("mergedOutputOntology.ttl");
        mBasePrefix = "http://www.example.net/ontologies/test_efs.owl/";


        tablesClass = new HashMap<>();
        paths = new HashMap<>();
        cacheSubElements = new HashMap<>();
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
                    //TODO remove mBasePrefix argument
                    String newPropURI = getNewPropertyURI(mBasePrefix, tableClassName, firstNode.getLocalName());
                    OntProperty firstProp = getOntProperty(newPropURI);
                    colPath.add(firstProp);
                }
            }
            // append the path elements to the column's list
            for(URI pathElement : propPath)
                colPath.add(getOntResource(pathElement));
        }
    }

    private OntResource getSubElementOf (OntResource element) {
        if (element.getNameSpace().equals(mBasePrefix))
            return element;

        List<? extends OntResource> subElements =
                (element.canAs(OntClass.class) ?
                        element.asClass().listSubClasses() :
                        element.asProperty().listSubProperties())
                .toList();
        for(OntResource subElement : subElements)
            if (subElement.getNameSpace().equals(mBasePrefix)) {
                cacheSubElements.put();
                return subElement;
            }

        return element;
    }

    private void printPaths() {
        tablesClass.forEach((tableName, tableClass) -> {
            System.out.println(">> " + tableName);
            System.out.println("Table class : " + tablesClass.get(tableName));

            paths.get(tableName).forEach((colName, colPath) -> {
                System.out.println("\nCol : " + colName);
                colPath.forEach(System.out::println);
            });
            System.out.println("====================");
        });
    }

    public static void main(String[] args) {
        new InsertData();
    }
}



















