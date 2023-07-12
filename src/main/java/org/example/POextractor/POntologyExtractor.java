package org.example.POextractor;

import org.example.InputPoint.SQLdb.DBSchema;
import org.example.POextractor.Properties.DomRan;
import org.example.util.Util;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.example.util.Util.POontology;

public class POntologyExtractor {

    private RulesetApplication rs;

    private String msBasePrefix;
    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;
    private PrefixManager pm;
    private final boolean turnAttributesToClasses = true;
    private final boolean includeInverseAxiom = false;

    private HashMap<String, OWLObject> baseElements = new HashMap<>(4);

    public POntologyExtractor(Object dataSource, String ontologyName) {
        rs = new RulesetApplication(turnAttributesToClasses);
        rs.applyRules(dataSource);
        msBasePrefix = "http://www.example.net/ontologies/" + ontologyName + ".owl/";
        createOntology();
        saveOntology(POontology);

        // new MappingsFileExtractor(dataSource, msBasePrefix, rs);


        //new JSONExtractor().createMappingJSON_forFKobjectProperties(db, msBbasePrefix, convertedIntoClass, objProperties);

    }

    public void createOntology(){
        IRI ontologyIRI = IRI.create(msBasePrefix);
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        pm = new DefaultPrefixManager(ontologyIRI.getIRIString());
        try {
            ontology = manager.createOntology(ontologyIRI);

            // add elements to the ontology
            createBaseElements();
            addClasses();
            addObjectProperties();
            rs.dataProperties.getProperties().forEach(this::addDatatype);

        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }


    private void createBaseElements() {
        baseElements.put("TableClass", addClass("TableClass", "TableClass", null));
        baseElements.put("AttributeClass", addClass("AttributeClass", "AttributeClass", null));

        for(String propName : new String[]{"FKProperty", "AttributeProperty"}) {
            OWLObjectProperty objproperty = factory.getOWLObjectProperty(propName, pm);
            baseElements.put(propName, objproperty);
            manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(objproperty));
        }

    }

    // CLASSES
    private void addClasses() {
        rs.classes.forEach((type, classes) -> {
            classes.forEach((elementName, elementClass) -> {
                String sDescription = String.format("%s %s converted to class %s", type, elementName, elementClass);
                addClass(elementClass, sDescription, type);
            });
        });
    }

    public OWLClass addClass(String className, String sDescription, String type) {
        OWLClass sClass = factory.getOWLClass(className, pm);
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(sClass));
        addDescriptions(sClass.getIRI(), className, sDescription);

        if(type != null)
            manager.applyChange(new AddAxiom(ontology,
                    factory.getOWLSubClassOfAxiom(sClass, (OWLClass) baseElements.get(type + "Class"))
            ));

        return sClass;
    }


    // OBJECT PROPERTIES
    private void addObjectProperties() {
        rs.objProperties.forEach((type, objProps) -> {
            objProps.getProperties().forEach((propName, domRan) ->
                    addObjectproperty(propName, domRan, type));
        });
    }

    public void addObjectproperty(String propName, DomRan domRan, String type) {

        OWLObjectProperty objproperty = factory.getOWLObjectProperty(propName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(objproperty);

        //Add object property
        manager.addAxiom(ontology, declaration);
        addDescriptions(objproperty.getIRI(), domRan.getObjectPropertyLabel(),
                String.format("%s from %s", domRan.rule.toString(), domRan.extractedField));

        ////////////////////////////////////
        // add domain
        Set<OWLClassExpression> domainClasses = new HashSet<>();
        for (String className : domRan.domain)
            domainClasses.add(factory.getOWLClass(className, pm));

        OWLClassExpression domainClass =
                domainClasses.size() > 1 ?
                factory.getOWLObjectUnionOf(domainClasses) : factory.getOWLClass(domRan.domain.iterator().next(), pm);

        OWLObjectPropertyDomainAxiom domainAxiom = factory.getOWLObjectPropertyDomainAxiom(objproperty, domainClass);
        manager.addAxiom(ontology, domainAxiom);

        ////////////////////////////////////
        // add range
        OWLClass rangeClass= factory.getOWLClass(domRan.range.iterator().next(), pm);
        OWLObjectPropertyRangeAxiom rangeAxiom = factory.getOWLObjectPropertyRangeAxiom(objproperty, rangeClass);
        manager.addAxiom(ontology, rangeAxiom);

        // domain (property someValuesFrom range)
        OWLObjectSomeValuesFrom restriction = factory.getOWLObjectSomeValuesFrom(objproperty, rangeClass);
        for (OWLClassExpression domClass : domainClasses) {
            OWLAxiom axiom = factory.getOWLSubClassOfAxiom(domClass, restriction);
            manager.applyChange(new AddAxiom(ontology, axiom));
        }

        // inverse property
        if(includeInverseAxiom) {
            OWLObjectProperty inverse = factory.getOWLObjectProperty(domRan.getInverse(), pm);

            if(ontology.containsObjectPropertyInSignature(inverse.getIRI()))
                manager.applyChange(new AddAxiom(ontology,
                        factory.getOWLInverseObjectPropertiesAxiom(objproperty, inverse)
                ));
        }

        // super property
        manager.addAxiom(ontology,
                factory.getOWLSubObjectPropertyOfAxiom(objproperty, (OWLObjectProperty) baseElements.get(type+"Property"))
        );

    }


    // DATA PROPERTIES

    public void addDatatype(String propName, DomRan domRan) {

        OWLDataProperty datatype = factory.getOWLDataProperty(propName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(datatype);
        addDescriptions(datatype.getIRI(), propName,
                        String.format("%s from %s", domRan.rule.toString(), domRan.extractedField));

        // Add datatype
        manager.addAxiom(ontology, declaration);
        OWLDataPropertyExpression man = factory.getOWLDataProperty(propName, pm);

        Set<OWLClassExpression> domainClasses = new HashSet<>();
        for (String className : domRan.domain)
            domainClasses.add(factory.getOWLClass(className, pm));

        OWLClassExpression domainClass =
                domainClasses.size() > 1 ?
                        factory.getOWLObjectUnionOf(domainClasses) : factory.getOWLClass(domRan.domain.iterator().next(), pm);

        OWLDataPropertyDomainAxiom domainAxiom = factory.getOWLDataPropertyDomainAxiom(man, domainClass);
        manager.addAxiom(ontology, domainAxiom);


        OWLDatatype dt = factory.getOWLDatatype(domRan.range.iterator().next(), pm);
        OWLDataPropertyRangeAxiom rangeAxiom = factory.getOWLDataPropertyRangeAxiom(man, dt);
        manager.addAxiom(ontology, rangeAxiom);

        for (OWLClassExpression domClass : domainClasses) {
            OWLDataSomeValuesFrom restriction = factory.getOWLDataSomeValuesFrom(datatype, dt);
            OWLAxiom axiom = factory.getOWLSubClassOfAxiom(domClass, restriction);
            manager.applyChange(new AddAxiom(ontology, axiom));
        }
    }


    private void addDescriptions(IRI iri, String resourceName, String description) {
        //Add label
        String label = Util.normalise(resourceName);
        OWLAnnotation sLabel = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
                factory.getOWLLiteral(label));

        manager.applyChange(new AddAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(iri, sLabel)));
        //Add description
        OWLAnnotation sDescription = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()),
                factory.getOWLLiteral(description));

        manager.applyChange(new AddAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(iri, sDescription)));
    }


    // SAVE TO TURTLE
    public void saveOntology(String sPath){

        File saveIn = new File(sPath);
        IRI fileIRI = IRI.create(saveIn.toURI());
        TurtleDocumentFormat turtleFormat = new TurtleDocumentFormat();
        turtleFormat.setDefaultPrefix(ontology.getOntologyID().getOntologyIRI().get().getIRIString());
        try {
            manager.saveOntology(ontology, turtleFormat, fileIRI);
        } catch (OWLOntologyStorageException e) {
            throw new RuntimeException(e);
        }

    }


    public static void main(String[] args) {
        DBSchema db = new DBSchema();
        new POntologyExtractor(db, db.getSchemaName());

        //new POntologyExtractor(new ArrayList<String>(), "json");
    }

}


