package org.example.createKG;

import com.google.gson.Gson;
import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.example.other.JSONFormatClasses;

import java.io.FileReader;
import java.net.URI;
import java.util.List;

public class JenaOntologyModelHandler {

    protected OntModel pModel;
    protected List<JSONFormatClasses.Table> tablesMaps;

    public JenaOntologyModelHandler(String ontologyFile) {
        loadPutativeOntology(ontologyFile);
        readMapJSON();
    }

    private void readMapJSON() {
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("EFS_mappings.json")) {
            // Convert JSON file to Java object
            tablesMaps = gson.fromJson(reader, JSONFormatClasses.class).getTables();
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


    protected String getNewPropertyURI(OntClass firstClass, String tableClassName) {
        String mBasePrefix = pModel.getNsPrefixURI("");
        String propertyNamePattern = firstClass.getNameSpace().equals(mBasePrefix) ?

                "%sp_%s_%s" :       //baseURI/p_tableClassName_firstclassName
                "%s%s_has_%s";      // baseURI/tableName_has_firstClassName

        return String.format(propertyNamePattern, mBasePrefix, tableClassName, firstClass.getLocalName());
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
