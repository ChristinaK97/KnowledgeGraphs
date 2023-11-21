package org.example.E_CreateKG;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.example.MappingsFiles.ManageMappingsFile;
import org.example.MappingsFiles.MappingsFileTemplate.Mapping;
import org.example.MappingsFiles.MappingsFileTemplate.Table;
import org.example.util.Ontology;

import java.net.URI;
import java.util.HashMap;
import java.util.List;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.util.Annotations.TABLE_CLASS_URI;
import static org.example.util.Ontology.getLocalName;

public class JenaOntologyModelHandler {

    protected Ontology ontology;
    protected List<Table> tablesMaps;
    private HashMap<String, URI> cachedSpecializedClasses = new HashMap<>();

    public JenaOntologyModelHandler(Object ontology) {
        this.ontology = ontology instanceof Ontology ? (Ontology) ontology : new Ontology((String) ontology);
        tablesMaps = ManageMappingsFile.readMapJSON();
    }



    protected String getNewPropertyURI(String mBasePrefix, OntClass firstClass, String tableClassName) {
        /* tableClass -[p]-> firstClass */
        mBasePrefix = mBasePrefix == null ? ontology.getBasePrefix() : mBasePrefix;
       /* String propertyNamePattern = firstClass.getNameSpace().equals(mBasePrefix) ?

                "%sp_%s_%s" :       //baseURI/p_tableClassName_firstclassName
                "%s%s_has_%s";      // baseURI/tableName_has_firstClassName */

        return String.format("%sp_%s_%s", mBasePrefix, tableClassName, getLocalName(firstClass));
    }


    protected void specialisePathDOclasses (Mapping map) {
        // the map has no path attribute, no specialization is needed
        List<URI> nodes = map.getPathURIs();
        if(nodes == null)
            return;

        for(int i=0; i< nodes.size(); ++i) {
            OntClass nodeClass  = ontology.getOntClass(nodes.get(i));

            if(nodeClass != null) { // if node is a class (and not a property instead)
                String nodeURI = nodeClass.getURI();

                if(cachedSpecializedClasses.containsKey(nodeURI)) {                                                     //if(DEV_MODE) System.out.println("CACHED REPLACE " + nodes.get(i) + " WITH\n" + cachedSpecializedClasses.get(nodeURI));
                    nodes.set(i, cachedSpecializedClasses.get(nodeURI));
                    continue;
                }

                OntClass tableClass = ontology.getOntClass(TABLE_CLASS_URI);

                // replace the class in the path with the specialised PO TableClass subclass
                for (ExtendedIterator<OntClass> it = nodeClass.listSubClasses(); it.hasNext(); ) {
                    OntClass subClass = it.next();
                    if(subClass.hasSuperClass(tableClass)) {                                                            // if(DEV_MODE) System.out.println("REPLACE " + nodes.get(i) + " WITH\n" + subClass);
                        URI subClassURI = URI.create(subClass.getURI());
                        nodes.set(i, subClassURI);
                        cachedSpecializedClasses.put(nodeURI, subClassURI);
                        break;
                    }
                }
            }}
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

