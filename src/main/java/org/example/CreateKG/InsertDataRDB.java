package org.example.CreateKG;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Resource;
import org.example.InputPoint.SQLdb.DBSchema;
import org.example.InputPoint.SQLdb.RTable;
import org.example.InputPoint.SQLdb.RTable.FKpointer;
import org.example.InputPoint.SQLdb.SQLconnector.DatabaseConnector;
import org.example.util.Pair;
import tech.tablesaw.api.Row;

import java.util.ArrayList;
import java.util.Collections;

public class InsertDataRDB extends InsertDataBase {

    // tablesClass : <tableName : table ontClass>
    // paths : <tableName : <columnName : path of resources>>
    // mBasePrefix;

    DBSchema db = new DBSchema();
    DatabaseConnector connector = new DatabaseConnector();

    public InsertDataRDB(String ontologyName) {
        super(ontologyName);
        run();
    }


    @Override
    protected void addAdditionalPaths() {
        addForeignKeysToPaths();
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
                    paths.get(tableName).put(fkCol, new ArrayList<>(Collections.singleton(new Pair<>(fkProp, true))));
                    //System.out.printf("%s %s %s\n", tableName, fkCol, fkPropURI);
                }
            });
            //System.out.println();
        }
    }


// INSERT DATA =====================================================================================================

    @Override
    protected void mapData() {
        db.getrTables().forEach((tableName, rTable) -> {
            tech.tablesaw.api.Table data = connector.retrieveDataFromTable(tableName);

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
                            createJoin(indiv, colValue.toString(), colPath.get(0).pathElement().asProperty(),
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

    // SuperClass methods:
    // OntClass getTClass(String tableName)
    // boolean isNotNull(Object colValue)

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

    // SuperClass methods:
    // String getIndivURI(OntClass indivType, String rowID)


    // =================================================================================================================
    // CREATE & CONNECT INDIVIDUALS  ===================================================================================


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

        OntProperty inverse = getInverse(fkProp);
        if(inverse != null)
            trg.addProperty(inverse, src);
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
                               ArrayList<Pair<OntResource,Boolean>> cp,
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
            String iplus1NodeID = generateAttrIndivURI(rowID, cp.get(i+1).pathElement().getURI(), rTable, row);  //2nd:class URI of the new indiv

            // create the next node entity
            Resource nextNode = createIndiv(iplus1NodeID,
                                            cp.get(i+1).pathElement().asClass(),
                                            comment);
            // connect the prevNode (i-1) with the next (i+1) using the objProp (i)
            prevNode.addProperty(cp.get(i).pathElement().asProperty(), nextNode);
            prevNode = nextNode;
        }
        // set the data value to the last node using the data property in the final pos of the path (size-1)
        setDataPropertyValue(prevNode, cp.get(cp.size()-1).pathElement().asProperty(), colValue);
    }

    // SuperClass methods:
    // Resource createIndiv(String indivURI, OntClass indivType, String comment) {
    // void setDataPropertyValue(Resource prevNode, OntProperty dataProp, Object colValue)

// ========================================================================================================


    public static void main(String[] args) {
        new InsertDataRDB("test_efs");
    }
}



