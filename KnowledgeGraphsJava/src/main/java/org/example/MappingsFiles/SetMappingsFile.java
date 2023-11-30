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
        super(false);
        this.matches = matches;
        tablesList = mappingsFile.getTables();

        if(spRules != null) {
            // add new mappings
            // set matches to the mapping json objects
            // make modifications to established mappings
            tablesList.addAll(spRules.getNewMappings());
            setMatches();
            spRules.modifyMappings(tablesList);

        }else // if not format specific need to be applied simply set the matches to the mappings file template
            setMatches();

        saveMappingsFile();
    }

    private void setMatches() {
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
            //System.out.printf("Add path map to %s. Map path = %s\n", ontoEl, matches.getPath(ontoEl));
            map.setPath(matches.getPath(ontoEl));
        }
    }


}

