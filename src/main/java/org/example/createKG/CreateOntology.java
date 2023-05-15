package org.example.createKG;

import com.google.gson.Gson;
import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL;
import org.example.other.JSONFormatClasses;
import org.example.other.JSONFormatClasses.Column;
import org.example.other.JSONFormatClasses.Mapping;
import org.example.other.JSONFormatClasses.Table;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CreateOntology {

    private List<Table> tablesMaps;
    private OntModel pModel;
    private HashSet<String> importURIs = new HashSet<>();

    public CreateOntology() {
        loadPutativeOntology();
        readMapJSON();
        gatherImports();
        loadDomainOntoImports();
        setHierarchy();
        saveOutputOntology();
    }
    //==================================================================================

    private void loadPutativeOntology() {
        pModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        RDFDataMgr.read(pModel, "test_efs.ttl");
        //pModel.listClasses().forEach(cl -> System.out.println(cl));
    }

    private void loadDomainOntoImports() {
        /*for (String uri : importURIs) {
            OntModel dModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
            dModel.read(uri);
            pModel.addSubModel(dModel);
        }*/
        OntModel dModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        RDFDataMgr.read(dModel, "C:\\Users\\karal\\progr\\workspace\\AutoMap4OBDA\\RUN\\FIBOFull.ttl");
        pModel.addSubModel(dModel);
        //pModel.listClasses().forEach(System.out::println);
    }


    private void setHierarchy() {
        for(Table tableMaps : tablesMaps) {

            if(tableMaps.getMapping().hasMatch())
                setSubClassOf(tableMaps.getMapping());

            for(Column colMaps : tableMaps.getColumns())
                setColumnHierarchy(colMaps);
        }
    }
    private void setColumnHierarchy(Column colMap) {
        Mapping objMap   = colMap.getObjectPropMapping();
        Mapping classMap = colMap.getClassPropMapping();
        Mapping dataMap  = colMap.getDataPropMapping();

        // a resource path has been set for obj prop mapping
        /*if(objMap.getPathURIs() != null) {

            List<String> path = objMap.getPathResources();

            // first resource on the path is a class
            if(Character.isUpperCase(path.get(0).charAt(0)))
                bl.append(getPropertyNodeString(objMap.getOntoElResource()));

            for (String pathNode : path)
                if (Character.isLowerCase(pathNode.charAt(0)))
                    bl.append(getPropertyNodeString(pathNode));
                else
                    bl.append(getClassNodeString(pathNode));
        }*/

        if(objMap.hasMatch())
            setSubPropertyOf(objMap);

        /*else if (classMap.hasMatch())
            bl.append(getPropertyNodeString(objMap.getOntoElResource()));*/


        if(classMap.hasMatch())
            setSubClassOf(classMap);


        /* if(dataMap.getPathURIs() != null) {

            List<String> path = dataMap.getPathResources();
            if(Character.isUpperCase(path.get(0).charAt(0)))
                bl.append(getPropertyNodeString(dataMap.getOntoElResource()));

            for (String pathNode : path)
                if (Character.isLowerCase(pathNode.charAt(0)))
                    bl.append(getPropertyNodeString(pathNode));
                else
                    bl.append(getClassNodeString(pathNode));
        }*/

        if(dataMap.hasMatch()) {
            setSubPropertyOf(dataMap);
            if(!dataMap.isCons())
                makeDataPropertyConsistent(dataMap);
        }


        /*else if (classMap.hasMatch())
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));*/


        /*if(! (objMap.hasMatch() || classMap.hasMatch() || dataMap.hasMatch()))
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


        bl.append(getClassNodeString("VALUE"));*/

    }

    private OntClass getOntClass(URI uri) {
        return pModel.getOntClass(uri.toString());
    }
    private OntProperty getOntProperty(URI uri) {
        return pModel.getOntProperty(uri.toString());
    }

    private void setSubClassOf(Mapping map) {

        OntClass pClass = getOntClass(map.getOntoElURI());
        OntClass dClass = getOntClass(map.getMatchURI());

        if (pClass != null && dClass != null)
            dClass.addSubClass(pClass);
        else
            System.err.println("CLASS NOT FOUND " + map.getOntoElURI().toString() + " " + map.getMatchURI().toString());
    }
    private void setSubPropertyOf(Mapping map) {
        OntProperty pProp = getOntProperty(map.getOntoElURI());
        OntProperty dProp = getOntProperty(map.getMatchURI());

        if (pProp != null && dProp != null)
            dProp.addSubProperty(pProp);
        else
            System.err.println("PROPERTY NOT FOUND " + map.getOntoElURI().toString() + " " + map.getMatchURI().toString());
    }

    private void makeDataPropertyConsistent(Mapping dataMap) {
        OntResource newRange = getOntProperty(dataMap.getMatchURI()).getRange();
        OntProperty onProperty = getOntProperty(dataMap.getOntoElURI());
        onProperty.setRange(newRange);

        OntClass DClass = onProperty.getDomain().asClass();
        removeRestriction(DClass, onProperty);
        addRangeRestriction(DClass, onProperty, newRange);
    }

    private void removeRestriction(OntClass DClass, OntProperty onProperty) {
        List<Statement> toRemove = new ArrayList<>();
        StmtIterator it = pModel.listStatements(DClass, null, (RDFNode) null);
        while (it.hasNext()) {
            Statement stmt = it.nextStatement();
            if (stmt.getObject().canAs(Restriction.class))
                if (stmt.getObject().as(Restriction.class).onProperty(onProperty))
                    toRemove.add(stmt);
        }
        toRemove.forEach(pModel::remove);
    }

    private void addRangeRestriction(OntClass DClass, OntProperty onProperty, OntResource newRange) {
        SomeValuesFromRestriction restriction = pModel.createSomeValuesFromRestriction(null, onProperty, newRange);
        DClass.addSuperClass(restriction.asClass());
    }

    //==================================================================================

    private void saveOutputOntology() {
        OutputStream out = null;
        try {
            out = new FileOutputStream("outputOntology.ttl");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        pModel.write(out, "TURTLE");
        addOntologyMetadata();
    }


    private void addOntologyMetadata() {
        String basePrefix = pModel.getNsPrefixURI("");
        System.out.println(basePrefix);

        String filePath = "outputOntology.ttl";

        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));

            // Find the index of the last "@prefix" line
            int lastIndex = -1;
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (line.startsWith("@prefix")) {
                    lastIndex = i;
                    break;
                }
            }

            if (lastIndex != -1) {
                lines.add(lastIndex + 1, String.format("<%s> rdf:type owl:Ontology .", basePrefix));

                for(String uri : importURIs)
                    lines.add(lastIndex + 1, String.format("<%s> owl:imports <%s> .", basePrefix, uri));

                // Find the index of the first triple pattern and insert a newline before it
                int firstTripleIndex = -1;
                for (int i = lastIndex + 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.matches("^:[^\\s]+.*")) {
                        firstTripleIndex = i;
                        break;
                    }
                }
                if (firstTripleIndex != -1)
                    lines.add(firstTripleIndex, "");

                // Write the modified lines back to the file
                Files.write(Paths.get(filePath), lines);
            }
        }catch (IOException e) {
            e.printStackTrace();
        }

    }

    //==================================================================================

    private void readMapJSON() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("EFS_mappings.json")) {
            // Convert JSON file to Java object
            tablesMaps = gson.fromJson(reader, JSONFormatClasses.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void gatherImports() {
        for(Table tableMaps : tablesMaps) {
            if(tableMaps.getMapping().hasMatch())
                importURIs.add(extractOntoModule(tableMaps.getMapping().getMatchURI()));

            for(Column colMaps : tableMaps.getColumns())
                for(Mapping map : colMaps.getMappings()) {
                    if(map.hasMatch())
                        importURIs.add(extractOntoModule(map.getMatchURI()));

                    if(map.getPathURIs() != null)
                        for(java.net.URI pURI : map.getPathURIs())
                            importURIs.add(extractOntoModule(pURI));
                }
        }
        System.out.println(importURIs);
    }

    private String extractOntoModule(java.net.URI uri) {
        return uri.toString().substring(0, uri.toString().lastIndexOf("/")) + "/";
    }

    //==================================================================================
    public static void main(String[] args) {
        new CreateOntology();
    }

}
