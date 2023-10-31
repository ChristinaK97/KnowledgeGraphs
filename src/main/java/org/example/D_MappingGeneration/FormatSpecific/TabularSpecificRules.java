package org.example.D_MappingGeneration.FormatSpecific;

import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.D_MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.util.Ontology;

import java.net.URI;
import java.util.*;


/**
 * Find columns that, although they are not explicitly recorded as PKs and FKs, they might be
 * candidate keys and FK references.
 * Covers cases where the table class and the attribute class where mapped to the same DO class
 * -> then the column of the attribute class is a candidate key (if it has unique values, else
 *    it is a simple direct attribute of the table class)
 * and the FK col has the same name colName as the candidate key
 * -> then, if all values of the cand FK column are contained in the candidate key of another table,
 *    the srcTable.colName references (is FK) candKeyCol_tableName.colName
 * See: Rule 3 in mapping selection rules
 * TODO:
 *      - cover cases of keys made up from multiple columns
 *      - columns that are FK while having a different name from the candidate key column
 *      (overall, a more thorough analysis to discover PKs and FKs)
 */
public class TabularSpecificRules implements FormatSpecificRules {

    private static class CandKeyCol {
        String tableName;
        URI pathURI;

        public CandKeyCol(String tableName, URI pathURI) {
            this.tableName = tableName;
            this.pathURI = pathURI;
        }
    }

    private RelationalDB db;

    public TabularSpecificRules(RelationalDB db) {
        this.db = db;
    }

    @Override
    public void addAdditionalMatches(Ontology srcOnto, Ontology trgOnto, Matches matches) {}

    @Override
    public ArrayList<Table> getNewMappings() {
        return new ArrayList<>();
    }

    @Override
    public void modifyMappings(List<Table> tablesList) {

        /* Find table with matches where there are columns whose Attribute Class was match with
         * the same DO class as the match of the Table Class -> These columns are candidate PKs
         * or simply direct attribute columns
         * Keep only the data property match for these columns
         * Maintain the initial matches in case they are needed later (eg for PII detection)
         * */
        HashMap<String, CandKeyCol> candKeyCols = new HashMap<>();
        for(Table table: tablesList) {
            if(table.getMapping().hasMatch()) {

                HashSet<String> uniqueValuedColumns = determineCandidateKeys(table.getTable());
                List<URI> tableMatch = table.getMapping().getMatchURI();

                for(Column col : table.getColumns()) {
                    String colClassMatch = col.getClassPropMapping().getMatchURI().toString();
                    if(tableMatch.toString().equals(colClassMatch)) {
                        col.getObjectPropMapping().setAsInitialMatch();
                        col.getClassPropMapping() .setAsInitialMatch();

                        // only the columns with unique values are considered as candidate keys to
                        // be referenced by other tables' columns.
                        // Other columns that where only mapped to the same class as that table class
                        // without having unique values are simply direct attributes of the table class\
                        // and will not be referenced by other tables
                        if(uniqueValuedColumns.contains(col.getColumn())) {
                            candKeyCols.put(col.getColumn(), new CandKeyCol(
                                    table.getTable(),
                                    tableMatch.size() == 1 ? tableMatch.get(0) : table.getMapping().getOntoElURI()
                            ));
                        }
                    }
                }
        }}
        if(tablesList.size() < 2)
            return; // single table dataset. don't search for PK-FK references/direct attrs

        /* For each table that contains a column with the same name as a candidate PK/direct attribute
         * -> these columns are a possible FK reference, or it is unnecessary to create a new class for them
         * Keep only the data property match and add the referenced table class/match
         * to the data property's path -> a pure object property will be created
         * to connect the two table classes
         */
        for(Table table : tablesList) {
            String srcTable = table.getTable();
            for(Column col : table.getColumns()) {
                String colName = col.getColumn();
                // if a column with the same name was recorded as a candidate key of some table, then candKeyCol is not null
                CandKeyCol candKeyCol = candKeyCols.get(colName);
                if(isCandFK(srcTable, colName, candKeyCol)) {

                    col.getObjectPropMapping().setAsInitialMatch();
                    col.getClassPropMapping() .setAsInitialMatch();
                    col.getDataPropMapping()  .setPath(Collections.singletonList(candKeyCol.pathURI));

                    // set the column as a FK in table, that references the same name column of the candKeyCol.tableName table
                    // srcTable.colName -> candKeyCol_tableName.colName
                    db.getTable(srcTable).addFK(colName, candKeyCol.tableName, colName);
        }}}
    }


    private boolean isCandFK(String tableWithFK, String fkCol, CandKeyCol candKeyCol) {
        /* For table with a potential FK in column fkCol, we check if this column is indeed a fk
         * referencing a column of the candKeyCol.tableName table.
         * This means that the fkCol was previously recorded as a candidate key (in candKeyCols map)
         * Due to the loop pass the same table
         * Check if all values of the tableWithFK.fkCol can be found in candKeyCol_tableName.candidate key col
         */
        return candKeyCol != null &&
               !candKeyCol.tableName.equals(tableWithFK) &&
                db.isJoin(tableWithFK, fkCol,
                          candKeyCol.tableName, fkCol);
    }


    /** Find all the columns in the table that have unique values -> these might be candidate keys */
    private HashSet<String> determineCandidateKeys(String tableName) {
        HashSet<String> uniqueValuedColumns = new HashSet<>();
        tech.tablesaw.api.Table table = db.retrieveDataFromTable(tableName);

        for(String colName : table.columnNames()) {
            Set<?> uniqueValues = table.column(colName).unique().asSet();
            if(uniqueValues.size() == table.rowCount())
                uniqueValuedColumns.add(colName);
        }
        return uniqueValuedColumns;
    }

}

