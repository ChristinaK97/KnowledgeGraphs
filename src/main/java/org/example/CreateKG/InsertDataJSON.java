package org.example.CreateKG;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.example.InputPoint.InputDataSource;
import org.example.util.HelperClasses.Pair;
import org.example.util.JsonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.util.Util.getLocalName;

public class InsertDataJSON  extends InsertDataBase {
    private ArrayList<String> files;
    private String root;
    private long currRowID;
    private HashMap<String, HashMap<String, Long>> classCounter;

    public InsertDataJSON(String ontologyName, ArrayList<String> files) {
        super(ontologyName);
        this.files = files;
        currRowID = 0;
        run();
    }

    @Override
    protected void addAdditionalPaths() {
        // no additional paths are needed for json
    }

    @Override
    protected void mapData() {
        findRoot();
        for (String file : files) {
            JsonElement json = JsonUtil.readJSON(file);
            System.out.println("Root " + root);
            parseJson(root, null, null, json,
                    root.equals("/" + JsonUtil.ROOTCLASS) ? root : ""
            );
        }
    }


    /** Root class is the table class whose JsonPath contains only one /
     *  e.g., /record when [{},{}] (outer element is an array
     *        /person when {person : {}} (outer dictionary has only one attribute)
     */
    private void findRoot() {
        for(String tableField : paths.keySet())
            if (tableField.chars().filter(ch -> ch == '/').count() == 1) {
                root = tableField;
                return;
            }
    }

// =====================================================================================================================

    /**
     * Read json file recursively and create the data graph.
     * @param prev The JPath of the previous node (e.g., /record)
     * @param prevIndiv The individual created from the previous node (e.g., record1)
     * @param key The current key attribute (e.g, time)
     * @param value The value of the current key attribute
     *              It can be a primitive, an array or a dictionary
     * @param extractedField The JPath of the current attribute (e.g., /record/time)
     */
    private void parseJson(String prev, Resource prevIndiv, String key, JsonElement value, String extractedField) {
        /* 1. A new record has been reached :
         *    2. Reset every class' id to 0 (except the root's)
         *    3. Create a new individual for the new record (e.g., record1 or person1)
         *
         * 4. If the value is a primitive (int, string etc.) and it is a valid value (not Null, "", etc):
         *    5. Create the path: (prevIndiv) -[key.path]-> primitive value
         *       (6. The key attribute's path)
         *
         * 7. Else, call the appropriate method to parse the jobject or jarray value
         *    (these functions contain the recursive call)
         */
        System.out.println();
        System.out.println("REC prev= " + prev +  "\tprevInd= " + (prevIndiv!=null? getLocalName(prevIndiv):null) + "\textr = " + extractedField
                +"\tkey= "+ key + (key!=null ? "\tvalue= "+ value:""));

        if(prev.equals(root) && key == null) {                                                                      //1
            classCounter = new HashMap<>();                                                                         //2
            setCounter(root);
            prevIndiv = createIndiv(getTClass(root), extractedField, prevIndiv, true, true);                                             //3
        }

        if(value.isJsonPrimitive() && JsonUtil.isValid(prev, key, value, extractedField)) {                         //4
            createColPath(prevIndiv, extractedField,                                                                               //5
                    JsonUtil.parseJSONvalue(value.getAsJsonPrimitive()));
        }
        else if(value.isJsonObject())                                                                               //7
            parseJsonObject(prev, prevIndiv, key, value.getAsJsonObject(), extractedField);
        else if(value.isJsonArray())
            parseJsonArray(prev, prevIndiv, key, value.getAsJsonArray(), extractedField);
    }



    private void parseJsonObject(String prev, Resource prevIndiv, String key, JsonObject valueObj, String extractedField) {
        /* 1. The key is null when prev is the root, and the value is the outer brackets of the json file
         *    i.e., value = json file contents = {...}
         *    A new node wasn't reached so stay in the root
         *
         * 2. Else, the new node (key) reached is a nested attribute of prev
         *    e.g., prev=person : { key=address : {value} } , where person is an existing attribute
         *          prev=record : { key=properties : {value} } , where record is an added central element
         *
         *    3. Create the path for the key attribute
         *       prevIndiv (method arg) -[path]-> new individual for key
         *       e.g., record1 -[p_record_properties]-> properties1.0            cp=[p_record_properties, properties]
         *             person1 -[p1, c1, p2, c2, p_person_address]-> address1.0  cp=[p1, c1, p2, c2, p_person_address, address]
         *       The colValue is set to null since the created path doesn't end with a data property,
         *       but instead defines a pure object property.
         *
         *       The prevIndiv is changed to the new individual created for key, to connect its nested elements to it.
         *
         *  4. For each nestedKey of the current key:
         *     5. Parse the nested element
         *     e.g., key = properties value = {p1:v1, p2:v2}  nestedKey=[p1,p2]
         *           call(key= properties, pI= properties1, nestedKey= p1, value= v1, eF= "/record/properties/p1 and same for p2
         */
        System.out.println("JObject :");

        if(key == null) {                                                                                            //1
            System.out.println("Null key");
            key = prev;
        } else {                                                                                                     //2
            System.out.printf("\t%s %s\n", extractedField, getLocalName(prevIndiv));
            setCounter(extractedField);
            prevIndiv = createColPath(prevIndiv, extractedField, null);                                                     //3

        }

        for(String nestedKey : valueObj.keySet()) {                                                                  //4
            parseJson(key, prevIndiv, nestedKey, valueObj.get(nestedKey),                                            //5
                    String.format("%s/%s", extractedField, nestedKey));
        }
    }


    private void parseJsonArray(String prev, Resource prevIndiv, String key, JsonArray valueArray, String extractedField) {
        /* 1. Determine the type of elements the array contains
         * 2. A mixed array contains both primitive and non primitive elements (e.g., ["John", "address":{}] TODO
         *
         * 3. Else if the array only contains primitive values, for each such value (arrayElement):
         *    Create the path prevIndiv -[key path]-> arrayElement
         *    e.g., prev=person key=language valueArray=["Eng", "Span"]
         *          person1 -[p_person_language]-> language1.1 -[has_language_value]-> "EN", and same for "Span"
         *          The colValue is set to the arrayElement to determine a path that concludes in a data property
         *
         * 4. Else if the array contains non-primitive values, recursively examine each value of the array
         *    e.g., prev= person, key=friends, value=[{name:Joe, age:21}, {name:Bob, age:20}]
         *          person1 -[p_person_friends]-> friends1 -[name]-> Joe
         *                                                 -[age]-> 21
         *                  -[p_person_friends]-> friends1 -[name]-> Bob
         *                                                 -[age]-> 20
         */
        System.out.println("JArray");
        boolean[] type = JsonUtil.arrayType(valueArray);   //1

        if(type[0] && type[1]){  //2
            //TODO handle mixed arrays (primitive values and json elements)
        }
        else if(type[0])        //3
            for(JsonElement arrayElement : valueArray)
                createColPath(prevIndiv, extractedField, JsonUtil.parseJSONvalue(arrayElement.getAsJsonPrimitive()));
        else  //4
            for(JsonElement arrayElement : valueArray)
                parseJson(prev, prevIndiv, key, arrayElement,
                          String.format("%s", extractedField));
    }

// =====================================================================================================================


    /**
     * Return the path of an attribute
     * @param extractedField The JSONPath of the attribute (simplified)
     *      extractedField = "/record/properties/p1"
     *      fieldTableClassJPath = "/record/properties" : The table class to whom the p1 is nested
     * @return a list of the resources (properties and classes) that define the extractedField's path
     *         if no such path was defined, return an empty list
     */
    private ArrayList<Pair<OntResource,Boolean>> getColPath(String extractedField) {
        String tableClassPath = extractedField.equals(root)? extractedField
                : extractedField.substring(0, extractedField.lastIndexOf("/"));

        System.out.println("Get Path for " + extractedField + " : " + tableClassPath);

        ArrayList<Pair<OntResource,Boolean>> cp = paths.get(tableClassPath).get(extractedField);
        return cp != null ? cp : new ArrayList<>();
    }

    /**
     * Get the id of the next individual that will be created for this class
     * @param className The local name of a OntClass object
     * @return The number of individuals belonging to this class for the current record.
     * When reaching a new record (i.e., the outermost attribute of a json file) reset the counters
     * for the classes of nested elements.
     * person : person0
     *          language : language0.0, language0.1
     * person : person1
     *          language : language1.0, language1.1
     */
    protected long getClassCounter(String tableClassPath, String className) {
        System.out.println("Get counter " + tableClassPath + " " + className);
        System.out.println(classCounter);
        if(!classCounter.get(tableClassPath).containsKey(className))
            classCounter.get(tableClassPath).put(className, 0L);
        return classCounter.get(tableClassPath).get(className);
    }

    private void setCounter(String extractedField) {
        System.out.println("Reset counter for " + extractedField);
        if(!classCounter.containsKey(extractedField))
            classCounter.put(extractedField, new HashMap<>());
    }

    /**
     * Create an individual if it doesn't exist
     * @param indivType : The class of the individual (rdf:type)
     * @param isRoot : Is the new root individual created? (e.g., a new record or a new person)
     * @return The resource that was created (or existed with this URI in the pModel)
     */
    protected Resource createIndiv(OntClass indivType,
                                   String extractedField,
                                   Resource prevIndiv,
                                   boolean isRoot,
                                   boolean createNewIndiv
    ){
        /* 1. Create the URI of the individual. Its id will be the prefix + the name of its class + its id
         *    - The id for a root individual is a single incremental integer (currRowID)
         *    - The id for an element that is nested/connected to the root individual is
         *      the id of the root individual . an incremental integer for the new individual's class
         *
         *      person : person0
         *               language : language0.0
         *
         *    When a new root individual is successfully created, the currRowId is increased by 1.
         *    (the incremented value will be the id of the next record)
         *    To connect the new nested individual with the correct/current root individual : currRowID-1 instead of simply currRowID
         *
         * 2. If the individual with the generated URI didn't already exist, create it.
         *    Increment the appropriate counter for the next new individual of the same class
         *    (and same record if not isRoot)
         */

        String className, tableClassPath="", indivURI;
        long classCounterValue=-1;

        if(isRoot) {
            className = root.substring(1);
            indivURI = String.format("%s%s%d", mBasePrefix, className, currRowID);
        }else {
            className = getLocalName(indivType);
            tableClassPath = extractedField.substring(0, extractedField.lastIndexOf("/"));
            boolean isTableClassWithPath = tablesClass.containsKey(extractedField) && tablesClass.get(extractedField).hasPath();
            if(isTableClassWithPath) {
                Matcher match = Pattern.compile("\\d+$").matcher(prevIndiv.toString());
                match.find();
                String prevIndivId = match.group();
                indivURI = String.format("%s_%s%s", prevIndiv, className, prevIndivId);
            }else{
                classCounterValue = getClassCounter(tableClassPath, className) ;
                indivURI = String.format("%s_%s%d", prevIndiv, className, classCounterValue - (createNewIndiv ? 0 : 1));
            }
        }
        System.out.println("Try Indiv = "+ indivURI+ " create new ? " + createNewIndiv);
        System.out.println(classCounter);

        Resource indiv = pModel.getOntResource(indivURI);
        assert !(indiv == null && !createNewIndiv);
        if(indiv == null) {     //2
            System.out.println("\tcreate " + indivURI);
            indiv = pModel.createResource(indivURI);
            indiv.addProperty(RDF.type, indivType);
            indiv.addLiteral(RDFS.comment, extractedField);

            if(isRoot)
                ++currRowID;
            else
                classCounter.get(tableClassPath).replace(className, classCounterValue + 1);
        }
        else{
            System.out.println("\tindiv exists " + indivURI);
        }

        /*long classCounterValue = -1; // unused value
        String className, tableClassPath=null, indivURI;
        boolean isElementOnTablePath = false;
        boolean isTableClassWithPath = false;

        if(isRoot) {
            className = root.substring(1);
            indivURI = String.format("%s%s%d", mBasePrefix, className, currRowID);
        }else {
            className = getLocalName(indivType);
            tableClassPath = extractedField.substring(0,extractedField.lastIndexOf("/"));

            if(tablesClass.containsKey(extractedField))
                isTableClassWithPath = tablesClass.get(extractedField).hasPath();
            if(!isTableClassWithPath)
                isTableClassWithPath = tablesClass.containsKey(tableClassPath) && tablesClass.get(tableClassPath).hasPath();

            System.out.println("isTableClassWithPath = " + isTableClassWithPath);

            if(isTableClassWithPath) {
                // sq table individual
                if(paths.get(tableClassPath).containsKey(extractedField)) {
                    Matcher match = Pattern.compile("\\d+$").matcher(prevIndiv.toString());
                    match.find();
                    String prevIndivId = match.group();
                    indivURI = String.format("%s_%s%s", prevIndiv, className, prevIndivId);
                }else{ //sq item individual
                    isElementOnTablePath = true;
                    classCounterValue = getClassCounter(tableClassPath, className);
                    indivURI = String.format("%s_%s%d", prevIndiv, className, classCounterValue);
                }
            }else {
                classCounterValue = getClassCounter(tableClassPath, className);
                indivURI = String.format("%s_%s%d", prevIndiv, className, classCounterValue);
            }
        }

        Resource indiv = pModel.getOntResource(indivURI);
        if(indiv == null) {     //2
            System.out.println("create " + indivURI);
            indiv = pModel.createResource(indivURI);
            indiv.addProperty(RDF.type, indivType);
            indiv.addLiteral(RDFS.comment, comment);

            if(isRoot)
                ++currRowID;
            else if(!isElementOnTablePath && !isTableClassWithPath)
                classCounter.get(tableClassPath).replace(className, classCounterValue + 1);
        }
        else{
            System.out.println("indiv exists " + indivURI);
        }*/
        return indiv;
    }



    /**
     * @param prevIndiv The individual that will be the first node of the path (the 1st subject)
     * cp : A path of properties and classes that express the column/attribute
     * @param colValue
     *          - The primitive value of a json attribute e.g., year:2005
     *            when the attribute has a path that concludes with a data property (:has_year_VALUE)
     *          - null when the attribute expresses a pure object property
     *            In this case, the cp concludes with this PO object property and its class
     *            [objP1, C1, ...., Cn, p_record_year, Year]
     * @return The new individual created for the final class in the path
     *         - If primitive-valued attribute, this resource can be ignored
     *         - If attribute is a pure object property, this resource might be need to connect its
     *           nested elements with it.
     * colValue == null
     *     i =       0       1     2
     * prevIndiv -[objP1]-> c1   -[dp]->          colValue
     *    prevNode (i-1)   -[i]-> nextNode (i+1)
     */
    private Resource createColPath(Resource prevIndiv,
                               String extractedField,
                               Object colValue) {


        /* 1. The path starts with the input/argument individual
         * 2. Upper bound to iterate the path:
         *                             i=0     i+1=1   i=2     i+1=3    i+1   i=size-2       i+1=size-1
         *    - if pure obj prop, cp= [objP1,  C1,     objP2,  C2, ..., Cn,   p_record_year, Year]
         *      prevNode -[objP1]-> C1 -[objP2]-> C2 ... Cn -[p_record_year]-> Year
         *
         *      i=size-2 is a pure object property and size-1 is a table class
         *
         *                                     i=0     i+1=1   i=2     i+1=3    i+1     i=size-2  i+1=size-1
         *    - if primitive-valued attr, cp= [objP1,  C1,     objP2,  C2, ..., POobjP, POc,      POdp]
         *      prevNode -[objP1]-> C1 -[objP2]-> C2 ... -[POobjP]-> POc -[POdp]-> colValue
         *
         * Iterate the path
         * 3. Create the next individual for next node (i+1)
         *    (The classes have odd number i)
         * 4. Connect the prevNode (i-1) with the next (i+1) using the objProp (i)
         *    (The object properties have even i)
         *
         * 5. set the data value to the last node using the data property in the final pos of the path (size-1)
         *    prevNode (size-2) -[dp (size-1)]-> colValue
         */
        ArrayList<Pair<OntResource,Boolean>> cp = getColPath(extractedField);
        System.out.println("Create col path " + extractedField + " " + cp);
        Resource prevNode = prevIndiv;                                          //1
        int upperBound = colValue == null ? cp.size() : cp.size() - 2;          //2
        for (int i = 0; i < upperBound; i+=2) {

            // 3
            Resource nextNode = createIndiv(cp.get(i+1).pathElement().asClass(),
                                            extractedField, prevIndiv,
                                            getLocalName(cp.get(i+1).pathElement()).equals(root),
                                            cp.get(i+1).createNewIndiv());
            // 4
            prevNode.addProperty(cp.get(i).pathElement().asProperty(), nextNode);
            prevNode = nextNode;
        }
        if(colValue != null)  //5
            setDataPropertyValue(prevNode, cp.get(cp.size()-1).pathElement().asProperty(), colValue);

        return prevNode;
    }


    public static void main(String[] args) {
        ArrayList<String> files = new ArrayList<>(Collections.singleton("src/main/resources/json/PT1H.json"));
        System.out.println(files);
        new InsertDataJSON(InputDataSource.ontologyName, files);
    }
}
