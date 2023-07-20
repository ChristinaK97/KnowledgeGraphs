package org.example.POextractor;

import com.google.gson.JsonObject;
import org.example.InputPoint.DICOM.DICOM2JSON;
import org.example.InputPoint.DICOM.TagDictionary;
import org.example.InputPoint.SQLdb.DBSchema;
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

    // for files (not sql rdb)
    private String rootElementName = null;
    private String fileExtension = null;

    // for dicom files
    private TagDictionary tagDictionary = null;

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

    // JSON-LIKE FILE TYPES RULES
    public void applyRules(ArrayList<String> files) {
        JSON2OWL json2owl;
        fileExtension = files.get(0).substring(files.get(0).lastIndexOf(".")+1);
        if("json".equals(fileExtension))
            json2owl = applyRulesToJson(files);
        else if ("dcm".equals(fileExtension))
            json2owl = applyRulesToDson(files);
        else {
            throw new UnsupportedOperationException("Unsupported file format " + fileExtension);
        }
        json2owl.removeNullRanges();
        rootElementName = json2owl.getRoot();
        json2owl.print();

        classes.put("Table", json2owl.convertedIntoClass);
        objProperties.put("FK", json2owl.objProperties);
        dataProperties = json2owl.dataProperties;
        if (turnAttributesToClasses) {
            classes.put("Attribute", json2owl.attrClasses);
            objProperties.put("Attribute", json2owl.newObjectProperties);
        }

    }

    private JSON2OWL applyRulesToJson(ArrayList<String> jsonFiles) {
        JSON2OWL json2owl = new JSON2OWL(turnAttributesToClasses);
        jsonFiles.forEach(json2owl::applyRules);
        return json2owl;
    }

    private JSON2OWL applyRulesToDson(ArrayList<String> dicomFiles) {

        DICOM2JSON dicom2json = new DICOM2JSON(dicomFiles, true);
        ArrayList<JsonObject> dson = dicom2json.getDsonAsList();
        tagDictionary = dicom2json.getTagDictionary();

        JSON2OWL dson2owl = new JSON2OWL(turnAttributesToClasses, tagDictionary);
        dson.forEach(dson2owl::applyRules);
        return dson2owl;
    }


    // GETTERS
    public HashMap<String, HashMap<String, String>> getClasses() {
        return classes;
    }

    public Properties getFKObjProperties() {
        return objProperties.get("FK");
    }
    public Properties getAttrObjProp() {
        return objProperties.get("Attribute");
    }

    public Properties getDataProperties() {
        return dataProperties;
    }

    public String getRootElementName() {
        return rootElementName;
    }

    public boolean isDson() {
        return tagDictionary != null;
    }

    public TagDictionary getTagDictionary() {
        return tagDictionary;
    }

}
