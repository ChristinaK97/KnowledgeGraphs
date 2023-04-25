package org.example.ontology_extractor;

import org.example.database_connector.DBSchema;
import org.example.ontology_extractor.Properties.DomRan;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.io.File;
import java.util.*;

public class OntologyExtractor {


    private DBSchema db;
    private String msBbasePrefix;
    private OWLOntologyManager oMan;
    private OWLOntology oOntology;
    private OWLDataFactory factory;
    private PrefixManager pm;
    private boolean turnAttributesToClasses = true;

    public OntologyExtractor(DBSchema db) {
        this.db = db;
        createOntology();
        applyRules();
        saveOntology(db.getSchemaName() + ".ttl");
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
        ArrayList<String> attrClasses = dpExtr.getAttrClasses();

        // add elements to the ontology
        addClasses(convertedIntoClass, attrClasses);
        addObjectProperties(objProperties, newObjProp);
        dataProperties.getProperties().forEach(this::addDatatype);

    }


    //===============================================================================================================
    // methods for creating ontology:
    public void createOntology(){
        msBbasePrefix = "http://www.example.net/ontologies/" + db.getSchemaName() + ".owl#";
        IRI ontologyIRI = IRI.create(msBbasePrefix);
        oMan = OWLManager.createOWLOntologyManager();
        factory = oMan.getOWLDataFactory();
        pm = new DefaultPrefixManager(msBbasePrefix);
        try {
            oOntology = oMan.createOntology(ontologyIRI);

        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }
    }

    // CLASSES
    private void addClasses(HashMap<String, String> convertedIntoClass, ArrayList<String> attrClasses) {
        convertedIntoClass.forEach((tableName, tableClass) -> {
            String sDescription = String.format("Table %s converted to class %s", tableName, tableClass);
            addClass(tableClass, sDescription);
        });
        for(String attrClass : attrClasses)
            addClass(attrClass, "Attribute class");
    }

    public void addClass(String className, String sDescription) {

        OWLClass sClass = factory.getOWLClass(className, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(sClass);

        //Add class
        oMan.addAxiom(oOntology, declaration);
        addDescriptions(sClass.getIRI(), className, sDescription);
    }


    // OBJECT PROPERTIES
    private void addObjectProperties(Properties objProp, Properties newObjProp) {
        objProp.getProperties().forEach(this::addObjectproperty);
        newObjProp.getProperties().forEach(this::addObjectproperty);
    }

    public void addObjectproperty(String propName, DomRan domRan) {

        OWLObjectProperty objproperty = factory.getOWLObjectProperty(propName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(objproperty);


        //Add object property
        oMan.addAxiom(oOntology, declaration);
        addDescriptions(objproperty.getIRI(), domRan.getObjectPropertyLabel(), domRan.rule.toString());

        ////////////////////////////////////
        // add domain
        OWLClass domainClass= factory.getOWLClass(domRan.domain.iterator().next(), pm);
        OWLObjectPropertyDomainAxiom domainAxiom = factory.getOWLObjectPropertyDomainAxiom(objproperty, domainClass);
        oMan.addAxiom(oOntology, domainAxiom);

        ////////////////////////////////////
        // add range
        OWLClass rangeClass= factory.getOWLClass(domRan.range.iterator().next(), pm);
        OWLObjectPropertyRangeAxiom rangeAxiom = factory.getOWLObjectPropertyRangeAxiom(objproperty, rangeClass);
        oMan.addAxiom(oOntology, rangeAxiom);

        // domain (property someValuesFrom range)
        OWLObjectSomeValuesFrom restriction = factory.getOWLObjectSomeValuesFrom(objproperty, rangeClass);
        OWLAxiom axiom = factory.getOWLSubClassOfAxiom(domainClass, restriction);
        oMan.applyChange(new AddAxiom(oOntology, axiom));

        // inverse property
        OWLObjectProperty inverse = factory.getOWLObjectProperty(domRan.getInverse(), pm);
        System.out.println(inverse.getIRI());
        if(oOntology.containsObjectPropertyInSignature(inverse.getIRI())) {
            OWLInverseObjectPropertiesAxiom inverseAxiom = factory.getOWLInverseObjectPropertiesAxiom(objproperty, inverse);
            oMan.applyChange(new AddAxiom(oOntology, inverseAxiom));
        }

    }


    // DATA PROPERTIES

    public void addDatatype(String propName, DomRan domRan) {

        OWLDataProperty datatype = factory.getOWLDataProperty(propName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(datatype);
        addDescriptions(datatype.getIRI(), propName, domRan.rule.toString());

        // Add datatype
        oMan.addAxiom(oOntology, declaration);
        OWLDataPropertyExpression man = factory.getOWLDataProperty(propName, pm);

        if(domRan.domain.size() > 1) {
            Set<OWLClassExpression> domainClasses = new HashSet<>();
            for (String className : domRan.domain)
                domainClasses.add(factory.getOWLClass(className, pm));

            OWLObjectUnionOf domainClass = factory.getOWLObjectUnionOf(domainClasses);
            OWLDataPropertyDomainAxiom domainAxiom = factory.getOWLDataPropertyDomainAxiom(man, domainClass);
            // Add domain
            oMan.addAxiom(oOntology, domainAxiom);
        }else{
            OWLClass domainClass= factory.getOWLClass(domRan.domain.iterator().next(), pm);

            OWLDataPropertyDomainAxiom domainAxiom=factory.getOWLDataPropertyDomainAxiom(man, domainClass);
            //Add domain
            oMan.addAxiom(oOntology, domainAxiom);
        }
        OWLDatatype dt = factory.getOWLDatatype(domRan.range.iterator().next(), pm);
        OWLDataPropertyRangeAxiom rangeAxiom = factory.getOWLDataPropertyRangeAxiom(man, dt);
        oMan.addAxiom(oOntology, rangeAxiom);

        for (String className : domRan.domain){
            OWLClass domainClass= factory.getOWLClass(className, pm);
            OWLDataSomeValuesFrom restriction = factory.getOWLDataSomeValuesFrom(datatype, dt);
            OWLAxiom axiom = factory.getOWLSubClassOfAxiom(domainClass, restriction);
            oMan.applyChange(new AddAxiom(oOntology, axiom));
        }
    }


    private void addDescriptions(IRI iri, String resourceName, String description) {
        //Add label
        String label = DomRan.normalise(resourceName);
        OWLAnnotation sLabel = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
                factory.getOWLLiteral(label));

        oMan.applyChange(new AddAxiom(oOntology, factory.getOWLAnnotationAssertionAxiom(iri, sLabel)));
        //Add description
        OWLAnnotation sDescription = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()),
                factory.getOWLLiteral(description));

        oMan.applyChange(new AddAxiom(oOntology, factory.getOWLAnnotationAssertionAxiom(iri, sDescription)));
    }


    // SAVE TO TURTLE
    public void saveOntology(String sPath){

        File saveIn = new File(sPath);
        IRI fileIRI = IRI.create(saveIn.toURI());
        TurtleDocumentFormat turtleFormat = new TurtleDocumentFormat();
        turtleFormat.setDefaultPrefix(oOntology.getOntologyID().getOntologyIRI().get().getIRIString() + "/");
        try {
            oMan.saveOntology(oOntology, turtleFormat, fileIRI);
        } catch (OWLOntologyStorageException e) {
            throw new RuntimeException(e);
        }

    }


    public static void main(String[] args) {
        new OntologyExtractor(new DBSchema());
    }

}




// Find all object properties that have range A and domain B
        /*Set<OWLObjectProperty> properties = oOntology.objectPropertiesInSignature().collect(
                Collectors.filtering(property -> {
                    Set<OWLClassExpression> domains = oOntology.getObjectPropertyDomainAxioms(property)
                            .stream()
                            .map(OWLObjectPropertyDomainAxiom::getDomain)
                            .collect(Collectors.toSet());
                    Set<OWLClassExpression> ranges = oOntology.getObjectPropertyRangeAxioms(property)
                            .stream()
                            .map(OWLObjectPropertyRangeAxiom::getRange)
                            .collect(Collectors.toSet());

                    return domains.contains(rangeClass) && ranges.contains(domainClass);
                }, Collectors.toSet()));

        // Define x as the inverse of each property found
        for (OWLObjectProperty y : properties) {
            OWLInverseObjectPropertiesAxiom inverseAxiom = factory.getOWLInverseObjectPropertiesAxiom(objproperty, y);
            oMan.applyChange(new AddAxiom(oOntology, inverseAxiom));
        }*/