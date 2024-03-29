package org.example.C_POextractor;

import com.google.gson.*;
import static org.example.A_Coordinator.Pipeline.config;
import org.example.util.JsonUtil;
import org.example.util.XSDmappers;

import java.util.*;

import static org.example.A_Coordinator.config.Config.DEV_MODE;
import static org.example.util.Annotations.*;
import static org.example.util.FileHandler.getFileNameWithoutExtension;

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
 *                         address -[has_street]-> xsd:string (123 Main St)
 *                         address -[has_city]-> xsd:string (New York) etc
 *
 * (turnAttributesToClasses is set to false in the above example,
 *  so only TableClasses are created, not AttributeClasses)
 */
public class JSON2OWL {

    private String filename;

    protected HashMap<String, String> tableClasses = new HashMap<>();
    protected Properties pureObjProperties = new Properties();
    protected Properties dataProperties = new Properties();

    protected HashMap<String, String> attrClasses = new HashMap<>();
    protected Properties attrObjProperties = new Properties();
    protected ArrayList<Properties.DomRan> nullValuedProperties = new ArrayList<>();

    private String root;

    public void applyRules(String file, JsonObject json) {
        this.filename = getFileNameWithoutExtension(file);
        applyRules(json);
    }

    public void applyRules(String file) {
        this.filename = getFileNameWithoutExtension(file);
        JsonElement json = JsonUtil.readJSON(file);
        applyRules(json);
    }

    private void applyRules(JsonElement json) {
        findRoot(json);
        parseJson(root, null, json,
                root.equals(config.In.DefaultRootClassName) ? "/" + root : ""
        );
    }


    private void findRoot(JsonElement json) {
        /* Root patterns:
         *-------------------
         * 1. Outer element is an array [{},...]
         *    -> Create a custom "record" class for root
         *
         * 2. Outer element is a dictionary
         *
         *    2.1. Outer element is a dictionary with a single attribute
         *         {"root" : [] or {} }
         *         -> Use the existing element as root
         *
         *    2.2. Outer element is a dictionary with multiple attributes
         *         {"a" : ., "b"}
         *         -> Create a custom "record" class for root
         */
        if (json.isJsonArray()) {                               //1
            root = config.Out.RootClassName;                                                                            if(DEV_MODE) System.out.println("array");
        }

        else if (json.isJsonObject()) {                         //2
            JsonObject jsonObj = json.getAsJsonObject();                                                                if(DEV_MODE) System.out.println("object");

            if (jsonObj.keySet().size() == 1) {                       //2.1
                root = jsonObj.keySet().iterator().next();
                config.Out.RootClassName = root;
            }else                                                    //2.2
                root = config.Out.RootClassName;

        } else {
            System.err.println("Invalid JSON");
            root = "?";
        }
        addTableClass("/" + root, root);
    }


// =====================================================================================================================

    /**
     * Recursively read all json nested elements
     */
    private void parseJson(String prev, String key, JsonElement value, String extractedField) {
                                                                                                                        if(DEV_MODE) System.out.println("REC prev= " + prev + "\tkey= "+ key + "\tvalue= "+ value);
        if(value.isJsonPrimitive()) {                                                                                   if(DEV_MODE) System.out.println("prev= " + prev + "\tkey= " + key + "\tprimitive= " + value+ "\tpath= " + extractedField);
            addDataProperty(prev, key, extractedField, value.getAsJsonPrimitive());
        }
        else if(value.isJsonNull()) {                                                                                   if(DEV_MODE) System.out.println("prev= " + prev + "\tkey= " + key + "\tprimitive= " + value+ "\tpath= " + extractedField);
            // if an attribute exists for a record, but it has null value, then should search other records with the
            // attribute to determine the property's range
            addDataProperty(prev, key, extractedField, null);
        }
        else if(value.isJsonObject())
            parseJsonObject(prev, key, value.getAsJsonObject(), extractedField);
        else if(value.isJsonArray())
            parseJsonArray(prev, key, value.getAsJsonArray(), extractedField);
                                                                                                                        if(DEV_MODE) System.out.println();
    }


    private void parseJsonObject(String prev, String key, JsonObject valueObj, String extractedField) {
                                                                                                                        if(DEV_MODE) System.out.println("JObject");
        if(key == null)
            key = prev;
        else {
            String newClass = key;
            addTableClass(extractedField, newClass);
            addObjectProperty(prev, key, extractedField, "jsonObj");
        }

        for(String nestedKey : valueObj.keySet()) {
            parseJson(key, nestedKey, valueObj.get(nestedKey),
                    String.format("%s/%s", extractedField, nestedKey));
        }
    }


    private void parseJsonArray(String prev, String key, JsonArray valueArray, String extractedField) {
                                                                                                                        if(DEV_MODE) System.out.println("JArray");
        boolean[] type = JsonUtil.arrayType(valueArray);

        if(type[0] && type[1]) //mixed
            addDataProperty(prev, key, extractedField, new JsonPrimitive("string"));

        else if (type[1]) { //non-primitive
            if(key == null)
                key = prev;
            else {
                String newClass = key;
                addTableClass(extractedField, newClass);
                addObjectProperty(prev, key, extractedField, "jsonArray");
            }
        }
        for(JsonElement arrayElement : valueArray) {
            parseJson(prev, key, arrayElement, extractedField);
        }
    }


// =====================================================================================================================

    protected void addTableClass(String tableName, String tableClassName) {
        tableClasses.put(tableName, tableClassName);

        // TODO: preprocessing notif
        String notificationFilename = getFileNameWithoutExtension(config.notification.getFilename());
        if(this.filename.equals(notificationFilename))
            config.notification.addExtractedTableName(tableName);
    }

    protected void addObjectProperty(String domain, String range, String extractedField, String rule) {
        if(JsonUtil.isInvalidProperty(domain, range, extractedField))
            return;

        String newClass = range;
        //domain = tableClasses.get(domain);
        pureObjProperties.addProperty(
                rule,
                domain,
                pureObjPropName(domain, newClass),
                newClass,
                extractedField
        );
                                                                                                                        if(DEV_MODE) System.out.println("\tADD OP: " + String.format("p_%s_%s", domain, newClass) + "\t\t" + rule);
    }

    // domain ( -[has_range]-> range )* -[has_range_VALUE]-> type(value)
    protected void addDataProperty(String domain, String range, String extractedField, JsonPrimitive value) {
        if(JsonUtil.isInvalidProperty(domain, range, extractedField))
            return;

        String domainClass = domain; //tableClasses.get(domain);
        if (config.Out.turnAttributesToClasses) {
            String attrClass = range;                                                                                   if(DEV_MODE) System.out.println("\tADD ATCL " + attrClass + "\t\t" + extractedField);
            domainClass = attrClass;
            attrClasses.put(extractedField, attrClass);
            attrObjProperties.addProperty("dp",
                    domain,
                    attrObjectPropertyName(attrClass),
                    attrClass,
                    extractedField
            );                                                                                                          if(DEV_MODE) System.out.println("\tADD NEW OP: " + String.format("p_%s_%s", domain, attrClass) + " \t\t" + extractedField);
        }
        String dtPropName = dataPropName(range);
        String xsdDatatype =  getDataPropertyRange(value, range);
        dataProperties.addProperty(
                "dp",
                domainClass,
                dtPropName,
                xsdDatatype,
                extractedField
        );                                                                                                              if(DEV_MODE) System.out.println("\tADD DP: " + dtPropName + " \t\t" + extractedField);
        if(xsdDatatype == null)
            nullValuedProperties.add(dataProperties.getPropertyDomRan(dtPropName));
    }


    protected String getDataPropertyRange(JsonPrimitive value, String range) {
        return XSDmappers.JSON2XSD(value);
    }

// =====================================================================================================================

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



    public void print() {
        System.out.println(">> CLASSES");
        System.out.println(tableClasses.values());
        System.out.println(attrClasses.values());
        System.out.println(">> OBJ PROP");
        System.out.println(pureObjProperties);
        System.out.println(">> NEW OBJ PROP");
        System.out.println(attrObjProperties);
        System.out.println(">> DATA PROP");
        System.out.println(dataProperties);
    }


}
