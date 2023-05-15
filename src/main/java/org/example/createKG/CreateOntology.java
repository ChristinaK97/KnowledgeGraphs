package org.example.createKG;

import com.google.gson.Gson;
import org.apache.jena.assembler.ImportManager;
import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.rdf.model.ModelFactory;
import org.example.other.JSONFormatClasses;
import org.example.other.JSONFormatClasses.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.*;
import java.net.URI;
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
        for (String uri : importURIs) {
            OntModel dModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
            dModel.read(uri);
            pModel.addSubModel(dModel);
        }
        /*OntModel dModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        RDFDataMgr.read(dModel, "C:\\Users\\karal\\progr\\workspace\\AutoMap4OBDA\\RUN\\FIBOFull.ttl");
        pModel.addSubModel(dModel);*/
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

        if(dataMap.hasMatch())
            setSubPropertyOf(dataMap);

        /*else if (classMap.hasMatch())
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));*/


        /*if(! (objMap.hasMatch() || classMap.hasMatch() || dataMap.hasMatch()))
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


        bl.append(getClassNodeString("VALUE"));*/

    }

    private void setSubClassOf(Mapping map) {

        OntClass pClass = pModel.getOntClass(map.getOntoElURI().toString());
        OntClass dClass = pModel.getOntClass(map.getMatchURI().toString());

        if (pClass != null && dClass != null)
            dClass.addSubClass(pClass);
        else
            System.err.println("CLASS NOT FOUND " + map.getOntoElURI().toString() + " " + map.getMatchURI().toString());
    }
    private void setSubPropertyOf(Mapping map) {
        OntProperty pProp = pModel.getOntProperty(map.getOntoElURI().toString());
        OntProperty dProp = pModel.getOntProperty(map.getMatchURI().toString());

        if (pProp != null && dProp != null)
            dProp.addSubProperty(pProp);
        else
            System.err.println("PROPERTY NOT FOUND " + map.getOntoElURI().toString() + " " + map.getMatchURI().toString());
    }

    //==================================================================================

    private void saveOutputOntology() {
        OutputStream out = null;
        try {
            out = new FileOutputStream("outputOntologyTemp.ttl");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        pModel.write(out, "TURTLE");
        addImports();
    }

    private void addImports() {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try {
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File("outputOntologyTemp.ttl"));
            for(String uri : importURIs) {
                OWLImportsDeclaration importDeclaration = manager.getOWLDataFactory().getOWLImportsDeclaration(IRI.create(uri));
                manager.applyChange(new AddImport(ontology, importDeclaration));
            }
            File outputFile = new File("outputOntology.ttl");
            manager.saveOntology(ontology, IRI.create(outputFile.toURI()));
            new File("outputOntologyTemp.ttl").delete();

        } catch (OWLOntologyCreationException | OWLOntologyStorageException e) {
            System.err.println("Error ontology: " + e.getMessage());
        }
    }

    /*private void addImports() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("outputOntologyTemp.ttl"));
            BufferedWriter writer = new BufferedWriter(new FileWriter("outputOntology.ttl"));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches("<.*>.+rdf:type owl:Ontology .")) {
                    writer.write(line);
                    writer.newLine();

                    for (String uri : importURIs) {
                        writer.write(String.format("<%s> owl:imports <%s> .",
                                line.substring(line.indexOf("<")+1, line.indexOf(">")), uri));
                        writer.newLine();
                    }
                } else {
                    writer.write(line);
                    writer.newLine();
                }
            }
            reader.close();
            writer.close();
            new File("outputOntologyTemp.ttl").delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

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
