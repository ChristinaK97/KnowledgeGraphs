package org.example.D_MappingGeneration.FormatSpecific;

import org.example.D_MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.util.Ontology;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class TabularSpecificRules implements FormatSpecificRules{

    private static class DirectAttrCol {
        String tableName;
        URI pathURI;

        public DirectAttrCol(String tableName, URI pathURI) {
            this.tableName = tableName;
            this.pathURI = pathURI;
        }
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
        HashMap<String, DirectAttrCol> daCols = new HashMap<>();
        for(Table table: tablesList) {
            if(table.getMapping().hasMatch()) {
                List<URI> tableMatch = table.getMapping().getMatchURI();

                for(Column col : table.getColumns()) {
                    String colClassMatch = col.getClassPropMapping().getMatchURI().toString();
                    if(tableMatch.toString().equals(colClassMatch)) {
                        col.getObjectPropMapping().setAsInitialMatch();
                        col.getClassPropMapping() .setAsInitialMatch();

                        daCols.put(col.getColumn(), new DirectAttrCol(
                                table.getTable(),
                                tableMatch.size() == 1 ? tableMatch.get(0) : table.getMapping().getOntoElURI()
                        ));
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
            for(Column col : table.getColumns()) {
                DirectAttrCol daCol = daCols.get(col.getColumn());
                if(daCol != null && !daCol.tableName.equals(table.getTable())) {
                    col.getObjectPropMapping().setAsInitialMatch();
                    col.getClassPropMapping() .setAsInitialMatch();
                    col.getDataPropMapping().setPath(Collections.singletonList(daCol.pathURI));
        }}}
    }

}















