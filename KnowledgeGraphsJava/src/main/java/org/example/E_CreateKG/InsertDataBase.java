package org.example.E_CreateKG;

import org.apache.jena.ontology.AnnotationProperty;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import static org.example.A_Coordinator.Pipeline.config;
import org.example.C_POextractor.Properties;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.util.Ontology;
import org.example.util.Pair;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static org.example.A_Coordinator.config.Config.DEV_MODE;
import static org.example.B_InputDatasetProcessing.Tabular.TabularFilesReader.nullValues;
import static org.example.util.Ontology.getLocalName;

public abstract class InsertDataBase extends JenaOntologyModelHandler {

    // <tableName : table ontClass, hasPath> where hasPath is whether the table mapping has path or not
    protected HashMap<String, Pair<OntClass, Boolean>> tablesClass;
    // <tableName : <columnName : path of resources>>
    protected HashMap<String, HashMap<String, ArrayList<Pair<OntResource,Boolean>>>> paths;

    protected AnnotationProperty sourceFileAnnotProp;


    // The elements -classes/properties- in the path of the current column + should create new individual? for each element
    private ArrayList<Pair<OntResource, Boolean>> colPath;
    // The set of elements found in the paths of each column in the current table
    // If an element was found in the paths of multiple columns, an individual will be created only the first time/first
    // column that contain the element and will be reused for the paths of other columns in the same table
    private HashSet<String> pathElementsFound;



    public InsertDataBase (Object refinedOnto) {
        super(refinedOnto);
        if(!(refinedOnto instanceof Ontology))
            ontology.pModel.loadImports();
        sourceFileAnnotProp = ontology.pModel.createAnnotationProperty(config.Out.POntologyBaseNS + "SourceFile");
    }

    protected void run() {
        // remove DO individuals before loading data
        ontology.listResources(Ontology.INDIVIDUALS).forEachRemaining(resource -> {
            ontology.pModel.removeAll(resource, null, null);
            ontology.pModel.removeAll(null, null, resource);
        });

        // create paths
        tablesClass = new HashMap<>();
        paths = new HashMap<>();
        extractMappingPaths();
        addAdditionalPaths();
        writePaths();

        // insert data and save kg
        mapData();
        saveIndivs();
        saveFullKG();
    }


    private void extractMappingPaths() {
        for(Table tableMaps : tablesMaps) {

            String tableName = tableMaps.getTable();
            Mapping tableMapping = tableMaps.getMapping();
            String tableClassName = tableMapping.getOntoElResource();

            tablesClass.put(tableName, new Pair<>(
                                    ontology.getOntClass(tableMapping.getOntoElURI()),
                                    tableMapping.hasPath())
            );
            paths.put(tableName, new HashMap<>());
            pathElementsFound = new HashSet<>();

            for(Column col : tableMaps.getColumns()) {

                colPath = new ArrayList<>();
                Mapping objMap   = col.getObjectPropMapping();
                Mapping classMap = col.getClassPropMapping();
                Mapping dataMap  = col.getDataPropMapping();

                boolean onlyDataPropertyWasMaintained = true;

                // Add the path of the table to each column's path
                // tableClass -> tablePath -> column path
                if(tableMapping.hasPath())
                    for(URI tablePathEl : tableMapping.getPathURIs())
                        addColumnPathElement(ontology.getOntResource(tablePathEl));


                // COLUMN OBJECT PROPERTY ==============================================================================
                // if object property wasn't deleted, add it to the column's path
                OntResource objPropResource = ontology.getOntResource(objMap.getOntoElURI());
                if (objPropResource != null){
                    onlyDataPropertyWasMaintained = false;
                    // append the path of the object property to the column's path
                    specialisePathDOclasses(objMap);
                    addPropertyPathToColumnPath(objMap, true, tableClassName);
                    // append the column object property to the column's path
                    addColumnPathElement(objPropResource);
                }

                //======================================================================================================
                // COLUMN CLASS ========================================================================================
                // append the column class to the column's path (if it wasn't deleted)
                OntResource classResource = ontology.getOntResource(classMap.getOntoElURI());
                if (classResource != null) {
                    onlyDataPropertyWasMaintained = false;
                    addColumnPathElement(classResource);
                }

                //======================================================================================================
                // DATA PROPERTY =======================================================================================
                /* If onlyDataPropertyWasMaintained == true :
                 * table class is directly connected to the first class in the data property's path,
                 * through a new property, as col.objProp and col.class were deleted
                 * (tableClass) -[newProp]-> (firstNode)
                 */
                specialisePathDOclasses(dataMap);
                if(dataMap.hasDataProperty()) {
                    addPropertyPathToColumnPath(dataMap, onlyDataPropertyWasMaintained, tableClassName);
                    // append data property to the column's path (data properties are never deleted)
                    addColumnPathElement(ontology.getOntResource(dataMap.getOntoElURI()));
                }
                //======================================================================================================
                paths.get(tableName).put(col.getColumn(), colPath);
            }
        }
    }

    private void addColumnPathElement(OntResource pathElement){
        colPath.add(new Pair<>(
                pathElement,
                // if the element wasn't previously found on another columns path, an individual of this pathElement
                // (if it's a class) will be created when the current column is reached and this new individual will
                // be reused for other columns that contain this path element on their path
                // (i.e., additional attributes will be added to the existing individual)
                !pathElementsFound.contains(pathElement.toString())
        ));
        pathElementsFound.add(pathElement.toString());
    }

    private void addPropertyPathToColumnPath(Mapping map, boolean checkFirstNode, String tableClassName) {
        if(map.hasPath()) {

            if(checkFirstNode) {
                OntResource firstNode = ontology.getOntResource(map.getFirstNodeFromPath());
                // if first node in the path is a class -> a new property (firstProp) was created
                if (firstNode.canAs(OntClass.class)) {
                    String newPropURI = getNewPropertyURI(config.Out.POntologyBaseNS, firstNode.asClass(), tableClassName);
                    OntProperty firstProp = ontology.getOntProperty(newPropURI);
                    addColumnPathElement(firstProp);
                }
            }
            // append the path elements to the column's list
            for(URI pathElement : map.getPathURIs())
                addColumnPathElement(ontology.getOntResource(pathElement));
        }
    }

    protected abstract void addAdditionalPaths();

    protected OntClass getTClass(String tableName) {
        return tablesClass.get(tableName).tableClass();
    }

    protected boolean isNotNull(Object colValue) {
        return colValue != null && !nullValues.contains(colValue.toString().toLowerCase());
    }

// INSERT DATA =====================================================================================================

    protected abstract void mapData();


// =================================================================================================================
// CREATE & CONNECT INDIVIDUALS  ===================================================================================


    /* Implement the following methods per subclass
        private Resource createIndiv(...)
        private void createColPath(...)
     */


    protected void setDataPropertyValue(Resource prevNode, OntProperty dataProp, Object colValue) {
        // to resolve WARN inventing a datatype for class java.time.Instant
        // cast the datatype according to the range of the data property
        Literal dataValue = ontology.pModel.createTypedLiteral(colValue, dataProp.getRange().getURI());                         //System.out.println("DATAPROP  " + dataProp + " -> " + colValue + " ^^ " + getLocalName(dataProp.getRange().getURI()));
        prevNode.addProperty(dataProp, dataValue);
    }



    public OntProperty getInverse(OntProperty property) {
        String inverse = config.Out.POntologyBaseNS + Properties.DomRan.getInverse(property.getLocalName());
        return ontology.getOntProperty(inverse);
    }

//==================================================================================================================


    protected void saveIndivs() {

        // Create a new Model to store the individuals
        Model individualsModel = ModelFactory.createDefaultModel();
        individualsModel.setNsPrefix("", config.Out.POntologyBaseNS);
        individualsModel.setNsPrefix("xsd", XSD.NS);
        individualsModel.setNsPrefix("rdfs", RDFS.getURI());

        // Iterate over the individuals and add them to the individualsModel
        ExtendedIterator<? extends Resource> individuals = ontology.listResources(Ontology.INDIVIDUALS);
        while (individuals.hasNext()) {
            Resource individual = individuals.next();
            individualsModel.add(individual.listProperties());
        }

        // Save the individualsModel to a TTL file
        String outputFile = config.Out.IndividualsTTL;
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            individualsModel.write(fos, "TURTLE");                                                                 LoggerFactory.getLogger(InsertDataBase.class).info("Individuals saved to: " + outputFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void saveFullKG() {
        OutputStream out = null;
        String filePath = config.Out.FullGraph;
        try {
            out = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ontology.pModel.setNsPrefix("", config.Out.POntologyBaseNS);
        ontology.pModel.write(out, "TURTLE");                                                                      LoggerFactory.getLogger(InsertDataBase.class).info("Full graph saved to " + filePath);
    }


// ------------------------------------------------------------------------------------
    protected void printIndivs(OntClass ontClass) {
        System.out.println("INDIVS OF " + ontClass.getLocalName());
        ontClass.listInstances().forEach(System.out::println);
        System.out.println("------");
    }

    protected void writePaths() {
        try {
            PrintWriter pw = new PrintWriter(config.Out.PathsTXT);
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


}
