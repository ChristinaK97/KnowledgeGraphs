package org.example.MappingsFiles;

import org.example.D_MappingGeneration.FormatSpecific.FormatSpecificRules;
import org.example.D_MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.example.A_Coordinator.Runner.config;


public class SetMappingsFile extends ManageMappingsFile {

    private Matches matches;
    private List<Table> tablesList;
    // private Set<String> tableClassesURIs;
    private boolean addPathsOnlyToTableMaps = config.In.isDSON();

    public SetMappingsFile(Matches matches, FormatSpecificRules spRules /*, Set<String> tableClassesURIs*/) {
        super();
        this.matches = matches;
        // this.tableClassesURIs = tableClassesURIs;
        tablesList = readMapJSON();
        addMatches();
        addNewMappings(spRules);
        fileTemplate.setTables(tablesList);
        saveMappingsFile();
    }

    private void addMatches() {
        for(Table tableMaps : tablesList) {
            Mapping tableMap = tableMaps.getMapping();
            setMatch(tableMap, false);

            for (MappingsFileTemplate.Column col : tableMaps.getColumns()) {
                setMatch(col.getObjectPropMapping(), true);
                setMatch(col.getClassPropMapping(), true);
                setMatch(col.getDataPropMapping(), true);
            }
        }
    }

    private void setMatch(Mapping map, boolean isColumnMapping) {
        String ontoEl = map.getOntoElURI().toString();
        map.setMatch(matches.getMatchURI(ontoEl));
        // Replaced condition: !isColumnMapping || !tableClassesURIs.contains(ontoEl)
        if(!isColumnMapping || !addPathsOnlyToTableMaps) {
            System.out.printf("Add path map to %s. Map path = %s\n", ontoEl, matches.getPath(ontoEl));
            map.setPath(matches.getPath(ontoEl));
        }
    }

    private void addNewMappings(FormatSpecificRules spRules) {
        ArrayList<Table> newMappings = spRules.getNewMappings();
        tablesList.addAll(newMappings);
    }

}
