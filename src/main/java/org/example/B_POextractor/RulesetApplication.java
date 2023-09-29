package org.example.B_POextractor;

import com.google.gson.JsonObject;
import org.example.A_InputPoint.DICOM.DICOM2SediJSON;
import org.example.A_InputPoint.DICOM.TagDictionary;
import org.example.A_InputPoint.SQLdb.DBSchema;
import org.example.A_InputPoint.medical.AbbreviationsDictionary;
import org.example.B_POextractor.RDB2OWL.ClassExtractor;
import org.example.B_POextractor.RDB2OWL.DataPropExtractor;
import org.example.B_POextractor.RDB2OWL.ObjectPropExtractor;

import org.example.A_InputPoint.InputDataSource;
import org.example.util.DatasetDictionary;

import java.util.ArrayList;
import java.util.HashMap;

public class RulesetApplication {

    boolean turnAttributesToClasses;
    HashMap<String, HashMap<String, String>> classes = new HashMap<>(2);
    HashMap<String, String> key_isSubclassOf_value = new HashMap<>();
    HashMap<String, Properties> objProperties = new HashMap<>(2);
    Properties dataProperties;


    // for files (not sql rdb)
    private String rootElementName = null;

    // for dicom files and medical with abbrev expansion on
    private DatasetDictionary datasetDictionary = null;

    public RulesetApplication(boolean turnAttributesToClasses) {
        this.turnAttributesToClasses = turnAttributesToClasses;
    }

    public void applyRules(Object dataSource) {
        if(dataSource instanceof DBSchema)
            applyRules((DBSchema) dataSource);
        else if (dataSource instanceof ArrayList)
            applyRules((ArrayList<String>) dataSource);
    }


// =====================================================================================================================
    // RELATIONAL DATABASE RULES
// =====================================================================================================================

    public void applyRules(DBSchema db) {
        // table classes
        classes.put("Table", new ClassExtractor(db).getTableClasses());

        // object properties connecting table classes
        objProperties.put("Pure", new ObjectPropExtractor(db, classes.get("Table")).getPureObjProperties());

        System.out.println(objProperties.get("Pure"));
        DataPropExtractor dpExtr = new DataPropExtractor(db,turnAttributesToClasses, classes.get("Table"));
        // data properties
        dataProperties = dpExtr.getDataProperties();
        // object properties connecting table classes with attribute classes. if !turnAttrToClasses :empty
        if(turnAttributesToClasses){
            classes.put("Attribute", dpExtr.getAttrClasses());
            objProperties.put("Attribute", dpExtr.getAttrObjProperties());
        }

        if(InputDataSource.applyMedAbbrevExpansion)
            datasetDictionary = new AbbreviationsDictionary();
    }


// =====================================================================================================================
    // JSON-LIKE FILE TYPES RULES
// =====================================================================================================================

    public void applyRules(ArrayList<String> files) {
        JSON2OWL json2owl;

        if(InputDataSource.isJSON())
            json2owl = applyRulesToJson(files);

        else if (InputDataSource.isDSON())
            json2owl = applyRulesToDson(files);

        else {
            throw new UnsupportedOperationException("Unsupported file format");
        }
        json2owl.removeNullRanges();
        rootElementName = json2owl.getRoot();
        json2owl.print();

        classes.put("Table", json2owl.tableClasses);
        objProperties.put("Pure", json2owl.pureObjProperties);
        dataProperties = json2owl.dataProperties;
        if (turnAttributesToClasses) {
            classes.put("Attribute", json2owl.attrClasses);
            objProperties.put("Attribute", json2owl.attrObjProperties);
        }

    }


    private JSON2OWL applyRulesToJson(ArrayList<String> jsonFiles) {
        JSON2OWL json2owl = new JSON2OWL(turnAttributesToClasses);
        jsonFiles.forEach(json2owl::applyRules);
        return json2owl;
    }



    private JSON2OWL applyRulesToDson(ArrayList<String> dicomFiles) {

        DICOM2SediJSON dicom2json = new DICOM2SediJSON(dicomFiles, true);
        ArrayList<JsonObject> dson = dicom2json.getDsonAsList();
        datasetDictionary = dicom2json.getTagDictionary();

        DSON2OWL dson2owl = new DSON2OWL(turnAttributesToClasses, (TagDictionary) datasetDictionary);
        dson.forEach(dson2owl::applyRules);
        key_isSubclassOf_value = dson2owl.getKey_isSubclassOf_value();
        return dson2owl;
    }


// =====================================================================================================================
    // GETTERS
// =====================================================================================================================

    public HashMap<String, HashMap<String, String>> getClasses() {
        return classes;
    }

    public Properties getPureObjProperties() {
        return objProperties.get("Pure");
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
        return datasetDictionary != null;
    }

    public DatasetDictionary getDatasetDictionary() {
        return datasetDictionary;
    }

    public HashMap<String, String> key_isSubclassOf_value() {
        return key_isSubclassOf_value;
    }

    public String getSuperClassOf(String subclassName) {
        return key_isSubclassOf_value.get(subclassName);
    }
}
