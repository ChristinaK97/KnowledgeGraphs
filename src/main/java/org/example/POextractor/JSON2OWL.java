package org.example.POextractor;

import com.google.gson.*;
import org.example.POextractor.Properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full Example:
 *
 * JSON =
 * {
 *   "person": {
 *     "name": "John Doe",
 *     "age": 30,
 *     "city": "New York",
 *     "languages": ["English", "Spanish", "French"],
 *     "address": {
 *       "street": "123 Main St",
 *       "city": "New York",
 *       "state": "NY"
 *     },
 *     "friends": [
 *       {
 *         "name": "Jane Smith",
 *         "age": 28,
 *         "city": "Los Angeles"
 *       },
 *       {
 *         "name": "Bob Johnson",
 *         "age": 32,
 *         "city": "Chicago"
 *       }
 *     ],
 *     "status": "active",
 *     "registered": true,
 *     "lastLogin": null
 *   }
 * }
 *
 * root = person
 *
 * Simple key:value pairs
 * person -[has_name]-> xsd:string  (John Doe)
 * person -[has_status]-> xsd:string  (active) etc
 *
 * value is a list of simple elements
 * person -[has_languages]-> xsd:string (English)
 * person -[has_languages]-> xsd:string (Spanish) etc
 *
 * value is a list of dictionaries
 * person -[has_friends]-> friends -[has_name]-> Jane and other properties for Jane
 * person -[has_friends]-> friends -[has_name]-> Bob and other properties for Bob
 *
 * value is a dictionary with simple values
 * person -[has_address]-> address
 * address -[has_street]-> xsd:string (123 Main St)
 * address -[has_city]-> xsd:string (New York) etc
 *
 */
public class JSON2OWL {

    boolean print = false;

    protected HashMap<String, String> convertedIntoClass = new HashMap<>();
    protected Properties objProperties = new Properties();
    protected Properties dataProperties = new Properties();

    protected HashMap<String, String> attrClasses = new HashMap<>();
    protected Properties newObjectProperties = new Properties();
    private ArrayList<Properties.DomRan> nullValuedProperties = new ArrayList<>();

    private boolean turnAttrToClasses;

    private String root;
    private String ROOTCLASS = "record";

    public JSON2OWL(boolean turnAttrToClasses) {
        this.turnAttrToClasses = turnAttrToClasses;
    }

    public void applyRules(String file) {
        JsonElement json = readJSON(file);
        findRoot(json);
        parseJson(root, null, json,
                root.equals(ROOTCLASS) ? "/" + root : ""
        );

    }

    private JsonElement readJSON(String file) {
        JsonElement json = null;
        try {
            // check if json is valid
            String jsonContent =  new String(Files.readAllBytes(Paths.get(file)));

            try {
                JsonParser.parseString(jsonContent);
            } catch (JsonSyntaxException e) {
                System.err.println("INVALID");
                jsonContent = fixJSON(jsonContent);
            }
            // create JsonElement object
            json = new Gson().fromJson(jsonContent, JsonElement.class);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return json;
    }


    private String fixJSON(String jsonContent) {
        String pattern = "\\}[\\r\\n]+";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(jsonContent);

        // Insert commas after closing braces
        StringBuffer sb = new StringBuffer("[\n");
        while (matcher.find()) {
            matcher.appendReplacement(sb, "},");
        }
        matcher.appendTail(sb);
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }


    private void findRoot(JsonElement json) {

        // ROOT PATTERN 1: Outer element is an array [{},...]
        if (json.isJsonArray()) {
            if(print) System.out.println("array");
            root = ROOTCLASS;
        }
        // ROOT PATTERN 2: Outer element is a dictionary
        else if (json.isJsonObject()) {
            if(print) System.out.println("object");
            JsonObject jsonObj = json.getAsJsonObject();

            if (jsonObj.keySet().size() == 1)
                // ROOT PATTERN 2.1: Outer element is a dictionary with a single attribute
                // {"root" : [] or {} }
                root = jsonObj.keySet().iterator().next();
            else
                // ROOT PATTERN 2.2: Outer element is a dictionary with multiple attributes
                // {"a" : ., "b"}
                root = ROOTCLASS;

        } else {
            System.err.println("Invalid JSON");
            root = "?";
        }
        convertedIntoClass.put(root, root);
    }

    /**
     * Recursively read all json nested elements
     */
    private void parseJson(String prev, String key, JsonElement value, String extractedField) {
        if(print) System.out.println("REC prev= " + prev + "\tkey= "+ key + "\tvalue= "+ value);

        if(value.isJsonPrimitive()) {
            if(print) System.out.println("prev= " + prev + "\tkey= " + key + "\tprimitive= " + value+ "\tpath= " + extractedField);
            addDataProperty(prev, key, extractedField, value.getAsJsonPrimitive());
        }
        else if(value.isJsonNull()) {
            // if an attribute exists for a record, but it has null value, then should search other records with the
            // attribute to determine the property's range
            if(print) System.out.println("prev= " + prev + "\tkey= " + key + "\tprimitive= " + value+ "\tpath= " + extractedField);
            addDataProperty(prev, key, extractedField, null);
        }
        else if(value.isJsonObject())
            parseJsonObject(prev, key, value.getAsJsonObject(), extractedField);
        else if(value.isJsonArray())
            parseJsonArray(prev, key, value.getAsJsonArray(), extractedField);

    }


    private void parseJsonObject(String prev, String key, JsonObject valueObj, String extractedField) {
        if(print) System.out.println("JObject");
        if(key == null)
            key = prev;
        else {
            String newClass = key;
            convertedIntoClass.put(key, newClass);
            addObjectProperty(prev, key, extractedField, "jsonObj");
        }

        for(String nestedKey : valueObj.keySet()) {
            parseJson(key, nestedKey, valueObj.get(nestedKey),
                    String.format("%s/%s", extractedField, nestedKey));
        }
    }


    private void parseJsonArray(String prev, String key, JsonArray valueArray, String extractedField) {
        if(print) System.out.println("JArray");
        boolean[] type = arrayType(valueArray);

        if(type[0] && type[1]) //mixed
            addDataProperty(prev, key, extractedField, new JsonPrimitive("string"));

        else if (type[1]) { //non-primitive
            if(key == null)
                key = prev;
            else {
                String newClass = key;
                convertedIntoClass.put(key, newClass);
                addObjectProperty(prev, key, extractedField, "jsonArray");
            }
        }
        for(JsonElement arrayElement : valueArray) {
            parseJson(prev, key, arrayElement,
                    String.format("%s", extractedField));
        }
    }

    private boolean isInvalidProperty(String domain, String range, String extractedField){
        HashSet<String> invalid = new HashSet<>(Set.of("None", "null", "", " "));
        return  domain == null || range == null ||
                invalid.contains(domain) || invalid.contains(range) ||
                (domain.equals(range) && extractedField.equals("/"+domain));

    }

    private void addObjectProperty(String domain, String range, String extractedField, String rule) {
        if(isInvalidProperty(domain, range, extractedField))
            return;

        String newClass = range;
        domain = convertedIntoClass.get(domain);
        objProperties.addProperty(
                rule,
                domain,
                String.format("p_%s_%s", domain, newClass),
                newClass,
                extractedField
        );
        //if(print) System.out.println("ADD OP: " + String.format("p_%s_%s", domain, newClass) + " " + rule);
    }

    // domain ( -[has_range]-> range )* -[has_range_VALUE]-> type(value)
    private void addDataProperty(String domain, String range, String extractedField, JsonPrimitive value) {
        if(isInvalidProperty(domain, range, extractedField))
            return;

        String domainClass = convertedIntoClass.get(domain);
        if (turnAttrToClasses) {
            String attrClass = range;
            domainClass = attrClass;
            attrClasses.put(range, attrClass);
            newObjectProperties.addProperty("dp",
                    convertedIntoClass.get(domain),
                    "has_"+attrClass,
                    attrClass,
                    extractedField
            );
        }
        String dtPropName = "has_"+range+"_VALUE";
        String xsdDatatype = JSON2XSD(value);
        dataProperties.addProperty(
                "dp",
                domainClass,
                dtPropName,
                xsdDatatype,
                extractedField
        );
        if(xsdDatatype == null)
            nullValuedProperties.add(dataProperties.getPropertyDomRan(dtPropName));
    }

    private boolean[] arrayType(JsonArray array) {
        //isPrimitive : type[0]
        //isNonPrimitive : type[1]
        //isMixed : type[0] && type[1]
        boolean[] type = new boolean[2];
        for(JsonElement element : array)
            if(element.isJsonPrimitive())
               type[0] = true;
            else
                type[1] = true;
        return type;
    }



    private String JSON2XSD(JsonPrimitive value) {
        if (value == null)
            return null;
        try {
            value.getAsInt();
            return "xsd:integer";
        }catch (NumberFormatException e1) {
            try {
                value.getAsFloat();
                return "xsd:decimal";
            }catch (NumberFormatException e2) {
                try {
                    value.getAsDouble();
                    return "xsd:decimal";
                }catch (NumberFormatException ignored) {
                    if(value.isBoolean())
                        return "xsd:boolean";
                    else if (value.isString())
                        return "xsd:string";
                }
            }
        }
        return null;
    }

    public void removeNullRanges() {
        if(nullValuedProperties.size() > 0) {
            for(Properties.DomRan domRan : nullValuedProperties) {
                // remove the null range and, if another type wasn't found set it to the most
                // general datatype i.e., string
                domRan.range.remove(null);
                if (domRan.range.size() == 0)
                    domRan.range.add("xsd:string");
            }
        }
    }

    public String getRoot() {
        return root;
    }

    public void print() {
        System.out.println(">> CLASSES");
        System.out.println(convertedIntoClass.values());
        System.out.println(attrClasses.values());
        System.out.println(">> OBJ PROP");
        System.out.println(objProperties);
        System.out.println(">> NEW OBJ PROP");
        System.out.println(newObjectProperties);
        System.out.println(">> DATA PROP");
        System.out.println(dataProperties);
    }


}
