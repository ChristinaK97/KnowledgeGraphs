package org.example.POextractor;

import org.example.InputPoint.DBSchema;
import org.example.POextractor.RDB2OWL.ClassExtractor;
import org.example.POextractor.RDB2OWL.DataPropExtractor;
import org.example.POextractor.RDB2OWL.ObjectPropExtractor;

import java.util.ArrayList;
import java.util.HashMap;

public class RulesetApplication {

    boolean turnAttributesToClasses;
    HashMap<String, HashMap<String, String>> classes = new HashMap<>(2);
    HashMap<String, Properties> objProperties = new HashMap<>(2);
    Properties dataProperties;

    public RulesetApplication(boolean turnAttributesToClasses) {
        this.turnAttributesToClasses = turnAttributesToClasses;
    }

    public void applyRules(Object dataSource) {
        if(dataSource instanceof DBSchema)
            applyRules((DBSchema) dataSource);
        else if (dataSource instanceof ArrayList)
            applyRules((ArrayList<String>) dataSource);
    }

    // RELATIONAL DATABASE RULES
    public void applyRules(DBSchema db) {
        // table classes
        classes.put("Table", new ClassExtractor(db).getConvertedIntoClass());

        // object properties connecting table classes
        objProperties.put("FK", new ObjectPropExtractor(db, classes.get("Table")).getObjProperties());

        System.out.println(objProperties.get("FK"));
        DataPropExtractor dpExtr = new DataPropExtractor(db,turnAttributesToClasses, classes.get("Table"));
        // data properties
        dataProperties = dpExtr.getDataProp();
        // object properties connecting table classes with attribute classes. if !turnAttrToClasses :empty
        if(turnAttributesToClasses){
            classes.put("Attribute", dpExtr.getAttrClasses());
            objProperties.put("Attribute", dpExtr.getNewObjProp());
        }
    }

    public void applyRules(ArrayList<String> files) {
        files.add("src/main/resources/temp/person.json");
        if(files.get(0).endsWith(".json")){
            JSON2OWL json2owl = new JSON2OWL(turnAttributesToClasses);
            files.forEach(json2owl::applyRules);
            classes.put("Table", json2owl.convertedIntoClass);
            objProperties.put("FK", json2owl.objProperties);
            dataProperties = json2owl.dataProperties;
            if (turnAttributesToClasses) {
                classes.put("Attribute", json2owl.attrClasses);
                objProperties.put("Attribute", json2owl.newObjectProperties);
            }
        }
    }
}
