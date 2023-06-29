package org.example.CreateKG;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import org.example.mappingsFiles.MappingsFileTemplate.Table;
import org.example.mappingsFiles.MappingsFileTemplate.Column;
import org.example.mappingsFiles.MappingsFileTemplate.Mapping;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.example.other.Util.*;

public abstract class InsertDataBase extends JenaOntologyModelHandler {

    // <tableName : table ontClass>
    protected HashMap<String, OntClass> tablesClass;
    // <tableName : <columnName : path of resources>>
    protected HashMap<String, HashMap<String, ArrayList<OntResource>>> paths;
    String mBasePrefix;


    public InsertDataBase (String ontologyName) {
        //TODO add this:
        /*super("outputOntology.ttl");
        pModel.loadImports();
        mBasePrefix = pModel.getNsPrefixURI("");*/

        //TODO remove this:
        super(outputOntology, ontologyName);
        mBasePrefix = "http://www.example.net/ontologies/json.owl/";

    }

    protected void run() {
        //remove DO individuals before loading data
        pModel.listIndividuals().forEachRemaining(resource -> {
            pModel.removeAll(resource, null, null);
        });

        // create paths
        tablesClass = new HashMap<>();
        paths = new HashMap<>();
        extractMappingPaths();
        addAdditionalPaths();
        printPaths();

        // insert data and save kg
        mapData();
        saveIndivs();
        saveFullKG();
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
            System.out.println(propPath);

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

    protected abstract void addAdditionalPaths();

    protected OntClass getTClass(String tableName) {
        return tablesClass.get(tableName);
    }

    protected boolean isNotNull(Object colValue) {
        return colValue != null && !colValue.equals("");
    }

// INSERT DATA =====================================================================================================

    protected abstract void mapData();

// GENERATE IDENTIFIERS ============================================================================================

    /**
     * Generate the URI of a node
     * @param indivType : The class of the individual
     * @param rowID : The identifier of individual
     * @return Concat(The uri of the class of the individual, its identifier)
     */
    protected String getIndivURI(OntClass indivType, String rowID) {
        return indivType + rowID;
    }

    // abstract String rowID(elements used to generate the id of a row/record);
    // abstract String generateAttrIndivURI(elements used to generate the id of a secondary resource)

// =================================================================================================================
// CREATE & CONNECT INDIVIDUALS  ===================================================================================

    /**
     * Create an individual if it doesn't exist
     * @param indivURI : The URI
     * @param indivType : The class of the individual (rdf:type)
     * @param comment : rdf:comment value
     * @return The resource that was created (or existed with this URI in the pModel)
     */
    protected Resource createIndiv(String indivURI, OntClass indivType, String comment) {
        Resource indiv = pModel.getOntResource(indivURI);
        if(indiv == null) {
            //System.out.println("create " + indivURI);
            indiv = pModel.createResource(indivURI);
            indiv.addProperty(RDF.type, indivType);
            indiv.addLiteral(RDFS.comment, comment);
        }
        return indiv;
    }

    protected void setDataPropertyValue(Resource prevNode, OntProperty dataProp, Object colValue) {
        // to resolve WARN inventing a datatype for class java.time.Instant
        // cast the datatype according to the range of the data property
        System.out.println(dataProp);
        System.out.println(dataProp.getRange().getURI());
        Literal dataValue = pModel.createTypedLiteral(colValue, dataProp.getRange().getURI());
        prevNode.addProperty(dataProp, dataValue);
    }


    // abstract void createColPath(String rowID, Resource coreIndiv, Object colValue, ArrayList<OntResource> cp, String comment
    //          + elements needed to create the ids and the path


//==================================================================================================================

    protected void printPaths() {
        try {
            PrintWriter pw = new PrintWriter(pathsTXT);
            tablesClass.forEach((tableName, tableClass) -> {
                pw.println(">> " + tableName);
                pw.println("Table class : " + tablesClass.get(tableName));

                paths.get(tableName).forEach((colName, colPath) -> {
                    pw.println("\nCol : " + colName);
                    colPath.forEach(pw::println);
                });
                pw.println("====================");
            });
            pw.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    protected void printIndivs(OntClass ontClass) {
        System.out.println("INDIVS OF " + ontClass.getLocalName());
        ontClass.listInstances().forEach(System.out::println);
        System.out.println("------");
    }

    protected void saveIndivs() {

        // Create a new Model to store the individuals
        Model individualsModel = ModelFactory.createDefaultModel();
        individualsModel.setNsPrefix("", mBasePrefix);
        individualsModel.setNsPrefix("xsd", XSD.NS);
        individualsModel.setNsPrefix("rdfs", RDFS.getURI());

        // Iterate over the individuals and add them to the individualsModel
        ExtendedIterator<? extends Resource> individuals = pModel.listIndividuals();
        while (individuals.hasNext()) {
            Resource individual = individuals.next();
            individualsModel.add(individual.listProperties());
        }

        // Save the individualsModel to a TTL file
        String outputFile = individualsTTL;
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            individualsModel.write(fos, "TURTLE");
            System.out.println("Individuals saved to: " + outputFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void saveFullKG() {
        OutputStream out = null;
        String filePath = sampleGraph;
        try {
            out = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        pModel.setNsPrefix("", mBasePrefix);
        pModel.write(out, "TURTLE");
        System.out.println("Full graph saved to " + filePath);
    }


}
