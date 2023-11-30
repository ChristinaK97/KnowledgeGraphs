package org.example.MappingsFiles;

import org.example.D_MappingGeneration.Matches;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;

import java.net.URI;
import java.util.List;

public class SetPIIsToMappingsFile extends ManageMappingsFile {

    private Matches matches;

    public SetPIIsToMappingsFile(Matches matches) {
        super(false);
        this.matches = matches;
        setDPVMatches(mappingsFile.getTables());
        saveMappingsFile();
    }

    private void setDPVMatches(List<Table> tablesList) {
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
