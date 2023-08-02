package org.example.MappingsFiles;

import org.example.MappingGeneration.FormatSpecific.FormatSpecificRules;
import org.example.MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.util.ArrayList;
import java.util.List;


public class SetMappingsFile extends ManageMappingsFile {

    private Matches matches;
    private List<Table> tablesList;

    public SetMappingsFile(Matches matches, FormatSpecificRules spRules) {
        super();
        this.matches = matches;
        tablesList = readMapJSON();
        addMatches();
        addNewMappings(spRules);
        fileTemplate.setTables(tablesList);
        saveMappingsFile();
    }

    private void addMatches() {
        for(Table tableMaps : tablesList) {
            Mapping tableMap = tableMaps.getMapping();
            setMatch(tableMap);

            for (MappingsFileTemplate.Column col : tableMaps.getColumns()) {
                setMatch(col.getObjectPropMapping());
                setMatch(col.getClassPropMapping());
                setMatch(col.getDataPropMapping());
            }
        }
    }

    private void setMatch(Mapping map) {
        String ontoEl = map.getOntoElURI().toString();
        map.setMatch(matches.getMatchURI(ontoEl));
        map.setPath(matches.getPath(ontoEl));
    }

    private void addNewMappings(FormatSpecificRules spRules) {
        ArrayList<Table> newMappings = spRules.getNewMappings();
        tablesList.addAll(newMappings);
    }

}
