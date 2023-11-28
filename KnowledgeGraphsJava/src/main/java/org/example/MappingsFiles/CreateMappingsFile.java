package org.example.MappingsFiles;

import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.util.Annotations;
import org.example.C_POextractor.Properties;
import org.example.C_POextractor.RulesetApplication;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.A_Coordinator.config.Config.DEV_MODE;
import static org.example.util.Annotations.CLASS_SUFFIX;
import static org.example.util.Annotations.TABLE_PREFIX;
import static org.example.util.FileHandler.fileExists;

public class CreateMappingsFile extends ManageMappingsFile {

    private HashMap<String, ArrayList<Transformation>> trf = new HashMap<>();
    private boolean isRDB;
    private boolean hasStored;
    private MappingsFileTemplate storedFileTemplate;

    public CreateMappingsFile() {
        hasStored = fileExists(config.Out.PO2DO_Mappings) && config.Out.maintainStoredPreprocessingResults;
        if(hasStored)
            storedFileTemplate = readMapJSONasTemplate();

        System.out.println("Extracted from notif : " + config.notification.getExtractedTableNames());
    }

    public void extractMappingsFile(Object dataSource, RulesetApplication rs) {
        this.isRDB = dataSource instanceof RelationalDB;
        gatherTrfs(rs);

        if (isRDB)
            createJSON((RelationalDB) dataSource);
        else {
            addProperties(rs.getPureObjProperties().getProperties(), "ObjectProperty");
            createJSON(config.Out.RootClassName, rs.getClasses().get(TABLE_PREFIX));
        }
        saveMappingsFile();
    }

    // =====================================================================================================
    class Transformation {
        String ontoElement;
        String type;
        public Transformation(String ontoElement, String type) {
            this.ontoElement = ontoElement;
            this.type = type;
        }
        @Override
        public String toString() {
            return ontoElement;
        }
    }

    private void gatherTrfs(RulesetApplication rs) {
        rs.getClasses().forEach((_type, classes) -> {
            classes.forEach((elName, elClass) -> {
                addTrf(elName, elClass, CLASS_SUFFIX);
            });});
        try{
            addProperties(rs.getAttrObjProp().getProperties(), "ObjectProperty");
        }catch (NullPointerException e) {
            // turnAttrToClasses was false
        }
        addProperties(rs.getDataProperties().getProperties(), "DataProperty");
    }

    private void addProperties(HashMap<String, Properties.DomRan> prop, String type) {
        prop.forEach((propName, domRan) -> {
            for(String field : domRan.getExtractedField()) {
                /*if(!isRDB)
                    field = field.substring(field.lastIndexOf('/') + 1);*/
                addTrf(field, propName, type);
            }
        });
    }

    private void addTrf(String elementName, String ontoElement, String type) {

        ontoElement = config.Out.POntologyBaseNS + Annotations.rmvInvalidIriChars(ontoElement);
        if (trf.containsKey(elementName))
            trf.get(elementName).add(new Transformation(ontoElement, type));
        else
            trf.put(elementName, new ArrayList<>(Collections.singleton(new Transformation(ontoElement, type))));
    }

// =====================================================================================================

    private void createJSON(RelationalDB db) {

        ArrayList<String> tables = new ArrayList<>(db.getrTables().keySet());
        Collections.sort(tables);

        for(String tableName : tables) {

            if (trf.containsKey(tableName)) {
                boolean isExtractedFromCurrentFile = config.notification.hasExtractedTable(tableName);

                Table table = updatedTable(tableName, isExtractedFromCurrentFile);

                Mapping tableMapping = new Mapping(
                        trf.get(tableName).get(0).type,
                        trf.get(tableName).get(0).ontoElement,
                        null, null);
                table.setMapping(tableMapping);

                // Table Columns =========================================================================================
                for (String columnName : db.getrTables().get(tableName).getColumns().keySet()) {
                    String t_c = String.format("%s.%s", tableName, columnName);

                    if (trf.containsKey(t_c)) {
                        Column column = updatedColumn(tableName, columnName, isExtractedFromCurrentFile);

                        for (Transformation t : trf.get(t_c))
                            column.addMapping(new MappingsFileTemplate.Mapping(
                                    t.type, t.ontoElement, null, null)
                            );
                        table.addColumn(columnName, column);
                    }
                }
                fileTemplate.addTable(tableName, table);
            }
        }
    }


// =====================================================================================================

    private void createJSON(String rootElementName, HashMap<String, String> tableClasses) {
        rootElementName = "/" + rootElementName;                                                                        if(DEV_MODE) System.out.println(rootElementName);
        HashMap<String, Table> tableClassesTable = new HashMap<>(); // MappingsFileTemplate.Table object for each TableClass
        for(String tableName: tableClasses.keySet()) {
            // Table table = new Table(tableName);
            Table table = updatedTable(tableName, config.notification.hasExtractedTable(tableName));

            Mapping tableMapping = new Mapping(
                    trf.get(tableName).get(0).type,
                    trf.get(tableName).get(0).ontoElement,
                    null, null);
            table.setMapping(tableMapping);
            tableClassesTable.put(tableName, table);
            fileTemplate.addTable(tableName, table);
        }                                                                                                               if(DEV_MODE) System.out.println(tableClassesTable.keySet());

        ArrayList<String> columnNames = new ArrayList<>(trf.keySet());
        Collections.sort(columnNames);

        for(String columnName : columnNames) {
            if(columnName.equals(rootElementName))
                continue;
            String tableName = columnName.substring(0, columnName.lastIndexOf("/"));                                //if(DEV_MODE) System.out.println(tableName + "\t\t" + columnName + "\t\t" + tableClassesTable.get(tableName));

            Column column = updatedColumn(tableName, columnName, config.notification.hasExtractedTable(tableName));
            for (Transformation t : trf.get(columnName))
                column.addMapping(new Mapping(
                        t.type, t.ontoElement, null,null)
                );

            tableClassesTable.get(tableName).addColumn(columnName, column);
        }
    }

// =====================================================================================================

    /** Maintain table metadata such as the files from which the table's po elements where extracted */
    private Table updatedTable(String tableName, boolean isExtractedFromCurrentFile) {
        // TODO: metadata_id
        String tableFile =  isExtractedFromCurrentFile ? config.notification.getFilename()    : String.format("%s.%s",tableName, config.In.FileExtension);
        String metadataId = isExtractedFromCurrentFile ? config.notification.getMetadata_id() : String.valueOf(tableFile.hashCode());

        Table storedTable  = hasStored ? storedFileTemplate.getTable(tableName) : null;
        Table updatedTable = new Table(tableName);

        if(storedTable != null)
            updatedTable.copyTableSource(storedTable);
        else if (isExtractedFromCurrentFile || !config.notification.isReceivedFromPreprocessing()) // the 2nd condition is for local testing
            updatedTable.addTableSource(tableFile, metadataId);     // TODO: metadata_id
        return updatedTable;
    }

    /** Maintain only is column is pii according to the preprocessing tool */
    private Column updatedColumn(String tableName, String columnName, boolean isExtractedFromCurrentFile) {
        Column storedColumns = hasStored ? storedFileTemplate.getTableColumn(tableName, columnName) : null;
        Column updatedColumn = new Column(columnName);

        if(storedColumns != null)
            updatedColumn.setPii(storedColumns.isPii());
        else if(isExtractedFromCurrentFile)
            updatedColumn.setPii(
                    config.notification.isPii(columnName));

        return updatedColumn;
    }

// =====================================================================================================


}
