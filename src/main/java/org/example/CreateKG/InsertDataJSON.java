package org.example.CreateKG;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.example.POextractor.JSON2OWL;

import java.util.*;

public class InsertDataJSON  extends InsertDataBase {
    private ArrayList<String> files;
    private String root;
    private long currRowID;
    private HashMap<String, Long> classCounter;

    public InsertDataJSON(String ontologyName, ArrayList<String> files) {
        super(ontologyName);
        this.files = files;
        classCounter = new HashMap<>();
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
            JsonElement json = JSON2OWL.readJSON(file);
            root = tablesClass.keySet().iterator().next();
            System.out.println(root);
            parseJson(root, null, null, json,
                    root.equals(JSON2OWL.ROOTCLASS) ? "/" + root : "");
        }
    }

    private ArrayList<OntResource> getColPath(String extractedField){
        System.out.println(extractedField);
        ArrayList<OntResource> cp = paths.get(root).get(extractedField);
        return cp != null ? cp : new ArrayList<>();
    }

    protected long getClassCounter(String className) {
        if(!classCounter.containsKey(className))
            classCounter.put(className, 1L);
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




    private void parseJson(String prev, Resource prevIndiv, String key, JsonElement value, String extractedField) {
        System.out.println();
        System.out.println("REC prev= " + prev + "\tkey= "+ key + (key!=null ? "\tvalue= "+ value:""));

        if(prev.equals(root) && key == null) { //new row
            prevIndiv = createIndiv(getTClass(root), true, root);
        }

        if(value.isJsonPrimitive() && isValid(prev, key, value, extractedField)) {
            System.out.println("prev= " + prev + "\tkey= " + key + "\tprimitive= " + value);
            createColPath(prevIndiv,
                    parseJSONvalue(value.getAsJsonPrimitive()),
                    getColPath(extractedField),
                    extractedField);
        }
        else if(value.isJsonObject())
            parseJsonObject(prev, prevIndiv, key, value.getAsJsonObject(), extractedField);
        else if(value.isJsonArray())
            parseJsonArray(prev, prevIndiv, key, value.getAsJsonArray(), extractedField);
    }


    private boolean isValid(String prev, String key, JsonElement value, String extractedField) {
        HashSet<String> invalid = new HashSet<>(Set.of("None", "null", "", " "));
        return !(prev == null || key == null || invalid.contains(value.getAsString()) ||
                invalid.contains(prev) || invalid.contains(key) ||
                (prev.equals(key) && extractedField.equals("/"+prev))
        );
    }


    private void parseJsonObject(String prev, Resource prevIndiv, String key, JsonObject valueObj, String extractedField) {
        System.out.println("JObject");

        if(key == null)
            key = prev;
        else {
            ArrayList<OntResource> cp = getColPath(extractedField);
            System.out.println(prevIndiv);
            System.out.println(cp);
            prevIndiv = createColPath(prevIndiv, null, cp, extractedField);
        }

        for(String nestedKey : valueObj.keySet()) {
            parseJson(key, prevIndiv, nestedKey, valueObj.get(nestedKey),
                    String.format("%s/%s", extractedField, nestedKey));
        }
    }


    private void parseJsonArray(String prev, Resource prevIndiv, String key, JsonArray valueArray, String extractedField) {
        System.out.println("JArray");
        boolean[] type = JSON2OWL.arrayType(valueArray);

        if(type[0] && type[1]){ //mixed
            //TODO
        }
        else {
            for(JsonElement arrayElement : valueArray)
                if(type[0])  //primitive
                    createColPath(prevIndiv, parseJSONvalue(arrayElement.getAsJsonPrimitive()),
                            getColPath(extractedField), extractedField);
                else  // non primitive
                    parseJson(prev, prevIndiv, key, arrayElement,
                            String.format("%s", extractedField));

        }

    }


    private Resource createColPath(Resource prevIndiv,
                               Object colValue,
                               ArrayList<OntResource> cp,
                               String comment) {
        /*
         *     i =       0       1   2
         * prevIndiv -[objP1]-> c1 -[dp]-> colValue
         * prevNode (i-1) -[i]-> nextNode (i+1)
         */
        Resource prevNode = prevIndiv;
        System.out.println(cp);
        for (int i = 0; i < cp.size() - 2; i+=2) {
            if(cp.get(i) == null)
                break;

            // create the next node entity for next node (i+1)
            Resource nextNode = createIndiv(cp.get(i+1).asClass(), cp.get(i+1).getLocalName().equals(root), comment);

            // connect the prevNode (i-1) with the next (i+1) using the objProp (i)
            prevNode.addProperty(cp.get(i).asProperty(), nextNode);
            prevNode = nextNode;
        }
        // set the data value to the last node using the data property in the final pos of the path (size-1)
        if(colValue != null)
            setDataPropertyValue(prevNode, cp.get(cp.size()-1).asProperty(), colValue);
        return prevNode;
    }



    private Object parseJSONvalue(JsonPrimitive value) {
        if (value == null)
            return null;
        try {
            return value.getAsInt();
        }catch (NumberFormatException e1) {
            try {
                return value.getAsFloat();
            }catch (NumberFormatException e2) {
                try {
                    return value.getAsDouble();
                }catch (NumberFormatException ignored) {
                    if(value.isBoolean())
                        return value.getAsBoolean();
                    else if (value.isString())
                        return value.getAsString();
                }
            }
        }
        return null;
    }


    public static void main(String[] args) {
        ArrayList<String> files = new ArrayList<>(Collections.singleton("src/main/resources/temp/PT1H.json"));
        new InsertDataJSON("json", files);
    }
}
