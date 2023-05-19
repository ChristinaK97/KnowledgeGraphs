package org.example.createKG;

import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.example.other.JSONMappingTableConfig.Column;
import org.example.other.JSONMappingTableConfig.Mapping;
import org.example.other.JSONMappingTableConfig.Table;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.example.other.Util.*;

public class SetPOasDOextension extends JenaOntologyModelHandler {


    private HashSet<String> importURIs = new HashSet<>();

    public SetPOasDOextension() {
        super("test_efs.ttl");

        gatherImports();
        loadDomainOntoImports();
        setHierarchy();
        restoreConsistency();
        saveOutputOntology();
    }
    //==================================================================================


    private void loadDomainOntoImports() {
        //TODO replace
        /*for (String uri : importURIs) {
            OntModel dModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
            dModel.read(uri);
            pModel.addSubModel(dModel);
        }*/
        OntModel dModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        RDFDataMgr.read(dModel, DOontology);
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

        if((dataMap.hasMatch() || dataMap.getPathURIs() != null)
                && !objMap.hasMatch() && !classMap.hasMatch())
        {
            /**/deleteClass(classMap.getOntoElURI());
            colMap.delClassPropMapping();

            deleteProperty(objMap.getOntoElURI());
            colMap.delObjectPropMapping();
        }

        if(objMap.hasMatch())
            setSubPropertyOf(objMap);

        if(classMap.hasMatch())
            setSubClassOf(classMap);

        if(dataMap.hasMatch())
            setSubPropertyOf(dataMap);
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


    private void deleteClass(URI classURI) {
        OntClass ontClass = getOntClass(classURI);
        // if not already deleted
        if(ontClass != null){
            System.out.println("DEL " + ontClass.getLocalName());
            removeRestriction(ontClass, null);
            pModel.removeAll(ontClass, null, null);
            pModel.removeAll(null, null, ontClass);
            ontClass.remove();
        }
    }


    private void deleteProperty(URI propURI) {
        OntProperty property = getOntProperty(propURI);
        if(property != null) {
            System.out.println("DEL "+ property.getLocalName());

            // remove restriction statements and anonymous restriction classes containing the property
            OntResource domain = property.getDomain();
            if(domain.canAs(UnionClass.class)) {
                for (OntClass DClass : domain.as(UnionClass.class).listOperands().toList())
                    removeRestriction(DClass, property);

                // remove anonymous union domain class
                domain.as(UnionClass.class).remove();
            }else
                removeRestriction(domain.asClass(), property);

            pModel.removeAll(property, null, null);
            pModel.removeAll(null, property, null);
            pModel.removeAll(null, null, property);
            property.remove();
        }
    }


    private void restoreConsistency() {
        for(Table tableMaps : tablesMaps)
            for(Column colMap : tableMaps.getColumns()) {
                Mapping objMap   = colMap.getObjectPropMapping();
                // Mapping classMap = colMap.getClassPropMapping();
                Mapping dataMap  = colMap.getDataPropMapping();

                try {
                    specialisePathDOclasses(objMap);
                    makeObjPropConsistent(objMap);
                    handleClassAsFirstPathNode(
                            tableMaps.getMapping().getOntoElResource(),
                            tableMaps.getMapping().getOntoElURI(), objMap);
                }catch (NullPointerException e) {
                    // property was unnecessary so it was previously deleted
                }
                specialisePathDOclasses(dataMap);
                makeDataPropertyConsistent(dataMap);
                handleClassAsFirstPathNode(
                        tableMaps.getMapping().getOntoElResource(),
                        tableMaps.getMapping().getOntoElURI(), dataMap);
            }
    }


    private void makeDataPropertyConsistent(Mapping dataMap) {
        correctDomain(dataMap);

        if(!dataMap.hasMatch() || dataMap.isCons())
            return;

        OntResource newRange = getOntProperty(dataMap.getMatchURI()).getRange();
        OntProperty onProperty = getOntProperty(dataMap.getOntoElURI());
        correctRange(onProperty, newRange);
    }

    private void makeObjPropConsistent(Mapping objMap) {
        correctDomain(objMap);
    }


    private void correctDomain(Mapping map) {
        // (tableClass) - PATH -[property]-> ...
        // but property curDomain == tableClass
        // newDomain must be equal to the last class node in the path

        // domain doesn't need to be corrected. Match is direct, not through a path
        if(map.getPathURIs() == null)
            return;

        OntProperty prop = getOntProperty(map.getOntoElURI());
        OntResource curDomain = prop.getDomain();
        OntResource newDomain = getOntClass(getLastNodeFromPath(map.getPathURIs()));

        System.out.println(map.getOntoElResource());
        correctDomain(prop, curDomain, newDomain);
    }


    private void correctDomain(OntProperty prop, OntResource curDomain, OntResource newDomain) {
        System.out.println(curDomain);

        if(curDomain != null) {
            if (curDomain.canAs(UnionClass.class)) {
                List<OntClass> unionDomainClasses = new ArrayList<>();
                UnionClass unionClass = curDomain.as(UnionClass.class);

                for(OntClass operand : unionClass.listOperands().toList()) {
                    boolean hasSuperClass = false;
                    System.out.println("Union operand: " + operand.getLocalName());

                    for(OntClass superclass : operand.listSuperClasses().toList())
                        if (superclass.isURIResource() && !superclass.getLocalName().equals(TABLE_CLASS)) {
                            System.out.println(superclass);
                            hasSuperClass = true;
                            unionDomainClasses.add(superclass);
                            removeRestriction(operand, prop);
                        }

                    if(!hasSuperClass)
                        unionDomainClasses.add(operand);
                }
                // remove current anonymous union domain node
                curDomain.as(UnionClass.class).remove();
                newDomain = pModel.createUnionClass(null, pModel.createList(unionDomainClasses.iterator()));
            }else
                removeRestriction(curDomain.asClass(), prop);
        }

        System.out.println("New Domain:");
        printClass(newDomain);

        prop.setDomain(newDomain);
        System.out.println();
    }


    private void correctRange(OntProperty property, OntResource newRange) {
        property.setRange(newRange);

        OntClass DClass = property.getDomain().asClass();
        removeRestriction(DClass, property);
        addRangeRestriction(DClass, property, newRange);
    }

    private void removeRestriction(OntClass DClass, OntProperty onProperty) {
        List<Statement> toRemove = new ArrayList<>();
        StmtIterator it = pModel.listStatements(DClass, RDFS.subClassOf, (RDFNode) null);
        while (it.hasNext()) {
            Statement stmt = it.nextStatement();
            if (stmt.getObject().canAs(Restriction.class))
                if (onProperty == null || stmt.getObject().as(Restriction.class).onProperty(onProperty))
                    toRemove.add(stmt);
        }
        System.out.println(toRemove);
        toRemove.forEach(pModel::remove);
        for(Statement stmt : toRemove)
            pModel.removeAll(stmt.getObject().asResource(), null, null);

    }

    private void addRangeRestriction(OntClass DClass, OntProperty onProperty, OntResource newRange) {
        SomeValuesFromRestriction restriction = pModel.createSomeValuesFromRestriction(null, onProperty, newRange);
        DClass.addSuperClass(restriction.asClass());
    }


    private void handleClassAsFirstPathNode(String tableClassName, URI tableClassURI, Mapping map) {
        try {
            OntProperty prop = getOntProperty(map.getOntoElURI());
            OntClass firstClass = getOntClass(getFirstNodeFromPath(map.getPathURIs()));
            String firstClassName = firstClass.getLocalName();

            // Create a new property with the specified URI
            // (tableClass) -[newProperty]-> (dOnto:firstClass)

            String newPropURI = getNewPropertyURI(null, firstClass, tableClassName);

            if (getOntProperty(newPropURI) == null) {

                System.out.println("FIRST CLASS : " + firstClass);
                System.out.println("NEW PROP :" + newPropURI);

                // property wasn't already created
                String newLabel = String.format("has %s", normalise(getLabel(firstClass)));

                OntProperty newProp = pModel.createObjectProperty(newPropURI);
                newProp.setLabel(newLabel, "en");
                newProp.setComment(String.format("New prop to connect tableClass \"%s\" with firstPathClass \"%s\"", tableClassName, firstClassName), "en");
                newProp.addComment(prop.getComment(""), "en");
                newProp.setSuperProperty(getOntProperty(ATTRIBUTE_PROPERTY_URI));

                OntClass DtableClass = getOntClass(tableClassURI);
                newProp.setDomain(DtableClass);
                newProp.setRange(firstClass);
                addRangeRestriction(DtableClass, newProp, firstClass);

                // Modify existing property
                // (dOnto:firstClass) -[ontoEl property]-> (same Range)
                correctDomain(prop, prop.getDomain(), firstClass);
            }
        }catch (NullPointerException e) {
            //TODO remove print
            if(map != null && map.getPathURIs() != null && getOntClass(getFirstNodeFromPath(map.getPathURIs())) != null)
                e.printStackTrace();
            // Either the map doesn't contain a path and so getPFirstNode throws NPE
            // or the first node in the path wasn't a class but a property and
            // so getOntClass returned null. No modifications are needed in this case
         }
    }



    //==================================================================================

    private void saveOutputOntology() {
        OutputStream out = null;
        try {
            out = new FileOutputStream(outputOntology);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        pModel.write(out, "TURTLE");
        addOntologyMetadata();
    }


    private void addOntologyMetadata() {
        String basePrefix = pModel.getNsPrefixURI("");
        System.out.println(basePrefix);

        String filePath = outputOntology;

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
                // lines.add(lastIndex + 1, String.format("<%s> rdf:type owl:Ontology .", basePrefix));

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
    private void printClass(OntResource cl) {
        if (cl.canAs(UnionClass.class)) {
            UnionClass unionClass = cl.as(UnionClass.class);
            ExtendedIterator<? extends OntClass> operands = unionClass.listOperands();
            while (operands.hasNext()) {
                OntClass operand = operands.next();
                System.out.println("Union operand: " + operand.getLocalName());
            }
        }else {
            System.out.println(cl.getURI());
        }
    }

    //==================================================================================
    public static void main(String[] args) {
        new SetPOasDOextension();
    }

}


/*
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
        }

        if((dataMap.hasMatch() || dataMap.getPathURIs() != null)
                && !objMap.hasMatch() && !classMap.hasMatch())
                {
                deleteClass(classMap.getOntoElURI());
                colMap.delClassPropMapping();

                deleteProperty(objMap.getOntoElURI());
                colMap.delObjectPropMapping();
                }

                if(objMap.hasMatch())
                setSubPropertyOf(objMap);


        /*else if (classMap.hasMatch())
            bl.append(getPropertyNodeString(objMap.getOntoElResource()));


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
        }

                if(dataMap.hasMatch())
                setSubPropertyOf(dataMap);



        /*else if (classMap.hasMatch())
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


        /*if(! (objMap.hasMatch() || classMap.hasMatch() || dataMap.hasMatch()))
            bl.append(getPropertyNodeString(dataMap.getOntoElResource()));


        bl.append(getClassNodeString("VALUE"));

                }
 */