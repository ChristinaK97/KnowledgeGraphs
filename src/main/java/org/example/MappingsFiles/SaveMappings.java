package org.example.MappingsFiles;

import org.example.MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;

import java.util.List;

import static org.example.MappingsFiles.MappingsFile.readMapJSON;

public class SaveMappings {
    private Matches matches;
    private MappingsFile mappingsFile = new MappingsFile();

    public SaveMappings(Matches matches) {
        this.matches = matches;
        addMatches();
    }

    private void addMatches() {
        List<Table> tablesList = readMapJSON();
        assert tablesList != null;
        for(Table tableMaps : tablesList) {

            Mapping tableMap = tableMaps.getMapping();
            setMatch(tableMap);

            for (MappingsFileTemplate.Column col : tableMaps.getColumns()) {
                setMatch(col.getObjectPropMapping());
                setMatch(col.getClassPropMapping());
                setMatch(col.getDataPropMapping());
            }
        }

        mappingsFile.saveMappingsFile(tablesList);
    }

    private void setMatch(Mapping map) {
        map.setMatch(matches.getMatchURI(map.getOntoElURI()));
    }

}
