package org.example.E_CreateKG;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import static org.example.A_Coordinator.Runner.config;
import org.example.util.JsonUtil;
import org.example.util.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.util.Ontology.getLocalName;

public class InsertDataJSON  extends InsertDataBase {
    private ArrayList<String> files;
    private String root;

    // <table JPath, <column JPath + each className from column paths, next id for column indiv>>
    private HashMap<String, HashMap<String, Long>> classCounter;

    /* key   = the JPath-like name/label of the individual e.g. record0_properties0
       value = a big integer which is the local name of the individual's uri
       .get(key) -> to get the big integer value as string
       .getKey(value) -> to get the string label
    */
    BidiMap<String, String> indivNames;
    private long currRowID;
    private BigInteger indivCounter;
    private String currentFileName;

    public InsertDataJSON(ArrayList<String> files) {
        super();
        this.files = files;
        //TODO Read initial values from a log file (last saved indiv ids). Now it is reset by run/session
        currRowID = 0;
        indivCounter = BigInteger.ZERO;
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
            currentFileName = file.substring(file.lastIndexOf("/")+1, file.lastIndexOf("."));
            indivNames = new DualHashBidiMap<>(); // reset per file
            JsonElement json = JsonUtil.readJSON(file);                                                                 System.out.println("Root " + root);
            parseJson(root, null, null, json,
                    root.equals("/" + config.Out.DefaultRootClassName) ? root : ""
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
                                                                                                                        System.out.println("\nREC prev= " + prev +  "\tprevInd= " + (prevIndiv!=null? indivNames.getKey(getLocalName(prevIndiv)):null) + "\textr = " + extractedField +"\tkey= "+ key + (key!=null ? "\tvalue= "+ value:""));
        if(prev.equals(root) && key == null) {                                                                      //1
            classCounter = new HashMap<>();                                                                         //2
            setCounter(root);
            prevIndiv = createIndiv(getTClass(root), extractedField, prevIndiv, true, true);    //3
            prevIndiv.addLiteral(sourceFileAnnotProp, currentFileName);
        }

        if(value.isJsonPrimitive() && JsonUtil.isValid(prev, key, value, extractedField)) {                         //4
            createColPath(prevIndiv, extractedField,                                                                //5
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
         *    e.g., prev=person.json : { key=address : {value} } , where person.json is an existing attribute
         *          prev=record : { key=properties : {value} } , where record is an added central element
         *
         *    3. Create the path for the key attribute
         *       prevIndiv (method arg) -[path]-> new individual for key
         *       e.g., record1 -[p_record_properties]-> record1_properties0            cp=[p_record_properties, properties]
         *             person1 -[p1, c1, p2, c2, p_person_address]-> person1_address0  cp=[p1, c1, p2, c2, p_person_address, address]
         *       The colValue is set to null since the created path doesn't end with a data property,
         *       but instead defines a pure object property.
         *
         *       The prevIndiv is changed to the new individual created for key, to connect its nested elements to it.
         *
         *  4. For each nestedKey of the current key:
         *     5. Parse the nested element
         *     e.g., key = properties value = {p1:v1, p2:v2}  nestedKey=[p1,p2]
         *           call(key= properties, pI= record1_properties0, nestedKey= p1, value= v1, eF= "/record/properties/p1 and same for p2
         */
                                                                                                                        System.out.println("JObject :");
        if(key == null) {    /*1*/
            key = prev;                                                                                                 System.out.println("Null key");
        } else {             /*2*/                                                                                      System.out.printf("\tExtr Field = %s Prev Indiv Name = %s\n", extractedField, indivNames.getKey(getLocalName(prevIndiv)));
            setCounter(extractedField);
            prevIndiv = createColPath(prevIndiv, extractedField, null);   //3
        }
        for(String nestedKey : valueObj.keySet()) {                              //4
            parseJson(key, prevIndiv, nestedKey, valueObj.get(nestedKey),        //5
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
         *          person1 -[p_person_language]-> person1_language1 -[has_language_value]-> "EN", and same for "Span"
         *          The colValue is set to the arrayElement to determine a path that concludes in a data property
         *
         * 4. Else if the array contains non-primitive values, recursively examine each value of the array
         *    e.g., prev= person, key=friends, value=[{name:Joe, age:21}, {name:Bob, age:20}]
         *          person1 -[p_person_friends]-> person1_friends1 -[name]-> Joe
         *                                                         -[age]-> 21
         *                  -[p_person_friends]-> person1_friends1 -[name]-> Bob
         *                                                         -[age]-> 20
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
     * @param extractedField The (simplified) JSONPath of the attribute
     *      extractedField = "/record/properties/p1"
     *      fieldTableClassJPath = "/record/properties" : The table class to whom the p1 is nested
     * @return a list of the resources (properties and classes) that define the extractedField's path
     *         if no such path was defined, return an empty list
     *         + for each such element "create new individual?" value
     */
    private ArrayList<Pair<OntResource,Boolean>> getColPath(String extractedField) {
        String tableClassPath = extractedField.equals(root)? extractedField
                : extractedField.substring(0, extractedField.lastIndexOf("/"));                                     System.out.println("Get Path for element " + extractedField + " of class " + tableClassPath);

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
     *          language : person0_language0, person0_language1
     * person : person1
     *          language : person1_language0, person1_language1
     */
    protected long getClassCounter(String tableClassPath, String className) {
        if(!classCounter.get(tableClassPath).containsKey(className))
            classCounter.get(tableClassPath).put(className, 0L);                                                        System.out.println("Get counter for table " + tableClassPath + " element : " + className + " = " + classCounter.get(tableClassPath).get(className));
        return classCounter.get(tableClassPath).get(className);
    }

    /**
     * Set empty counter set for table with JPath extractedField if it doesn't already exist
     * (it doesn't reset the counters of table extracted field, just initializes the hashmap)
     * @param extractedField JPath of a table
     */
    private void setCounter(String extractedField) {                                                                    System.out.println("Set counter for " + extractedField);
        if(!classCounter.containsKey(extractedField))
            classCounter.put(extractedField, new HashMap<>());
    }

    /**
     * Create an individual if it doesn't exist
     * @param indivType : The class of the individual (rdf:type)
     * @param extractedField : The JPath of the column/attribute (includes the column)
     * @param prevIndiv : The individual created from the previous node (e.g., record1). Else if isRoot it's null
     * @param isRoot : Is the new root individual created? (e.g., a new record or a new person)
     * @param createNewIndiv : Whether a new individual of the indivType class should be created
     *                         or the last individual that was previously created should be reused instead
     * @return The resource that was created (or existed with this URI in the pModel)
     */
    private Resource createIndiv(OntClass indivType,
                                   String extractedField,
                                   Resource prevIndiv,
                                   boolean isRoot,
                                   boolean createNewIndiv
    ){
        /*
         * 1. className : The name of the class of the new individual that will be created (or retrieved)
         *    tableClassPath : The JPath of the table that the current column was found in
         *          e.g., extractedField="/person.json/age"   className="age"   tableClassPath="/person.json"
         *    classCounterValue : The id of the next individual of the className that will be created
         *    indivLabel : The uri of the new (or retrieved individual)
         *    (The default values "" and -1 are never used)
         *
         * 2. If the individual that will be created is root (e.g., record or person.json)
         *    indivLabel = ontologyURI/classNameCurrRowID e.g., :record0
         *    The id for a root individual is a single incremental integer (currRowID)
         *
         * 3. Else, if non-root element
         *
         *      4. tableClassWithPath is the class (with className) of a table that has a path in its mapping
         *          {table: "/something1/something2",                 and extractedField=t"/something1/something2"
         *           mapping: {..., path: [something non-null]}}
         *       To create an individual of the class of a table that has path,
         *       the nested table's individual will have the same numbers as its container table (caught by the regex \d+$ : at least one digit at the end of the prevIndiv URI)
         *       e.g., record0 -[hasSQtA]-> record0_SQtA0 -[hasItem]-> record0_SQtA0_SQtAitem0
         *                                                             record0_SQtA0_SQtAitem1
         *             so both items (0 and 1) will be attached (with hasItem) to the same SQtA sequence individual
         *             (the item/table path element 's counter increases, not the column/sequence's
         *
         *      5. Else, in case of table without path or simple.dcm attribute column
         *         the uri of the new individual is "{prevIndivURI}_{className}{classCounterValue}"
         *         e.g., /person.json/age        person0 -[has_age]-> person0_age0 ->...-> 35
         *               /person.json/address    person0 -[has_address]-> person0_address0  //-[has_city]->NY
         *                                                           person0_address1  //-[has_city]->LA
         *
         *        To reuse an existing (the last created individual of the class with className):
         *              The classCounter (<tableName, <className, long>>) stores the id of the next className individual
         *              that will be created. -> so to retrieve the previously created individual of className subtract 1
         *
         * 6. When trying to create a new or retrieve the last created individual:
         *    If createNewIndiv==false (retrieve) then indiv should not be null (not found) -> error!
         *    If createNewIndiv==true (create new) then indiv should be null
         *
         */
        String className, tableClassPath="", indivLabel;  //1
        long classCounterValue=-1;                                                                                      System.out.println("> Create indiv of type " + indivType);

        if(isRoot) {  //2
            className = root.substring(1);                                                                     System.out.println("\tisRoot true");
            indivLabel = String.format("%s%d", className, currRowID);
        }else {      //3
            className = getLocalName(indivType);
            tableClassPath = extractedField.substring(0, extractedField.lastIndexOf("/"));
            classCounterValue = getClassCounter(tableClassPath, className);                                             System.out.println("\tclass counter value = " + classCounterValue);
            String prevIndivLabel = indivNames.getKey(getLocalName(prevIndiv));

            boolean isTableClassWithPath = tablesClass.containsKey(extractedField)
                    && tablesClass.get(extractedField).hasPath();  //4

            if(isTableClassWithPath && !className.endsWith("Item")) {                                                   System.out.println("\text field " + extractedField + " is table with path");
                Matcher match = Pattern.compile("\\d+$").matcher(prevIndivLabel);
                match.find();
                indivLabel = String.format("%s_%s%s", prevIndivLabel, className, match.group());
            }else{  //5
                classCounterValue = getClassCounter(tableClassPath, className);
                indivLabel = String.format("%s_%s%d", prevIndivLabel, className, classCounterValue - (createNewIndiv ? 0 : 1));
            }
        }
        Resource indiv = null;                                                                                                  System.out.println("\tTry Indiv = "+ indivLabel+ " create new ? " + createNewIndiv + "\n\t counter = " + classCounter);
        if(indivNames.containsKey(indivLabel))
            indiv = ontology.getOntResource(config.Out.POntologyBaseNS + indivNames.get(indivLabel));


        assert !(indiv == null && !createNewIndiv); //6
        if(indiv == null) {
            indivCounter = indivCounter.add(BigInteger.ONE);
            indivNames.put(indivLabel, indivCounter.toString());
            indiv = ontology.createResource(config.Out.POntologyBaseNS + indivCounter, null, indivLabel, null);
            indiv.addProperty(RDF.type, indivType);
            indiv.addLiteral(SKOS.prefLabel, String.format("%d_%d_%s", currRowID - (isRoot?0:1), indivCounter, getLocalName(indivType)));   System.out.println("\tcreate " + indivLabel + " as " + indiv);

            if(isRoot)
                ++currRowID;
            else
                classCounter.get(tableClassPath).replace(className, classCounterValue + 1);
        }
        else System.out.println("\tindiv exists " + indivLabel + " as " + indiv);

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
        ArrayList<Pair<OntResource,Boolean>> cp = getColPath(extractedField);                                           System.out.println("Create col path for extr field = " + extractedField + "\n\tpath = " + cp + "\n\tprev indiv = " + indivNames.getKey(getLocalName(prevIndiv)));
        Resource prevNode = prevIndiv;                                          //1
        int upperBound = colValue == null ? cp.size() : cp.size() - 2;          //2
        for (int i = 0; i < upperBound; i+=2) {

            // 3
            Resource nextNode = createIndiv(cp.get(i+1).pathElement().asClass(),
                                            extractedField, prevNode,
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


    /*public static void main(String[] args) {
        ArrayList<String> files = new ArrayList<>(Collections.singleton("src/main/resources/dicom_data/simple.json"));
        files.add("src/main/resources/dicom_data/ct.json");
        //ArrayList<String> files = new ArrayList<>(Collections.singleton("src/main/resources/json_data/person.json"));
        System.out.println(files);
        new InsertDataJSON(InputDataSource.ontologyName, files);
    }*/
}
