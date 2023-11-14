package org.example.MappingsFiles;

import org.example.D_MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;

import java.net.URI;
import java.util.List;

public class SetPIIsToMappingsFile extends ManageMappingsFile {

    private Matches matches;
    private List<Table> tablesList;

    public SetPIIsToMappingsFile(Matches matches) {
        super();
        this.matches = matches;
        tablesList = readMapJSON();
        setDPVMatches();
        saveMappingsFile(tablesList);
    }

    private void setDPVMatches() {
        for(Table tableMaps : tablesList) {

            URI ontoEl = tableMaps.getMapping().getOntoElURI();
            tableMaps.appendDPVMappings(matches.getMatchURI(ontoEl));

            for (Column col : tableMaps.getColumns()) {
                appendDPVMappingsToColumn(col, col.getObjectPropMapping());
                appendDPVMappingsToColumn(col, col.getClassPropMapping());
                appendDPVMappingsToColumn(col, col.getDataPropMapping());
            }
        }
    }

    private void appendDPVMappingsToColumn(Column col, Mapping elMap) {
        URI ontoEl = elMap.getOntoElURI();
        col.appendDPVMappings(matches.getMatchURI(ontoEl));
    }

}
