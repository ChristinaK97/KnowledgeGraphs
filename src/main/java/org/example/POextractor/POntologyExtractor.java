package org.example.POextractor;

import org.example.InputPoint.InputDataSource;
import org.example.MappingsFiles.CreateMappingsFile;
import org.example.POextractor.Properties.DomRan;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.example.InputPoint.InputDataSource.POontology;
import static org.example.util.Annotations.*;

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

        new CreateMappingsFile().extractMappingsFile(dataSource, msBasePrefix, rs);
        //new JSONExtractor().createMappingJSON_forFKobjectProperties(db, msBbasePrefix, tableClasses, objProperties);

    }

    public void createOntology(){
        IRI ontologyIRI = IRI.create(msBasePrefix);
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        pm = new DefaultPrefixManager(ontologyIRI.getIRIString());
        try {
            ontology = manager.createOntology(ontologyIRI);

            // Import SKOS vocabulary for altLabel
            if(rs.isDson()) {
                OWLImportsDeclaration skosImport = factory.getOWLImportsDeclaration(skosIRI);
                manager.applyChange(new AddImport(ontology, skosImport));
            }

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
        baseElements.put("TableClass", addClass("TableClass", "Base Element", null));
        baseElements.put("AttributeClass", addClass("AttributeClass", "Base Element", null));

        for(String propName : new String[]{"PureProperty", "AttributeProperty"}) {
            OWLObjectProperty objproperty = factory.getOWLObjectProperty(propName, pm);
            baseElements.put(propName, objproperty);
            manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(objproperty));
            addAnnotations(objproperty.getIRI(), propName, "Base Element");
        }
        String baseDataProperty = "hasValueProperty";
        OWLDataProperty dataProperty = factory.getOWLDataProperty(baseDataProperty, pm);
        baseElements.put(baseDataProperty, dataProperty);
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(dataProperty));
        addAnnotations(dataProperty.getIRI(), "has value", "Base Element");
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
        String validClassName = validName(className);
        OWLClass sClass = factory.getOWLClass(validClassName, pm);
        manager.addAxiom(ontology, factory.getOWLDeclarationAxiom(sClass));
        addAnnotations(sClass.getIRI(), className, sDescription);

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
        if(includeInverseAxiom) {
            OWLObjectProperty inverse = factory.getOWLObjectProperty(validName(domRan.getInverse()), pm);

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
                factory.getOWLSubDataPropertyOfAxiom(datatypeProp, (OWLDataProperty) baseElements.get("hasValueProperty"))
        );
    }


    private void addAnnotations(IRI iri, String rawLabel, String description) {
        //Add labels =======================================================================

        getLabelSet(rawLabel, rs.getDatasetDictionary()).forEach(label -> {
            OWLAnnotation sLabel = factory.getOWLAnnotation(
                    factory.getOWLAnnotationProperty(label.annotationPropIRI()),
                    factory.getOWLLiteral(label.label()));

            manager.applyChange(new AddAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(iri, sLabel)));
        });

        //Add description =======================================================================

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
        InputDataSource dataSource = new InputDataSource();
        new POntologyExtractor(dataSource.getDataSource(), dataSource.getSchemaName());
    }

}


