package org.example.C_POextractor;

import static org.example.A_Coordinator.Runner.config;
import static org.example.util.Annotations.*;
import org.example.C_POextractor.Properties.DomRan;
import org.example.MappingsFiles.CreateMappingsFile;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;


public class POntologyExtractor {

    private RulesetApplication rs;

    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;
    private PrefixManager pm;

    private HashMap<String, OWLObject> baseElements = new HashMap<>(5);

    public POntologyExtractor(Object dataSource) {

        rs = new RulesetApplication(dataSource);
        createOntology();
        saveOntology();

        new CreateMappingsFile().extractMappingsFile(dataSource, rs);
        //new JSONExtractor().createMappingJSON_forFKobjectProperties(db, msBbasePrefix, tableClasses, objProperties);

    }

    public void createOntology(){
        IRI ontologyIRI = IRI.create(config.Out.POntologyBaseNS);
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        pm = new DefaultPrefixManager(ontologyIRI.getIRIString());
        try {
            ontology = manager.createOntology(ontologyIRI);

            // Import SKOS vocabulary for prefLabel and altLabel
            manager.applyChange(new AddImport(ontology, factory.getOWLImportsDeclaration(skosIRI)));

            // add elements to the ontology
            createBaseElements();
            addClasses();
            addObjectProperties();
            addDatatypeProperties();

        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }

//======================================================================================================================
// Base Elements
//======================================================================================================================
    private void createBaseElements() {
        baseElements.put(TABLE_CLASS, addClass(TABLE_CLASS, BASE_ELEMENT, null));
        baseElements.put(ATTRIBUTE_CLASS, addClass(ATTRIBUTE_CLASS, BASE_ELEMENT, null));

        for(String propName : new String[]{PURE_OBJ_PROPERTY, ATTRIBUTE_PROPERTY}) {
            OWLObjectProperty objproperty = factory.getOWLObjectProperty(propName, pm);
            baseElements.put(propName, objproperty);
            manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(objproperty));
            addAnnotations(objproperty.getIRI(), propName, BASE_ELEMENT);
        }
        OWLDataProperty dataProperty = factory.getOWLDataProperty(VALUE_DATA_PROPERTY, pm);
        baseElements.put(VALUE_DATA_PROPERTY, dataProperty);
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(dataProperty));
        addAnnotations(dataProperty.getIRI(), VALUE_DATA_PROPERTY_LABEL, BASE_ELEMENT);
    }



//======================================================================================================================
// CLASSES
//======================================================================================================================
    private void addClasses() {
        rs.classes.forEach((type, classes) -> {
            classes.forEach((elementName, elementClass) -> {
                String sDescription = String.format("%s %s converted to class %s", type, elementName, elementClass);
                addClass(elementClass, sDescription, type);
            });
        });
    }

    public OWLClass addClass(String className, String sDescription, String type) {
        String validClassName = validName(className);
        OWLClass sClass = factory.getOWLClass(validClassName, pm);
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(sClass));
        addAnnotations(sClass.getIRI(), className, sDescription);

        if(type != null)
            manager.applyChange(new AddAxiom(ontology,
                    factory.getOWLSubClassOfAxiom(sClass, (OWLClass) baseElements.get(type + CLASS_SUFFIX))
            ));
        String superClass = rs.getSuperClassOf(className);
        if(superClass != null) {
            IRI superclassURI = IRI.create(config.Out.POntologyBaseNS + superClass);
            manager.applyChange(new AddAxiom(ontology,
                    factory.getOWLSubClassOfAxiom(sClass, factory.getOWLClass(superclassURI))
            ));
        }

        return sClass;
    }

//======================================================================================================================
// OBJECT PROPERTIES
//======================================================================================================================
    private void addObjectProperties() {
        rs.objProperties.forEach((type, objProps) -> {
            objProps.getProperties().forEach((propName, domRan) ->
                    addObjectproperty(propName, domRan, type));
        });
    }

    public void addObjectproperty(String propName, DomRan domRan, String type) {

        OWLObjectProperty objproperty = factory.getOWLObjectProperty(validName(propName), pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(objproperty);

        //Add object property
        manager.addAxiom(ontology, declaration);
        addAnnotations(
                objproperty.getIRI(),
                domRan.getObjectPropertyRawLabel(),
                String.format("%s from %s", domRan.rule.toString(),
                domRan.extractedField));

        ////////////////////////////////////
        // add domain
        Set<OWLClassExpression> domainClasses = new HashSet<>();
        for (String className : domRan.domain)
            domainClasses.add(factory.getOWLClass(validName(className), pm));

        OWLClassExpression domainClass =
                domainClasses.size() > 1 ?
                factory.getOWLObjectUnionOf(domainClasses) :                           // domain is union
                factory.getOWLClass(domainClasses.iterator().next().toString(), pm);  // domain is a class

        OWLObjectPropertyDomainAxiom domainAxiom = factory.getOWLObjectPropertyDomainAxiom(objproperty, domainClass);
        manager.addAxiom(ontology, domainAxiom);

        ////////////////////////////////////
        // add range
        String validRangeClass = validName(domRan.range.iterator().next());
        OWLClass rangeClass= factory.getOWLClass(validRangeClass, pm);
        OWLObjectPropertyRangeAxiom rangeAxiom = factory.getOWLObjectPropertyRangeAxiom(objproperty, rangeClass);
        manager.addAxiom(ontology, rangeAxiom);

        // domain sbClassOf (property someValuesFrom range)
        OWLObjectSomeValuesFrom restriction = factory.getOWLObjectSomeValuesFrom(objproperty, rangeClass);
        for (OWLClassExpression domClass : domainClasses) {
            OWLAxiom axiom = factory.getOWLSubClassOfAxiom(domClass, restriction);
            manager.applyChange(new AddAxiom(ontology, axiom));
        }

        // inverse property
        if(config.Out.includeInverseAxioms) {
            OWLObjectProperty inverse = factory.getOWLObjectProperty(validName(domRan.getInverse()), pm);

            if(ontology.containsObjectPropertyInSignature(inverse.getIRI()))
                manager.applyChange(new AddAxiom(ontology,
                        factory.getOWLInverseObjectPropertiesAxiom(objproperty, inverse)
                ));
        }

        // super property
        manager.addAxiom(ontology,
                factory.getOWLSubObjectPropertyOfAxiom(objproperty, (OWLObjectProperty) baseElements.get(type+PROPERTY_SUFFIX))
        );

    }

//======================================================================================================================
// DATA PROPERTIES
//======================================================================================================================
    private void addDatatypeProperties() {
        rs.dataProperties.getProperties().forEach(this::addDatatype);
    }

    public void addDatatype(String propName, DomRan domRan) {
        String validPropName = validName(propName);
        OWLDataProperty datatypeProp = factory.getOWLDataProperty(validPropName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(datatypeProp);
        addAnnotations(datatypeProp.getIRI(), propName,
                        String.format("%s from %s", domRan.rule.toString(), domRan.extractedField));

        // Add datatypeProp
        manager.addAxiom(ontology, declaration);
        OWLDataPropertyExpression man = factory.getOWLDataProperty(validPropName, pm);

        Set<OWLClassExpression> domainClasses = new HashSet<>();
        for (String className : domRan.domain)
            domainClasses.add(factory.getOWLClass(validName(className), pm));
        
        OWLClassExpression domainClass =
                domainClasses.size() > 1 ?
                        factory.getOWLObjectUnionOf(domainClasses) :                            // domain is union
                        factory.getOWLClass(domainClasses.iterator().next().toString(), pm);   // domain is a class

        OWLDataPropertyDomainAxiom domainAxiom = factory.getOWLDataPropertyDomainAxiom(man, domainClass);
        manager.addAxiom(ontology, domainAxiom);


        OWLDatatype dt = factory.getOWLDatatype(domRan.range.iterator().next(), pm);
        OWLDataPropertyRangeAxiom rangeAxiom = factory.getOWLDataPropertyRangeAxiom(man, dt);
        manager.addAxiom(ontology, rangeAxiom);

        for (OWLClassExpression domClass : domainClasses) {
            OWLDataSomeValuesFrom restriction = factory.getOWLDataSomeValuesFrom(datatypeProp, dt);
            OWLAxiom axiom = factory.getOWLSubClassOfAxiom(domClass, restriction);
            manager.applyChange(new AddAxiom(ontology, axiom));
        }

        // super property
        manager.addAxiom(ontology,
                factory.getOWLSubDataPropertyOfAxiom(datatypeProp, (OWLDataProperty) baseElements.get(VALUE_DATA_PROPERTY))
        );
    }

//======================================================================================================================
// Annotations
//======================================================================================================================
    private void addAnnotations(IRI iri, String rawLabel, String description) {
        //Add labels -------------------------------------------------------------------------------
        getLabelSet(rawLabel, rs.getDatasetDictionary()).forEach(label -> {
            OWLAnnotation sLabel = factory.getOWLAnnotation(
                    factory.getOWLAnnotationProperty(label.annotationPropIRI()),
                    factory.getOWLLiteral(label.label()));

            manager.applyChange(new AddAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(iri, sLabel)));
        });

        //Add description ---------------------------------------------------------------------------
        OWLAnnotation sDescription = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()),
                factory.getOWLLiteral(description));

        manager.applyChange(new AddAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(iri, sDescription)));
    }



//======================================================================================================================
// SAVE TO TURTLE
//======================================================================================================================
    public void saveOntology(){

        File saveIn = new File(config.Out.POntology);
        IRI fileIRI = IRI.create(saveIn.toURI());
        TurtleDocumentFormat turtleFormat = new TurtleDocumentFormat();
        turtleFormat.setDefaultPrefix(ontology.getOntologyID().getOntologyIRI().get().getIRIString());
        try {
            manager.saveOntology(ontology, turtleFormat, fileIRI);
        } catch (OWLOntologyStorageException e) {
            throw new RuntimeException(e);
        }

    }


}


