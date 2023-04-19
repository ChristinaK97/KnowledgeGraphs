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
    OWLOntologyManager oMan;
    OWLOntology oOntology;

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
        DataPropExtractor dpExtr = new DataPropExtractor(db,false, convertedIntoClass);
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
    // AutoMap4OBDA methods for creating ontology:
    public void createOntology(){
        msBbasePrefix = "http://www.example.net/ontologies/" + db.getSchemaName() + ".owl#";
        IRI ontologyIRI = IRI.create(msBbasePrefix);
        oMan = OWLManager.createOWLOntologyManager();
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

    public void addClass(String sName, String sDescription) {

        OWLDataFactory factory = oMan.getOWLDataFactory();
        PrefixManager pm = new DefaultPrefixManager(msBbasePrefix);

        OWLClass sClass = factory.getOWLClass(sName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(sClass);

        //Add class
        oMan.addAxiom(oOntology, declaration);

        //Add label
        OWLAnnotation sClassLabel = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()),
                factory.getOWLLiteral(sName));

        oMan.applyChange(new AddAxiom(oOntology, factory.getOWLAnnotationAssertionAxiom(sClass.getIRI(), sClassLabel)));
        //Add description
        OWLAnnotation sClassDescription = factory.getOWLAnnotation(
                factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI()),
                factory.getOWLLiteral(sDescription));

        oMan.applyChange(new AddAxiom(oOntology, factory.getOWLAnnotationAssertionAxiom(sClass.getIRI(), sClassDescription)));
    }



    // OBJECT PROPERTIES
    private void addObjectProperties(Properties objProp, Properties newObjProp) {
        objProp.getProperties().forEach(this::addObjectproperty);
        newObjProp.getProperties().forEach(this::addObjectproperty);
    }

    public void addObjectproperty(String propName, DomRan domRan) {
        OWLDataFactory factory = oMan.getOWLDataFactory();

        PrefixManager pm = new DefaultPrefixManager(msBbasePrefix);

        OWLObjectProperty objproperty = factory.getOWLObjectProperty(propName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(objproperty);

        //Add object property
        oMan.addAxiom(oOntology, declaration);

        ////////////////////////////////////
        // add domain
        OWLObjectPropertyDomainAxiom domainAxiom;
        if(domRan.domain.size() == 1) {
            OWLClass domainClass= factory.getOWLClass(domRan.domain.iterator().next(), pm);
            domainAxiom = factory.getOWLObjectPropertyDomainAxiom(objproperty, domainClass);
            //Add domain with global restrictions
            oMan.addAxiom(oOntology, domainAxiom);
            //Add domain with local restrictions
            oMan.addAxiom(oOntology, factory.getOWLSubClassOfAxiom(
                    factory.getOWLObjectSomeValuesFrom(objproperty, factory.getOWLThing()), domainClass));
        }else {
            Set<OWLClassExpression> domainClasses = new HashSet<>();
            for (String className : domRan.domain) {
                OWLClass owlClass = factory.getOWLClass(IRI.create(className));
                domainClasses.add(owlClass);
            }
            OWLObjectUnionOf domainClass = factory.getOWLObjectUnionOf(domainClasses);
            domainAxiom = factory.getOWLObjectPropertyDomainAxiom(objproperty, domainClass);
            //Add domain with global restrictions
            oMan.addAxiom(oOntology, domainAxiom);
            //Add domain with local restrictions
            oMan.addAxiom(oOntology, factory.getOWLSubClassOfAxiom(
                    factory.getOWLObjectSomeValuesFrom(objproperty, factory.getOWLThing()), domainClass));
        }




        ////////////////////////////////////
        // add range
        OWLObjectPropertyRangeAxiom rangeAxiom;
        if(domRan.range.size() == 1) {
            OWLClass rangeClass= factory.getOWLClass(domRan.range.iterator().next(), pm);
            rangeAxiom = factory.getOWLObjectPropertyRangeAxiom(objproperty, rangeClass);
            //Add range with global restrictions
            oMan.addAxiom(oOntology, rangeAxiom);
            //Add range with local restrictions
            oMan.addAxiom(oOntology, factory.getOWLSubClassOfAxiom(
                    factory.getOWLObjectSomeValuesFrom(objproperty, factory.getOWLThing()), rangeClass));
            factory.getOWLInverseFunctionalObjectPropertyAxiom(objproperty);
            oMan.addAxiom(oOntology, factory.getOWLSubClassOfAxiom(
                    factory.getOWLObjectSomeValuesFrom(objproperty.getInverseProperty(), factory.getOWLThing()), rangeClass));
        }else {
            Set<OWLClassExpression> rangeClasses = new HashSet<>();
            for (String className : domRan.range) {
                OWLClass owlClass = factory.getOWLClass(IRI.create(className));
                rangeClasses.add(owlClass);
            }
            OWLObjectUnionOf rangeClass = factory.getOWLObjectUnionOf(rangeClasses);
            rangeAxiom = factory.getOWLObjectPropertyRangeAxiom(objproperty, rangeClass);
            //Add range with global restrictions
            oMan.addAxiom(oOntology, rangeAxiom);
            //Add range with local restrictions
            oMan.addAxiom(oOntology, factory.getOWLSubClassOfAxiom(
                    factory.getOWLObjectSomeValuesFrom(objproperty, factory.getOWLThing()), rangeClass));
            factory.getOWLInverseFunctionalObjectPropertyAxiom(objproperty);
            oMan.addAxiom(oOntology, factory.getOWLSubClassOfAxiom(
                    factory.getOWLObjectSomeValuesFrom(objproperty.getInverseProperty(), factory.getOWLThing()), rangeClass));
        }

    }


    // DATA PROPERTIES

    public void addDatatype(String propName, DomRan domRan) {
        OWLDataFactory factory = oMan.getOWLDataFactory();
        PrefixManager pm = new DefaultPrefixManager(msBbasePrefix);

        OWLDataProperty datatype = factory.getOWLDataProperty(propName, pm);

        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(datatype);

        // Add datatype
        oMan.addAxiom(oOntology, declaration);
        OWLDataPropertyExpression man = factory.getOWLDataProperty(propName, pm);

        if(domRan.domain.size() > 1) {
            Set<OWLClassExpression> domainClasses = new HashSet<>();
            for (String className : domRan.domain) {
                OWLClass cls = factory.getOWLClass(className, pm);
                domainClasses.add(cls);
            }
            OWLObjectUnionOf domain = factory.getOWLObjectUnionOf(domainClasses);
            OWLDataPropertyDomainAxiom domainAxiom = factory.getOWLDataPropertyDomainAxiom(man, domain);
            // Add domain
            oMan.addAxiom(oOntology, domainAxiom);
        }else{
            OWLClass car= factory.getOWLClass(domRan.domain.iterator().next(), pm);

            OWLDataPropertyDomainAxiom domain=factory.getOWLDataPropertyDomainAxiom(man, car);
            //Add domain
            oMan.addAxiom(oOntology, domain);
        }

        String range = domRan.range.iterator().next();
        if (range.length() > 0) {
            OWLDatatype dt = factory.getOWLDatatype(range, pm);

            OWLDataPropertyRangeAxiom rangeAxiom = factory.getOWLDataPropertyRangeAxiom(man, dt);

            // Add range
            oMan.addAxiom(oOntology, rangeAxiom);
        }
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
