package org.example.createKG;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.eclipse.rdf4j.model.base.CoreDatatype;
import org.example.database_connector.DBSchema;
import org.example.database_connector.DatabaseConnector;
import org.example.database_connector.RTable;
import org.example.database_connector.RTable.FKpointer;
import org.example.other.JSONMappingTableConfig;
import org.example.other.JSONMappingTableConfig.Column;
import org.example.other.JSONMappingTableConfig.Mapping;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.*;

import static org.example.other.Util.*;

public class InsertData extends JenaOntologyModelHandler {

    // <tableName : table ontClass>
    private HashMap<String, OntClass> tablesClass;
    // <tableName : <columnName : path of resources>>
    private HashMap<String, HashMap<String, ArrayList<OntResource>>> paths;
    String mBasePrefix;

    DBSchema db = new DBSchema();
    DatabaseConnector connector = new DatabaseConnector();

    public InsertData() {
        //TODO add this:
        /*super("outputOntology.ttl");
        pModel.loadImports();
        mBasePrefix = pModel.getNsPrefixURI("");*/

        //TODO remove this:
        super(mergedOutputOntology);
        mBasePrefix = "http://www.example.net/ontologies/test_efs.owl/";


        tablesClass = new HashMap<>();
        paths = new HashMap<>();
        extractMappingPaths();
        addForeignKeysToPaths();
        printPaths();

        //remove fibo individuals before loading data
        /*pModel.listIndividuals().forEachRemaining(resource -> {
            pModel.removeAll(resource, null, null);
        });

        mapData();
        saveIndivs();

        OutputStream out = null;
        try {
            out = new FileOutputStream("smallGraph.ttl");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        pModel.setNsPrefix("", mBasePrefix);
        pModel.write(out, "TURTLE");*/
    }



    private void extractMappingPaths() {
        for(JSONMappingTableConfig.Table tableMaps : tablesMaps) {

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


    private void addForeignKeysToPaths() {
        for(String tableName : paths.keySet()) {
            db.getTable(tableName).getFKs().forEach((fkCol, fkp) -> {
                String tableClassName = getTClass(tableName).getLocalName();

                String fkPropURI = tableName.equals(fkp.refTable) ? //self ref
                        String.format("%shas_%s", mBasePrefix, tableClassName) :
                        getNewPropertyURI(mBasePrefix, getTClass(fkp.refTable), tableClassName);

                OntProperty fkProp = getOntProperty(fkPropURI);
                if(fkProp != null) {
                    paths.get(tableName).put(fkCol, new ArrayList<>(Collections.singleton(fkProp)));
                    //System.out.printf("%s %s %s\n", tableName, fkCol, fkPropURI);
                }
            });
            //System.out.println();
        }
    }

    private void printPaths() {
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

    //==================================================================================================================


    private void mapData() {

        db.getrTables().forEach((tableName, rTable) -> {
            Table data = connector.retrieveDataFromTable(tableName);

            /*p*/System.out.println(">> TABLE : " + tableName);
            /*p*/System.out.println(data.first(3));
            /*p*/rTable.getFKs().forEach((t, r) -> System.out.println("FK " + t + " " + r));

            // for each record in the table
            for(Row row : data) {

                if(!tablesClass.containsKey(tableName)) { //TODO
                    /*p*/System.err.println("Table " + tableName + " is not a class.");
                    continue;
                }

                String rowID = rowID(row, rTable);
                String coreIndivID = getIndivURI(getTClass(tableName), rowID);
                Resource indiv = createIndiv(coreIndivID, getTClass(tableName), tableName);

                paths.get(tableName).forEach((colName, colPath) -> {
                    // /*p*/System.out.println("T : " + tableName + " C : " + colName);
                    Object colValue = row.getObject(colName);
                    if(colValue != null && !colValue.equals("")) { // row has value for this column

                        if(rTable.isFK(colName))
                            createJoin(indiv, colValue.toString(), colPath.get(0).asProperty(),
                                    rTable.getFKpointer(colName));
                        else
                            createColPath(rowID, indiv, row.getObject(colName), colPath,
                                    String.format("%s.%s", tableName, colName));
                    }

                });
            }
            /*p*/System.out.println("\n\n");
        });

    }



    private Resource createIndiv(String indivURI, OntClass indivType, String comment) {
        Resource indiv = pModel.getOntResource(indivURI);
        if(indiv == null) {
            //System.out.println("create " + indivURI);
            indiv = pModel.createResource(indivURI);
            indiv.addProperty(RDF.type, indivType);
            indiv.addLiteral(RDFS.comment, comment);
        }
        return indiv;
    }

    private OntClass getTClass(String tableName) {
        return tablesClass.get(tableName);
    }


    private void createColPath(String rowID,
                               Resource coreIndiv,
                               Object colValue,
                               ArrayList<OntResource> cp,
                               String comment) {

        Resource prevNode = coreIndiv;
        for (int i = 0; i < cp.size() - 2; i+=2) {
            Resource nextNode = createIndiv(generateAttrIndivURI(rowID, cp.get(i+1)),
                                            cp.get(i+1).asClass(),
                                            comment);
            prevNode.addProperty(cp.get(i).asProperty(), nextNode);
            prevNode = nextNode;
        }
        prevNode.addLiteral(cp.get(cp.size()-1).asProperty(), pModel.createTypedLiteral(colValue));

    }


    private void createJoin(Resource src, String trgID, OntProperty fkProp, FKpointer fkp) {
        OntClass trgClass = getTClass(fkp.refTable);
        Resource trg = createIndiv(getIndivURI(trgClass, trgID), trgClass, fkp.refTable);
        src.addProperty(fkProp, trg);
    }



    private String getIndivURI(OntClass indivType, String rowID) {
        return indivType + rowID;
    }

    private String rowID(Row row, RTable rTable) {
        StringBuilder rowID = new StringBuilder();
        rTable.getPKs().forEach(pkCol -> rowID.append(row.getObject(pkCol)));
        return rowID.toString();
    }

    private String generateAttrIndivURI(String rowID, OntResource resType) {
        return resType.getURI() + rowID;
    }

    // ========================================================================================================



    private void printIndivs(OntClass ontClass) {
        System.out.println("INDIVS OF " + ontClass.getLocalName());
        ontClass.listInstances().forEach(System.out::println);
        System.out.println("------");
    }
    private void saveIndivs() {

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


    //==================================================================================================================

    public static void main(String[] args) {
        new InsertData();
    }
}



/*private void addDataValue(Resource lastNode, OntProperty dataProp, Object colValue) {
        String datatype = dataProp.getRange().getLocalName();
        String value = colValue.toString();

        switch (datatype) {
            case "string":
                lastNode.addLiteral(dataProp, value);
                break;
            case "boolean":
                lastNode.addLiteral(dataProp, Boolean.parseBoolean(value));
                break;
            case "decimal":
            case "float":
            case "double":
                lastNode.addLiteral(dataProp, Double.parseDouble(value));
                break;
            case "integer":
            case "int":
                lastNode.addLiteral(dataProp, Integer.parseInt(value));
                break;
            case "date":
                LocalDate date = LocalDate.parse(value);
                XSDDateTime xsdDate = new XSDDateTime(date.toString(), XSDDatatype.XSDdate);
                lastNode.addLiteral(dataProp, xsdDate);
                break;
            case "time":
                LocalTime time = LocalTime.parse(value);
                XSDDateTime xsdTime = new XSDDateTime(time.toString(), XSDDatatype.XSDtime);
                lastNode.addLiteral(dataProp, xsdTime);
                break;
            case "dateTime":
                LocalDateTime dateTime = LocalDateTime.parse(value);
                XSDDateTime xsdDateTime = new XSDDateTime(dateTime.toString(), XSDDatatype.XSDdateTime);
                lastNode.addLiteral(dataProp, xsdDateTime);
                break;
            default:
                throw new IllegalArgumentException("Unsupported datatype: " + datatype);
        }*/

//System.out.println("create " + cp.get(i+1).getLocalName());
//System.out.println(prevNode + " -[" + cp.get(i).getLocalName() + "]-> " + cp.get(i+1).getLocalName());





