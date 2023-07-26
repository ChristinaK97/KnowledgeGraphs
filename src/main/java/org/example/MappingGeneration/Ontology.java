package org.example.MappingGeneration;

import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

public class Ontology {

    public static final int CLASSES = 1;
    public static final int OBJPROPS = 2;
    public static final int DATAPROPS = 3;
    public static final int[] ONTELEMENTS = new int[]{CLASSES, OBJPROPS, DATAPROPS};

    private static final List<Property> predefinedAnnotations = Arrays.asList(
            RDFS.label
    );


    private OntModel pModel;
    private HashSet<Property> annotationPropertiesIRIs = new HashSet<>();
    private HashMap<String, ArrayList<String>> cachedAnnotations;
    private boolean removePunct;

    public Ontology(String ontologyFile, ArrayList<String> annotationPropertiesIRIs, boolean removePunct) {
        loadOntology(ontologyFile);
        findAnnotationProperties(annotationPropertiesIRIs);
        resetCached();
        this.removePunct = removePunct;
        System.out.println(this.annotationPropertiesIRIs);
    }


    private void loadOntology(String ontologyFile) {
        pModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        RDFDataMgr.read(pModel, ontologyFile);
        //pModel.listClasses().forEach(cl -> System.out.println(cl));
    }

    protected OntClass getOntClass(URI uri) {
        return pModel.getOntClass(uri.toString());
    }
    protected OntProperty getOntProperty(URI uri) {
        return pModel.getOntProperty(uri.toString());
    }
    protected OntProperty getOntProperty(String uri) {
        return pModel.getOntProperty(uri);
    }
    protected OntResource getOntResource(String uri) {return pModel.getOntResource(uri);}
    protected OntResource getOntResource(URI uri) {return pModel.getOntResource(uri.toString());}


    private void findAnnotationProperties(ArrayList<String> userDefinedAnnotProp) {
        // predefined
        for(Property annotProp : predefinedAnnotations)
            if (isUsed(annotProp))
                annotationPropertiesIRIs.add(annotProp);

        // user defined
        if(userDefinedAnnotProp != null)
            for(String annotProp : userDefinedAnnotProp) {
                Property annotP = getOntProperty(annotProp);
                if (!annotationPropertiesIRIs.contains(annotP) && isUsed(annotP))
                    annotationPropertiesIRIs.add(annotP);
            }

        // from ontology
        ExtendedIterator<? extends OntProperty> iter = pModel.listAnnotationProperties();
        iter.forEachRemaining(annotProp -> {
            if (!annotationPropertiesIRIs.contains(annotProp) && isUsed(annotProp))
                annotationPropertiesIRIs.add(annotProp);
        });

    }


    private boolean isUsed(String annotProp) {
        OntProperty annotP = getOntProperty(annotProp);
        return annotProp != null && isUsed(annotP);
    }
    private boolean isUsed(Property annotProp) {
        return pModel.contains(null, annotProp, (RDFNode) null);
    }


    public ExtendedIterator<? extends OntResource> listResources(int TYPE) {
        switch (TYPE) {
            case CLASSES:
                return pModel.listNamedClasses();
            case OBJPROPS:
                return pModel.listObjectProperties();
            case DATAPROPS:
                return pModel.listDatatypeProperties();
            default:
                return null;
        }
    }

    public ArrayList<String> getResourceAnnotations(String resourceURI) {
        return getResourceAnnotations(
                getOntResource(resourceURI)
        );
    }
    public ArrayList<String> getResourceAnnotations(URI resourceURI) {
        return getResourceAnnotations(
                getOntResource(resourceURI)
        );
    }

    public ArrayList<String> getResourceAnnotations(OntResource resource) {
        String resourceURI = resource.getURI();
        if(cachedAnnotations.containsKey(resourceURI))
            return cachedAnnotations.get(resourceURI);

        ArrayList<String> resourcesAnnotations = new ArrayList<>();
        for (Property property : annotationPropertiesIRIs) {
            StmtIterator stmtIterator = resource.listProperties(property);

            stmtIterator.forEachRemaining(stmt -> {
                RDFNode object = stmt.getObject();
                if (object.isLiteral())
                    resourcesAnnotations.add(removePunctuation(object.asLiteral().getString()));
            });
        }
        cachedAnnotations.put(resourceURI, resourcesAnnotations);
        return resourcesAnnotations;
    }

    public void resetCached() {
        cachedAnnotations = new HashMap<>();
    }

    private String removePunctuation(String annotation) {
        if(!removePunct)
            return annotation;

        Pattern pattern = Pattern.compile("\\p{Punct}");
        String result = pattern.matcher(annotation).replaceAll(" ");
        return result.trim();
    }


}
