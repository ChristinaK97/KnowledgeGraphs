package org.example.MappingsFiles;

import org.example.D_MappingGeneration.FormatSpecific.FormatSpecificRules;
import org.example.D_MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class SetMappingsFile extends ManageMappingsFile {

    private Matches matches;
    private List<Table> tablesList;
    private HashSet<String> tableClassesURIs;

    public SetMappingsFile(Matches matches, FormatSpecificRules spRules, HashSet<String> tableClassesURIs) {
        super();
        this.matches = matches;
        this.tableClassesURIs = tableClassesURIs;
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
        if(!isColumnMapping || !tableClassesURIs.contains(ontoEl))
            map.setPath(matches.getPath(ontoEl));

    }

    private void addNewMappings(FormatSpecificRules spRules) {
        ArrayList<Table> newMappings = spRules.getNewMappings();
        tablesList.addAll(newMappings);
    }

}
