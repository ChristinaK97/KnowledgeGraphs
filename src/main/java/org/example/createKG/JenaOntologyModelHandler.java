package org.example.createKG;

import com.google.gson.Gson;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.example.other.JSONMappingTableConfig;

import java.io.FileReader;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.example.other.Util.EFS_mappings;
import static org.example.other.Util.TABLE_CLASS_URI;

public class JenaOntologyModelHandler {

    protected OntModel pModel;
    protected List<JSONMappingTableConfig.Table> tablesMaps;
    private HashMap<String, URI> cachedSpecializedClasses = new HashMap<>();

    public JenaOntologyModelHandler(String ontologyFile) {
        loadPutativeOntology(ontologyFile);
        readMapJSON();
    }

    private void readMapJSON() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(EFS_mappings)) {
            // Convert JSON file to Java object
            tablesMaps = gson.fromJson(reader, JSONMappingTableConfig.class).getTables();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadPutativeOntology(String ontologyFile) {
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
    protected OntResource getOntResource(URI uri) {return pModel.getOntResource(uri.toString());}

    protected URI getFirstNodeFromPath(List<URI> path) {
        return path.get(0);
    }
    protected URI getLastNodeFromPath(List<URI> path) {
        return path.get(path.size() - 1);
    }


    protected String getNewPropertyURI(String mBasePrefix, OntClass firstClass, String tableClassName) {
        /* tableClass -[p]-> firstClass */
        mBasePrefix = mBasePrefix == null ? pModel.getNsPrefixURI("") : mBasePrefix;
        String propertyNamePattern = firstClass.getNameSpace().equals(mBasePrefix) ?

                "%sp_%s_%s" :       //baseURI/p_tableClassName_firstclassName
                "%s%s_has_%s";      // baseURI/tableName_has_firstClassName

        return String.format(propertyNamePattern, mBasePrefix, tableClassName, firstClass.getLocalName());
    }


    protected void specialisePathDOclasses (JSONMappingTableConfig.Mapping map) {
        // the map has no path attribute, no specialization is needed
        List<URI> nodes = map.getPathURIs();
        if(nodes == null)
            return;

        for(int i=0; i< nodes.size(); ++i) {
            OntClass nodeClass  = getOntClass(nodes.get(i));

            if(nodeClass != null) { // if node is a class (and not a property instead)
                String nodeURI = nodeClass.getURI();

                if(cachedSpecializedClasses.containsKey(nodeURI)) {
                    //System.out.println("CACHED REPLACE " + nodes.get(i) + " WITH\n" + cachedSpecializedClasses.get(nodeURI));
                    nodes.set(i, cachedSpecializedClasses.get(nodeURI));
                    continue;
                }

                OntClass tableClass = getOntClass(TABLE_CLASS_URI);

                // replace the class in the path with the specialised PO TableClass subclass
                for (ExtendedIterator<OntClass> it = nodeClass.listSubClasses(); it.hasNext(); ) {
                    OntClass subClass = it.next();
                    if(subClass.hasSuperClass(tableClass)) {
                        //System.out.println("REPLACE " + nodes.get(i) + " WITH\n" + subClass);
                        URI subClassURI = URI.create(subClass.getURI());
                        nodes.set(i, subClassURI);
                        cachedSpecializedClasses.put(nodeURI, subClassURI);
                        break;
                    }
                }
            }}
    }


    protected String getLabel(OntResource resource) {
        String label = resource.getLabel("en");
        if (label == null)
            label = resource.getLabel("");
        if(label == null)
            label = resource.getLocalName();
        return label;
    }

}



    /*public static HashSet<String> XSDDateTypes = dateTypesSet();
    private static HashSet<String> dateTypesSet() {
        return new HashSet<>(
                List.of(new String[]{XSDDatatype.XSDdate.getURI(),
                                     XSDDatatype.XSDdateTime.getURI(),
                                     XSDDatatype.XSDtime.getURI()})
        );
    }

    public boolean isDateType (Resource range) {
        return XSDDateTypes.contains(range.getURI());
    }*/

