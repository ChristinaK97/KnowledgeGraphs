package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.ontology_extractor.Properties.DomRan;
import org.example.other.JSONExtractor;
import org.example.other.Util;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.example.other.Util.POontology;

public class OntologyExtractor {

    private DBSchema db;

    private String msBbasePrefix;
    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;
    private PrefixManager pm;
    private final boolean turnAttributesToClasses = true;
    private final boolean includeInverseAxiom = false;

    private HashMap<String, OWLObject> baseElements = new HashMap<>(4);

    public OntologyExtractor(DBSchema db) {
        this.db = db;
        createOntology();
        applyRules();
        saveOntology(POontology);
    }

    private void applyRules() {
        // table classes
        HashMap<String, String> convertedIntoClass = new ClassExtractor(db).getConvertedIntoClass();
        // object properties connecting table classes
        Properties objProperties = new ObjectPropExtractor(db, convertedIntoClass).getObjProperties();

        System.out.println(objProperties);
        DataPropExtractor dpExtr = new DataPropExtractor(db,turnAttributesToClasses, convertedIntoClass);
        // data properties
        Properties dataProperties = dpExtr.getDataProp();
        // object properties connecting table classes with attribute classes. if !turnAttrToClasses :empty
        Properties newObjProp = dpExtr.getNewObjProp();


        // attribute classes
        HashMap<String, String> attrClasses = dpExtr.getAttrClasses();

        // add elements to the ontology
        createBaseElements();
        addClasses(convertedIntoClass, "Table");
        addClasses(attrClasses, "Attribute");
        addObjectProperties(objProperties, newObjProp);
        dataProperties.getProperties().forEach(this::addDatatype);

        //new JSONExtractor().createMappingJSON_fromOntology(db, msBbasePrefix,
        //        convertedIntoClass, attrClasses, objProperties, newObjProp, dataProperties);

        //new JSONExtractor().createMappingJSON_forFKobjectProperties(db, msBbasePrefix, convertedIntoClass, objProperties);
    }


    //===============================================================================================================
    // methods for creating ontology:
    public void createOntology(){
        msBbasePrefix = "http://www.example.net/ontologies/" + db.getSchemaName() + ".owl/";
        IRI ontologyIRI = IRI.create(msBbasePrefix);
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        pm = new DefaultPrefixManager(ontologyIRI.getIRIString());
        try {
            ontology = manager.createOntology(ontologyIRI);

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
    private void addClasses(HashMap<String, String> classes, String type) {
        classes.forEach((elementName, elementClass) -> {
            String sDescription = String.format("%s %s converted to class %s", type, elementName, elementClass);
            addClass(elementClass, sDescription, type);
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
    private void addObjectProperties(Properties objProp, Properties newObjProp) {
        objProp.getProperties().forEach((propName, domRan) -> addObjectproperty(propName, domRan, "FK"));
        newObjProp.getProperties().forEach((propName, domRan) -> addObjectproperty(propName, domRan, "Attribute"));
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
        new OntologyExtractor(new DBSchema());
    }

}


