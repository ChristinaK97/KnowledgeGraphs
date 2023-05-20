package org.example.createKG;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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
        pModel.listIndividuals().forEachRemaining(resource -> {
            pModel.removeAll(resource, null, null);
        });

        mapData();
        saveIndivs();

        OutputStream out = null;
        try {
            out = new FileOutputStream(sampleGraph);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        pModel.setNsPrefix("", mBasePrefix);
        pModel.write(out, "TURTLE");
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
                String coreIndivURI = getIndivURI(getTClass(tableName), rowID);
                Resource indiv = createIndiv(coreIndivURI, getTClass(tableName), tableName);

                paths.get(tableName).forEach((colName, colPath) -> {

                     //*p*/System.out.println("T : " + tableName + " C : " + colName);

                    Object colValue = row.getObject(colName);
                    if(isNotNull(colValue)) { // row has value for this column

                        if(rTable.isFK(colName))
                            createJoin(indiv, colValue.toString(), colPath.get(0).asProperty(),
                                       rTable.getFKpointer(colName));
                        else
                            createColPath(rowID, indiv, row.getObject(colName),
                                          colPath, rTable, row,
                                          String.format("%s.%s", tableName, colName));
                    }
                });
            }
            /*p*/System.out.println("\n\n");
        });

    }


    private OntClass getTClass(String tableName) {
        return tablesClass.get(tableName);
    }

    private boolean isNotNull(Object colValue) {
        return colValue != null && !colValue.equals("");
    }

    // GENERATE IDENTIFIERS ============================================================================================

    /**
     * Generate the node identifier of the core entity of a table
     * @param row: The Tablesaw row that is currently processed
     * @param rTable: The RTable of table that contains its columns,PKs,FKs (used to retrieve PK cols)
     * @return The concatenation of all PK column values of the table row
     */
    private String rowID(Row row, RTable rTable) {
        StringBuilder rowID = new StringBuilder();
        rTable.getPKs().forEach(pkCol -> rowID.append(row.getObject(pkCol)));
        return rowID.toString();
    }

    /**
     * Generate the URI of a node
     * @param indivType : The class of the individual
     * @param rowID : The identifier of individual
     * @return Concat(The uri of the class of the individual, its identifier)
     */
    private String getIndivURI(OntClass indivType, String rowID) {
        return indivType + rowID;
    }

    /**
     * We want to name a secondary resource from a column path
     * rer_metric -[has person]-> person -[has address]-> address -[is not valid]-> bool
     * colName="is not valid", AttrIndiv=person
     * rer_metric.CLIENT_ID -> person.PERSON_ID
     *      : Name the AttrIndiv person with the CLIENT_ID value
     *
     * @param rowID : The identifier of the core entity of the table
     * @param resTypeURI : The URI of the class of the AttrIndiv in the path (http://.../person)
     * @param rTable : The RTable from where the core entity is derived      (rer_metric rTable)
     * @param row : The row of the Tablesaw that is currently processed
     * @return The URI of the AttrIndiv.
     *          - If the class of the AttrIndiv matches the tableClass of a fkp.refTable
     *              : classURI + row[FK col]
     *          - But if the row[FK col] == null or
     *            If class doesn't match with some fk.refTable
     *              :  classURI + rowID <- the identifier of the core entity
     */
    private String generateAttrIndivURI(String rowID, String resTypeURI, RTable rTable, Row row) {

        // find if there exists a FK col that references table such that tableClass == AttrIndiv type
        for(String fkCol : rTable.getFKs().keySet()) {
            FKpointer fkp = rTable.getFKpointer(fkCol);

            if ( resTypeURI.equals( getTClass(fkp.refTable).getURI() )) {

                Object referencedIndivID = row.getObject(fkCol);
                if(isNotNull(referencedIndivID))
                    return resTypeURI + referencedIndivID;
            }
        }
        return resTypeURI + rowID;
    }

    // =================================================================================================================
    // CREATE & CONNECT INDIVIDUALS  ===================================================================================

    /**
     * Create an individual if it doesn't exist
     * @param indivURI : The URI
     * @param indivType : The class of the individual (rdf:type)
     * @param comment : rdf:comment value
     * @return The resource that was created (or existed with this URI in the pModel)
     */
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

    /**
     * Connect two individual of table classes with a "pure" FK object property.
     * src -[fkProp]-> trg
     * @param src : The subject resource
     * @param trgID : The identifier of the object resource
     * @param fkProp : The object (FK) property  resource
     * @param fkp : "Tuple" refTable.refCol (used to retrieve the tableClass of the object)
     */
    private void createJoin(Resource src, String trgID, OntProperty fkProp, FKpointer fkp) {
        OntClass trgClass = getTClass(fkp.refTable);
        Resource trg = createIndiv(getIndivURI(trgClass, trgID), trgClass, fkp.refTable);
        src.addProperty(fkProp, trg);
    }


    /**
     * Parse the path cp of a column, create and connect individuals
     * @param rowID : The identifier of the core entity
     * @param coreIndiv : The core entity resource of the table row
     * @param colValue : The value of the data property at the end of the path
     * @param cp : The path of resources for this specific table column
     * @param rTable : The RTable of the table currently processed
     * @param row : The Tablesaw row that is currently processed
     * @param comment : rdf:comment value, typically table.column
     */
    private void createColPath(String rowID,
                               Resource coreIndiv,
                               Object colValue,
                               ArrayList<OntResource> cp,
                               RTable rTable,
                               Row row,
                               String comment) {
        /*
         *     i =       0       1   2
         * coreIndiv -[objP1]-> c1 -[dp]-> colValue
         * prevNode (i-1) -[i]-> nextNode (i+1)
         */
        Resource prevNode = coreIndiv;
        for (int i = 0; i < cp.size() - 2; i+=2) {
            // generate the uri of next node (i+1)
            String iplus1NodeID = generateAttrIndivURI(rowID, cp.get(i+1).getURI(), rTable, row);  //2nd:class URI of the new indiv

            // create the next node entity
            Resource nextNode = createIndiv(iplus1NodeID,
                                            cp.get(i+1).asClass(),
                                            comment);
            // connect the prevNode (i-1) with the next (i+1) using the objProp (i)
            prevNode.addProperty(cp.get(i).asProperty(), nextNode);
            prevNode = nextNode;
        }
        // set the data value to the last node using the data property in the final pos of the path (size-1)
        setDataPropertyValue(prevNode, cp.get(cp.size()-1).asProperty(), colValue);
    }

    private void setDataPropertyValue(Resource prevNode, OntProperty dataProp, Object colValue) {
        // to resolve WARN inventing a datatype for class java.time.Instant
        // cast the datatype according to the range of the data property
        Literal dataValue = pModel.createTypedLiteral(colValue, dataProp.getRange().getURI());
        prevNode.addLiteral(dataProp, dataValue);
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





