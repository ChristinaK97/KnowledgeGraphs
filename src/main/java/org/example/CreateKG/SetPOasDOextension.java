package org.example.CreateKG;

import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;

import org.example.mappingsFiles.MappingsFileTemplate.Table;
import org.example.mappingsFiles.MappingsFileTemplate.Column;
import org.example.mappingsFiles.MappingsFileTemplate.Mapping;

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
        super(POontology);

        gatherImports();
        loadDomainOntoImports();
        setHierarchy();
        restoreConsistency();
        saveOutputOntology();
    }


//======================================================================================================================
// 1. LOAD PUTATIVE AND DOMAIN ONTOLOGIES
//======================================================================================================================

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



//======================================================================================================================
// 2. DEFINE THE PUTATIVE ONTOLOGY AS EXTENSION OF THE DOMAIN ONTOLOGY
//======================================================================================================================

    private void setHierarchy() {

        /* 1. For each table in the db :
         *      2. If the table class has been matched with a DO class:
         *         3. Set the table class as a subclass of the DO class
         *      4. For each column in the table:
         *         5. Define hierarchical relationship for the column's matches
         */
        for(Table tableMaps : tablesMaps) {                //1

            if(tableMaps.getMapping().hasMatch())          //2
                setSubClassOf(tableMaps.getMapping());     //3

            for(Column colMaps : tableMaps.getColumns())   //4
                setColumnHierarchy(colMaps);               //5
        }
    }


    private void setColumnHierarchy(Column colMap) {
        /* Defines the PO elements that express the column as
         * sub-elements of the matched DO elements.
         * A column can be expressed by:
         *  - an object property //1
         *  - a class            //2
         *  - a data property    //3
         * some of these elements might have been matched with DO elements.
         *
         * 4. If only the data PO property has been matched, the column expresses
         *    a pure data attribute. The PO object property and class are unnecessary,
         *    so they are deleted from the ontology model.
         * 5. For each PO element that has been matched, set hierarchical relationships with the DO
         *
         */
        Mapping objMap   = colMap.getObjectPropMapping(); //1
        Mapping classMap = colMap.getClassPropMapping();  //2
        Mapping dataMap  = colMap.getDataPropMapping();   //3

        // TODO dataMap.hasDataProperty() fix missing domain
        if((dataMap.hasMatch() || dataMap.getPathURIs() != null)  //4
                && !objMap.hasMatch() && !classMap.hasMatch())
        {
            /**/deleteClass(classMap.getOntoElURI());
            colMap.delClassPropMapping();

            deleteProperty(objMap.getOntoElURI());
            colMap.delObjectPropMapping();
        }
        //5
        if(objMap.hasMatch())
            setSubPropertyOf(objMap);

        if(classMap.hasMatch())
            setSubClassOf(classMap);

        if(dataMap.hasMatch())
            setSubPropertyOf(dataMap);
    }


    private void setSubClassOf(Mapping map) {
        /* Map contains the URIs of the PO class and the matched DO class
         * 1. Retrieve the PO class from the ontology
         * 2. Retrieve the DO class from the ontology
         * 3. If both classes have been defined in the ontology model :
         *      4. Set the PO class as a subclass of the DO class
         */
        OntClass pClass = getOntClass(map.getOntoElURI());      //1
        OntClass dClass = getOntClass(map.getMatchURI());       //2

        if (pClass != null && dClass != null)                   //3
            dClass.addSubClass(pClass);                         //4
        else
            System.err.println("CLASS NOT FOUND " + map.getOntoElURI().toString() + " " + map.getMatchURI().toString());
    }

    private void setSubPropertyOf(Mapping map) {
        /* Map contains the URIs of the PO property and the matched DO property
         * 1. Retrieve the PO property from the ontology
         * 2. Retrieve the DO property from the ontology
         * 3. If both properties have been defined in the ontology model :
         *      4. Set the PO property as a sub-property of the DO property
         */
        OntProperty pProp = getOntProperty(map.getOntoElURI());
        OntProperty dProp = getOntProperty(map.getMatchURI());

        if (pProp != null && dProp != null)
            dProp.addSubProperty(pProp);
        else
            System.err.println("PROPERTY NOT FOUND " + map.getOntoElURI().toString() + " " + map.getMatchURI().toString());
    }


    private void deleteClass(URI classURI) {
        /*
         * 1. Retrieve the class (PO) with the given classURI and if it hasn't already been deleted:
         *      2. Remove all anonymous restriction nodes of the class from the ontology model
         *      3. Remove all triples that contain this class as subject or object
         *      4. Delete the class from the ontology
         */
        OntClass ontClass = getOntClass(classURI);                          // 1
        if(ontClass != null){
            System.out.println("DEL " + ontClass.getLocalName());
            removeRestriction(ontClass, null);                    //2
            pModel.removeAll(ontClass, null, null);                   //3
            pModel.removeAll(null, null, ontClass);
            ontClass.remove();                                             //4
        }
    }


    private void deleteProperty(URI propURI) {
        /* 1. Retrieve the property (PO) from the ontology model and if it hasn't already been deleted:
         *      2. For each class in the domain of the property (union of classes -2.1- or single class -2.2-):
         *          3. Delete the restriction statements that connects the domain class with this property (and del the anonymous node)
         *      4. Remove all triples that contain this property
         *      5. Delete the property form the ontology model
         */
        OntProperty property = getOntProperty(propURI);     // 1
        if(property != null) {
            System.out.println("DEL "+ property.getLocalName());

            // remove restriction statements and anonymous restriction classes containing the property
            OntResource domain = property.getDomain();
            if(domain != null) {
                if(domain.canAs(UnionClass.class)) {                //2.1
                    for (OntClass DClass : domain.as(UnionClass.class).listOperands().toList())
                        removeRestriction(DClass, property);

                    // remove anonymous union domain class
                    domain.as(UnionClass.class).remove();
                }else   //2.2
                    removeRestriction(domain.asClass(), property);
            }
            //4
            pModel.removeAll(property, null, null);
            pModel.removeAll(null, property, null);
            pModel.removeAll(null, null, property);
            property.remove();  //5
        }
    }

//======================================================================================================================
// 3. RESTORE THE CONSISTENCY OF THE RESULTING ONTOLOGY
//======================================================================================================================

    private void restoreConsistency() {
        /*
         * After defining the PO as an extension of the DO some steps need to be performed to restore
         * the consistency of the ontology.
         * 1. For each table in the database, and for each column on this table:
         *      2. For the object PO property and the data PO property that express the column:
         *              3. Specialize the paths: Replace each DO class in the path with the specialized PO TableClass subclass
         *                  (if such TableClass exists)
         *              4. Correct the domain (and the range) of the PO property
         *              5. Handle the case where the first element in the mapping path is a DO class
         */
        for(Table tableMaps : tablesMaps)
            for(Column colMap : tableMaps.getColumns()) {               //1
                System.out.println("MAKE CONS : " + tableMaps.getTable() + "." + colMap.getColumn());

                Mapping objMap   = colMap.getObjectPropMapping();       //2
                Mapping dataMap  = colMap.getDataPropMapping();

                try {
                    specialisePathDOclasses(objMap);                    //3
                    makeObjPropConsistent(objMap, tableMaps.getMapping().getOntoElResource());                      //4
                    handleClassAsFirstPathNode(                         //5
                            tableMaps.getMapping().getOntoElResource(),
                            tableMaps.getMapping().getOntoElURI(), objMap);
                }catch (NullPointerException e) {
                    // property was unnecessary so it was previously deleted
                }
                specialisePathDOclasses(dataMap);                       //3
                makeDataPropertyConsistent(dataMap, tableMaps.getMapping().getOntoElResource());                    //4
                handleClassAsFirstPathNode(                             //5
                        tableMaps.getMapping().getOntoElResource(),
                        tableMaps.getMapping().getOntoElURI(), dataMap);

                System.out.println("-------");
            }
    }


    private void makeObjPropConsistent(Mapping objMap, String tableClass) {
        System.out.println("MAKE OBJ PROP CONS " + objMap.getOntoElResource());
        correctDomain(objMap, tableClass);
    }


    private void makeDataPropertyConsistent(Mapping dataMap, String tableClass) {
        correctDomain(dataMap, tableClass);

        if(!dataMap.hasMatch() || dataMap.isCons())
            return;

        OntResource newRange = getOntProperty(dataMap.getMatchURI()).getRange();
        OntProperty onProperty = getOntProperty(dataMap.getOntoElURI());
        correctRange(onProperty, newRange);
    }

//  DOMAIN =============================================================================================================

    private void correctDomain(Mapping map, String tableClass) {
        // Mapping pattern (the match has been performed through a path of DO elements):
        // (tableClass) - PATH -[property]-> ...
        // but the property's current domain (curDomain) == tableClass
        // newDomain must be equal to the last class node in the path

        // domain doesn't need to be corrected. Match is direct, not through a path
        if(map.getPathURIs() == null)
            return;

        OntProperty prop = getOntProperty(map.getOntoElURI());
        OntResource curDomain = prop.getDomain();
        OntResource newDomain = getOntClass(getLastNodeFromPath(map.getPathURIs()));

        System.out.println("PATH IS NOT NULL. DOMAIN WILL BE CORRECTED. PATH = " + map.getPathResources());
        correctDomain(prop, curDomain, tableClass, newDomain);
    }


    /**
     * prop current domain contains table class. The table class must be replaced by the new domain class
     * since (tableClass) -[...]-> (lastClass on path aka newDomain) -[prop]-> (...)
     * / The connection of prop with table class is not direct but through a path.
     * @param prop The property whose domain must be corrected
     * @param curDomain The current domain of the property
     * @param tableClass the class of the table that will be replaced in the domain of the property by the new domain
     * @param newDomain The new domain of the property
     */
    private void correctDomain(OntProperty prop, OntResource curDomain, String tableClass, OntResource newDomain) {
        /* 0. curDomain of a PO data property can be null if only the dp was matched and maintained and the PO class
         *    (and PO objProp) were previously deleted
         * 1. If the current domain is a union of classes:
         *      2. For each class (operand) in the union:
         *              3. If the operand is the table class that should be replaced:
         *                      4. The new domain class will replace the operand in the union domain of the property
         *                      5. Go to the operand class and remove the restriction "prop some range"
         *              6. Keep all other operand in the union the same
         *              7. Remove the anonymous union class of the previous domain classes
         *              8. Create the new union domain
         * 9. Else, if the current domain is a single class
         *      10. Go to this class and remove the restriction "prop some range"
         * 11. Set newDomain as the domain of the prop
         */
        System.out.println("PROP : " + prop.getLocalName() + " CURDOM: " + curDomain + " NEWDOM: " + newDomain);

        if(curDomain != null) {                                                                                    //0
            if (curDomain.canAs(UnionClass.class)) {                                                               //1
                HashSet<OntClass> unionDomainClasses = new HashSet<>();
                UnionClass unionClass = curDomain.as(UnionClass.class);

                for(OntClass operand : unionClass.listOperands().toList()) {                                       //2
                    System.out.println("Cur Union operand: " + operand.getLocalName());

                    if(operand.getLocalName().equals(tableClass)) {                                                //3
                        unionDomainClasses.add(newDomain.asClass());                                               //4
                        removeRestriction(operand, prop);                                                          //5
                    }else
                        unionDomainClasses.add(operand);                                                           //6
                }
                curDomain.as(UnionClass.class).remove();                                                            //7
                newDomain = unionDomainClasses.size() > 1 ?                                                         //8
                            pModel.createUnionClass(null, pModel.createList(unionDomainClasses.iterator())) :   // multiple classes as union
                            unionDomainClasses.iterator().next();                                                   // a single class
            }else                                                                                                   //9
                removeRestriction(curDomain.asClass(), prop);                                                       //10
        }

        System.out.println("New Domain:");
        printClass(newDomain);

        prop.setDomain(newDomain);                                                                                  //11
        System.out.println();
    }


//  RANGE ==============================================================================================================
    private void correctRange(OntProperty property, OntResource newRange) {
        /* 1. Set the new range of the property
         * 2. Go to the property's domain class DClass
         * 3. Remove restriction "property some oldRange" on DClass
         * 4. Add restriction    "property some newRange" on DClass
         */
        property.setRange(newRange);

        OntClass DClass = property.getDomain().asClass();
        removeRestriction(DClass, property);
        addRangeRestriction(DClass, property, newRange);
    }


//  RESTRICTIONS =======================================================================================================

    /**
     * Removes a restriction statement
     *      DClass subClassOf [
     *          a owl:Restriction ;
     *          owl:onProperty onProperty ;
     *          ...
     *      ]
     * for the class DClass (Domain Class), and deletes the anonymous restriction node from the model
     * @param DClass the class that has the restriction
     * @param onProperty the property of the restriction. To remove all property restrictions from a
     *                   DClass set this argument to null.
     */
    private void removeRestriction(OntClass DClass, OntProperty onProperty) {
        /* 1. list that will gather the restriction statements that will be deleted
         * 2. Retrieve all the statements <DClass, subClassOf, _>
         * 3. For each such statement, if it is a restriction:
         *      4. If the restrictions of all properties should be removed from the class or
         *         if the restriction is about the given property:
         *              5. Add the statement to toRemove list
         * 6. Delete every restriction statement <DClass, subClassOf, an anonymous restriction node>
         * 7. Delete the anonymous restriction node from the model
         */
        List<Statement> toRemove = new ArrayList<>(); //1
        StmtIterator it = pModel.listStatements(DClass, RDFS.subClassOf, (RDFNode) null);  //2
        while (it.hasNext()) {  //3
            Statement stmt = it.nextStatement();
            if (stmt.getObject().canAs(Restriction.class))
                if (onProperty == null || stmt.getObject().as(Restriction.class).onProperty(onProperty))  //4
                    toRemove.add(stmt);  //5
        }
        System.out.println(toRemove);
        toRemove.forEach(pModel::remove);  //6
        for(Statement stmt : toRemove)     //7
            pModel.removeAll(stmt.getObject().asResource(), null, null);

    }

    /**
     * Defines a restriction
     * DClass subClassOf [
     *      a owl:Restriction ;
     *      owl:onProperty onProperty ;
     *      owl:someValuesFrom newRange
     * ]
     * or DClass subClassOf (onProperty some newRange)
     * @param DClass the domain class
     * @param onProperty the property of the restriction
     * @param newRange the range of values of the property for this class
     */
    private void addRangeRestriction(OntClass DClass, OntProperty onProperty, OntResource newRange) {
        SomeValuesFromRestriction restriction = pModel.createSomeValuesFromRestriction(null, onProperty, newRange);
        DClass.addSuperClass(restriction.asClass());
    }


//  FIRST CLASS ON PATH ================================================================================================

    private void handleClassAsFirstPathNode(String tableClassName, URI tableClassURI, Mapping map) {
        try {
            OntProperty prop = getOntProperty(map.getOntoElURI());
            OntClass firstClass = getOntClass(getFirstNodeFromPath(map.getPathURIs()));
            String firstClassName = firstClass.getLocalName();

            // Create a new property with the specified URI
            // (tableClass) -[newProperty]-> (dOnto:firstClass)

            String newPropURI = getNewPropertyURI(null, firstClass, tableClassName);

            // property wasn't already created
            if (getOntProperty(newPropURI) == null) {

                System.out.println("FIRST CLASS : " + firstClass);
                System.out.println("NEW PROP :" + newPropURI);

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

//======================================================================================================================
// 4. SAVE THE RESULTING ONTOLOGY TO outputOntology.ttl
//======================================================================================================================
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

