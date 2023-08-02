package org.example.MappingGeneration;

import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
    private String ontologyFile;
    private boolean newElementsAdded = false;
    private HashSet<Property> annotationPropertiesIRIs = new HashSet<>();
    private HashMap<String, ArrayList<String>> cachedAnnotations;
    private boolean removePunct;

    public Ontology(String ontologyFile, ArrayList<String> annotationPropertiesIRIs, boolean removePunct) {
        this.ontologyFile = ontologyFile;
        loadOntology();
        findAnnotationProperties(annotationPropertiesIRIs);
        resetCached();
        this.removePunct = removePunct;
        System.out.println(this.annotationPropertiesIRIs);
    }


    private void loadOntology() {
        pModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        RDFDataMgr.read(pModel, ontologyFile);
        //pModel.listClasses().forEach(cl -> System.out.println(cl));
    }

    public String getBasePrefix() {
        return pModel.getNsPrefixURI("");
    }

    public static String swPrefixes() {
        return   "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                + "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
                + "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>" ;
    }

    public OntClass getOntClass(String uri) {
        return pModel.getOntClass(uri);
    }
    public OntClass getOntClass(URI uri) {
        return pModel.getOntClass(uri.toString());
    }
    public OntProperty getOntProperty(URI uri) {
        return pModel.getOntProperty(uri.toString());
    }
    public OntProperty getOntProperty(String uri) {
        return pModel.getOntProperty(uri);
    }
    public OntResource getOntResource(String uri) {return pModel.getOntResource(uri);}
    public OntResource getOntResource(URI uri) {return pModel.getOntResource(uri.toString());}


    public OntProperty createProperty(String propertyURI, String label, String description, int TYPE) {
        // Create the property with the given URI
        OntProperty newProperty;
        switch (TYPE) {
            case OBJPROPS:
                newProperty = pModel.createObjectProperty(propertyURI);
                break;
            case DATAPROPS:
                newProperty = pModel.createDatatypeProperty(propertyURI);
                break;
            default:
                throw new UnsupportedOperationException("Use OBJPROPS or DATAPROPS constants instead.");
        }
        addAnnotations(newProperty, label, description);
        return newProperty;
    }
    public OntClass createClass(String classURI, String label, String description) {
        OntClass newClass = pModel.createClass(classURI);
        addAnnotations(newClass, label, description);
        return newClass;
    }

    private void addAnnotations(Resource newResource, String label, String description) {
        // Add rdfs:label and rdfs:comment annotations to the property
        newResource.addProperty(ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"), label);
        newResource.addProperty(ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#comment"), description);

        newElementsAdded = true;
    }

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

    public Table runQuery(String queryString, String[] vars) {
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, pModel)) {
            ResultSet results = qexec.execSelect();

            // Create an empty Table with columns based on the variable names
            Table table = Table.create();
            for (String var : vars) {
                table.addColumns(StringColumn.create(var, new ArrayList<>()));
            }

            // Populate the Table with the query results
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();

                // Add each variable value to the corresponding column in the Table
                for (String var : vars) {
                    RDFNode node = soln.get(var);
                    if (node != null) {
                        table.stringColumn(var).append(node.toString());
                    } else {
                        // If the variable is not bound in the solution, add an empty cell
                        table.stringColumn(var).append("");
                    }
                }
            }
            return table;
        }
    }

    public void close() {
        // DON'T FORGET TO CALL TO SAVE CHANGES!
        if(newElementsAdded)
            saveOntology();
    }

    private void saveOntology() {
        OutputStream out = null;
        try {
            out = new FileOutputStream(ontologyFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        pModel.write(out, "TURTLE");
    }

}
