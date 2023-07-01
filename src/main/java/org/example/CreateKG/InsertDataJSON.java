package org.example.CreateKG;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.example.other.JsonUtil;

import java.util.*;

public class InsertDataJSON  extends InsertDataBase {
    private ArrayList<String> files;
    private String root;
    private long currRowID;
    private HashMap<String, Long> classCounter;

    public InsertDataJSON(String ontologyName, ArrayList<String> files) {
        super(ontologyName);
        this.files = files;
        currRowID = 0;
        run();
    }

    @Override
    protected void addAdditionalPaths() {
        /*ExtendedIterator<? extends OntProperty> pureObjProp = pModel.getProperty(
                    Util.get_FK_PROPERTY_URI(ontologyName).toString())
                .as(ObjectProperty.class).listSubProperties();
        while (pureObjProp.hasNext()) {
            OntProperty prop = pureObjProp.next();

        }*/
    }

    @Override
    protected void mapData() {
        for (String file : files) {
            JsonElement json = JsonUtil.readJSON(file);
            findRoot();
            System.out.println(root);
            parseJson(root, null, null, json,
                    root.equals(JsonUtil.ROOTCLASS) ? root : ""
            );
        }
    }

    private void findRoot() {
        for(String tableField : paths.keySet()) {
            if (tableField.chars().filter(ch -> ch == '/').count() == 1) {
                root = tableField;
                return;
            }
        }
    }

// =====================================================================================================================

    private void parseJson(String prev, Resource prevIndiv, String key, JsonElement value, String extractedField) {
        System.out.println();
        System.out.println("REC prev= " + prev +  " prevInd= " + (prevIndiv!=null?prevIndiv.getLocalName():"")
                +"\tkey= "+ key + (key!=null ? "\tvalue= "+ value:""));

        if(prev.equals(root) && key == null) { //new row
            classCounter = new HashMap<>();
            prevIndiv = createIndiv(getTClass(root), true, root);
        }

        if(value.isJsonPrimitive() && JsonUtil.isValid(prev, key, value, extractedField)) {
            createColPath(prevIndiv,
                    JsonUtil.parseJSONvalue(value.getAsJsonPrimitive()),
                    getColPath(extractedField),
                    extractedField);
        }
        else if(value.isJsonObject())
            parseJsonObject(prev, prevIndiv, key, value.getAsJsonObject(), extractedField);
        else if(value.isJsonArray())
            parseJsonArray(prev, prevIndiv, key, value.getAsJsonArray(), extractedField);
    }


    private void parseJsonObject(String prev, Resource prevIndiv, String key, JsonObject valueObj, String extractedField) {
        System.out.println("JObject");

        if(key == null) {
            System.out.println("Null key");
            key = prev;
        } else {
            prevIndiv = createColPath(prevIndiv, null,
                    getColPath(extractedField), extractedField);
            System.out.printf("144 %s %s\n", extractedField, prevIndiv.getLocalName());
        }

        for(String nestedKey : valueObj.keySet()) {
            parseJson(key, prevIndiv, nestedKey, valueObj.get(nestedKey),
                    String.format("%s/%s", extractedField, nestedKey));
        }
    }


    private void parseJsonArray(String prev, Resource prevIndiv, String key, JsonArray valueArray, String extractedField) {
        System.out.println("JArray");
        boolean[] type = JsonUtil.arrayType(valueArray);

        if(type[0] && type[1]){ //mixed
            //TODO
        }
        else {
            if(type[0]) //primitive
                for(JsonElement arrayElement : valueArray)
                    createColPath(prevIndiv, JsonUtil.parseJSONvalue(arrayElement.getAsJsonPrimitive()),
                            getColPath(extractedField), extractedField);
            else // non primitive
                for(JsonElement arrayElement : valueArray)
                    parseJson(prev, prevIndiv, key, arrayElement,
                            String.format("%s", extractedField));
        }

    }

// =====================================================================================================================


    private ArrayList<OntResource> getColPath(String extractedField){
        String fieldTableClassJPath = extractedField.equals(root)? extractedField
                : extractedField.substring(0, extractedField.lastIndexOf("/"));

        ArrayList<OntResource> cp = paths.get(fieldTableClassJPath).get(extractedField);
        return cp != null ? cp : new ArrayList<>();
    }

    protected long getClassCounter(String className) {
        if(!classCounter.containsKey(className))
            classCounter.put(className, 0L);
        return classCounter.get(className);
    }


    protected Resource createIndiv(OntClass indivType, boolean isRoot, String comment) {
        String className = indivType.getLocalName();
        String indivURI = mBasePrefix + (isRoot ?
                className + currRowID :
                String.format("%s%d.%d", className, currRowID-1, getClassCounter(className)));

        Resource indiv = pModel.getOntResource(indivURI);
        if(indiv == null) {
            System.out.println("create " + indivURI);
            indiv = pModel.createResource(indivURI);
            indiv.addProperty(RDF.type, indivType);
            indiv.addLiteral(RDFS.comment, comment);

            if(isRoot)
                ++currRowID;
            else
                classCounter.replace(className, getClassCounter(className) + 1);
        }
        return indiv;
    }



    private Resource createColPath(Resource prevIndiv,
                               Object colValue,
                               ArrayList<OntResource> cp,
                               String comment) {
        /*
         *     i =       0       1     2
         * prevIndiv -[objP1]-> c1   -[dp]->          colValue
         *    prevNode (i-1)   -[i]-> nextNode (i+1)
         */
        System.out.println("Create col path " + comment + " " + cp);
        Resource prevNode = prevIndiv;
        int upperBound = colValue == null ? cp.size() : cp.size() - 2;
        for (int i = 0; i < upperBound; i+=2) {

            // create the next node entity for next node (i+1)
            Resource nextNode = createIndiv(cp.get(i+1).asClass(), cp.get(i+1).getLocalName().equals(root), comment);
            System.out.println(nextNode);

            // connect the prevNode (i-1) with the next (i+1) using the objProp (i)
            prevNode.addProperty(cp.get(i).asProperty(), nextNode);
            prevNode = nextNode;
        }
        // set the data value to the last node using the data property in the final pos of the path (size-1)
        if(colValue != null) {
            setDataPropertyValue(prevNode, cp.get(cp.size()-1).asProperty(), colValue);
        }
        return prevNode;
    }


    public static void main(String[] args) {
        ArrayList<String> files = new ArrayList<>(Collections.singleton("src/main/resources/temp/person.json"));
        new InsertDataJSON("json", files);
    }
}
