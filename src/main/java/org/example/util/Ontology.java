package org.example.util;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Ontology {

    public static final int CLASSES = 1;
    public static final int OBJPROPS = 2;
    public static final int DATAPROPS = 3;
    public static final int INDIVIDUALS = 4;
    public static final int[] ONTELEMENTS = new int[]{CLASSES, OBJPROPS, DATAPROPS};

    public OntModel pModel;
    private String ontologyFile;


    private HashSet<Property> annotationPropertiesIRIs = new HashSet<>();
    private static final List<Property> predefinedAnnotations = Arrays.asList(
            RDFS.label
    );
    private boolean removePunct;
    private HashMap<String, ArrayList<String>> cachedAnnotations;

    private boolean newElementsAdded = false;

// =================================================================================================================


    public Ontology(String ontologyFile) {
        this.ontologyFile = ontologyFile;
        loadOntology();
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

    // extract the local name of a uri
    public static String getLocalName(URI uri) {
        return getLocalName(uri.getPath());
    }
    public static String getLocalName(Resource resource) {
        return getLocalName(resource.getURI());
    }
    public static String getLocalName(String uri) {
        // Regular expression to match / or # followed by anything except / and #
        Matcher matcher = Pattern.compile("[/#]([^/#]+)$").matcher(uri);
        return matcher.find() ? matcher.group(1) : null;
    }

    // =================================================================================================================

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

    // =================================================================================================================

    public OntResource getInferedDomRan(String propURI, boolean searchDomain) {
        OntProperty prop = getOntProperty(propURI);
        return prop!=null ? getInferedDomRan(prop, searchDomain) : null;
    }

    public OntResource getInferedDomRan(OntProperty prop, boolean searchDomain) {
        OntResource domRan = null;
        LinkedList<OntProperty> superprops = new LinkedList<>();
        superprops.addLast(prop);                                                                                       //System.out.println("\nsearch " + (searchDomain?"domain":"range") + " for " + prop);

        while (superprops.size()>0) {
            prop = superprops.removeFirst();
            domRan = searchDomain? prop.getDomain() : prop.getRange();
            if(domRan != null)
                break;
            else{
                Set<? extends OntProperty> superpropsSet = prop.listSuperProperties().toSet();
                superpropsSet.remove(prop);
                superprops.addAll(superpropsSet);                                                                       //System.out.println(prop + " has super " + superprops);
            }
        }                                                                                                               //System.out.println("found " + domRan + "\n");
        return domRan;
    }
    // =================================================================================================================

    public OntClass getTopSuperclass(String subclassName) {
        try{
            return getTopSuperclass(getOntClass(getBasePrefix() + subclassName));
        }catch (NullPointerException e) {
            return null;
        }
    }

    /* Function to find the top superclass of a class */
    public OntClass getTopSuperclass(OntClass ontClass) {
        while (ontClass.hasSuperClass()) {
            for (OntClass operand : ontClass.listSuperClasses().toList())
                if (operand.isURIResource()) {
                    ontClass = operand.asClass();
                    break;
                }
        }
        return ontClass;
    }


    public HashMap<String,Integer> getAncestors(String ontResourceURI, boolean isSelfSuperclass) {
        OntResource ontResource = getOntResource(ontResourceURI);
        return ontResource==null ? new HashMap<>() : getAncestors(ontResource, isSelfSuperclass);
    }

    public HashMap<String,Integer> getAncestors(OntResource ontResource, boolean isSelfSuperclass) {
        HashMap<String, Integer> ancestors = new HashMap<>();
        if(isSelfSuperclass)
            ancestors.put(ontResource.toString(), 0);
        HashSet<String> visited = new HashSet<>();
        getAncestors(ontResource, ancestors, 1, visited);
        return ancestors;
    }

    private void getAncestors(OntResource ontResource, HashMap<String, Integer> ancestors, int depth, HashSet<String> visited) {
        if(!visited.contains(ontResource.toString())){
            visited.add(ontResource.toString());
            List<? extends OntResource> superResources = listSuperResources(ontResource);
            if(superResources.size()>0){
                for (OntResource operand : superResources) {
                    if (operand.isURIResource()) {
                        ancestors.put(
                                operand.toString(),
                                Math.min(ancestors.getOrDefault(operand.toString(), Integer.MAX_VALUE), depth)
                        );
                        getAncestors(operand, ancestors, depth+1, visited);
            }}}
        }
    }

    private List<? extends OntResource> listSuperResources(OntResource ontResource) {
        if(ontResource.canAs(OntClass.class))
            return ontResource.asClass().listSuperClasses().toList();
        else
            return ontResource.asProperty().listSuperProperties().toList();
    }


    public boolean areDisjoint(String resource1, String resource2) {
        OntResource r1 = getOntResource(resource1);
        OntResource r2 = getOntResource(resource2);
        boolean areDisjoint = getOntResource(resource1).isDifferentFrom(getOntResource(resource2));
        if(!areDisjoint && r1.canAs(OntClass.class))
            areDisjoint = r1.asClass().isDisjointWith(r2);
        return areDisjoint;
    }

    // =================================================================================================================

    public Resource getClassCopy(Resource cl) {
        if (cl.canAs(UnionClass.class)) {
            UnionClass unionClass = cl.as(UnionClass.class);
            ExtendedIterator<? extends OntClass> operands = unionClass.listOperands();
            RDFList members = pModel.createList(operands);
            operands.close();
            return pModel.createUnionClass(null, members);
        }
        return cl;
    }

    // =================================================================================================================

    public Resource createResource(String resourceIRI, Property labelProp, String label, String description) {
        newElementsAdded = true;
        Resource newResource = pModel.createResource(resourceIRI);
        addAnnotations(newResource, labelProp, label, description);
        return newResource;
    }

    public OntProperty createProperty(String propertyURI, String label, String description, int TYPE) {
        // Create the property with the given URI
        newElementsAdded = true;
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
        addAnnotations(newProperty,null, label, description);
        return newProperty;
    }
    public OntClass createClass(String classURI, String label, String description) {
        newElementsAdded = true;
        OntClass newClass = pModel.createClass(classURI);
        addAnnotations(newClass,null, label, description);
        return newClass;
    }

    private void addAnnotations(Resource newResource, Property labelProp, String label, String description) {
        // Add rdfs:label and rdfs:comment annotations to the property
        if(labelProp == null)
            labelProp = RDFS.label;
        if(!"".equals(label) && label != null)
            newResource.addLiteral(labelProp, label);
        if(!"".equals(description) && description != null)
            newResource.addLiteral(RDFS.comment, description);
    }


    // =================================================================================================================
    public void findAnnotationProperties(ArrayList<String> userDefinedAnnotProp, boolean removePunct) {

        this.removePunct = removePunct;

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

        System.out.println(this.annotationPropertiesIRIs);
        resetCached();
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
            case INDIVIDUALS:
                return pModel.listIndividuals();
            default:
                return null;
        }
    }

    public String getLabel(OntResource resource) {
        String label = resource.getLabel("en");
        if (label == null)
            label = resource.getLabel("");
        if(label == null)
            label = getLocalName(resource);
        return label;
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
        if(resourcesAnnotations.size() == 0)
            resourcesAnnotations.add(getLocalName(resource));
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
            for (String var : vars)
                table.addColumns(StringColumn.create(var, new ArrayList<>()));

            // Populate the Table with the query results
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();

                // Add each variable value to the corresponding column in the Table
                for (String var : vars) {
                    RDFNode node = soln.get(var);
                    table.stringColumn(var).append(node!=null ? node.toString() : "");   // If the variable is not bound in the solution, add an empty cell
                }
            }
            return table;
        }
    }

    public boolean hasDomRan(OntProperty property) {
        return property.getDomain() != null && property.getRange() != null;
    }

    public void saveChanges() {
        // DON'T FORGET TO CALL TO SAVE CHANGES!
        if(newElementsAdded) {
            OutputStream out = null;
            try {
                out = new FileOutputStream(ontologyFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            pModel.write(out, "TURTLE");
        }
    }



}
