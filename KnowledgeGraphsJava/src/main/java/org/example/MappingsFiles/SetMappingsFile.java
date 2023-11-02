package org.example.MappingsFiles;

import org.example.D_MappingGeneration.FormatSpecific.FormatSpecificRules;
import org.example.D_MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.util.List;

import static org.example.A_Coordinator.Pipeline.config;


public class SetMappingsFile extends ManageMappingsFile {

    private Matches matches;
    private List<Table> tablesList;
    private boolean addPathsOnlyToTableMaps = config.In.isDSON();

    public SetMappingsFile(Matches matches, FormatSpecificRules spRules) {
        super();
        this.matches = matches;
        tablesList = readMapJSON();
        addMatches();
        applyFormatSpecificRules(spRules);
        saveMappingsFile(tablesList);
    }


    //TODO for testing. Remove it
    public SetMappingsFile(Matches matches, FormatSpecificRules spRules, String PO2DO) {
        super();
        this.matches = matches;
        tablesList = readMapJSON(PO2DO);
        addMatches();
        applyFormatSpecificRules(spRules);
        fileTemplate.setTables(tablesList);
        saveMappingsFile(PO2DO);
    }


    private void addMatches() {
        for(Table tableMaps : tablesList) {
            Mapping tableMap = tableMaps.getMapping();
            setMatch(tableMap, false);

            for (Column col : tableMaps.getColumns()) {
                setMatch(col.getObjectPropMapping(), true);
                setMatch(col.getClassPropMapping(), true);
                setMatch(col.getDataPropMapping(), true);
            }
        }
    }

    private void setMatch(Mapping map, boolean isColumnMapping) {
        String ontoEl = map.getOntoElURI().toString();
        map.setMatch(matches.getMatchURI(ontoEl));
        if(!isColumnMapping || !addPathsOnlyToTableMaps) {
            System.out.printf("Add path map to %s. Map path = %s\n", ontoEl, matches.getPath(ontoEl));
            map.setPath(matches.getPath(ontoEl));
        }
    }


    private void applyFormatSpecificRules(FormatSpecificRules spRules) {
        if(spRules != null) {
            // add new mappings
            tablesList.addAll(spRules.getNewMappings());
            // make modifications to established mappings
            spRules.modifyMappings(tablesList);
        }
    }

}

