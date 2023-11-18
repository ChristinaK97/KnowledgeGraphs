package org.example.C_POextractor;

import static org.example.A_Coordinator.Pipeline.config;
import static org.example.A_Coordinator.config.Config.DEV_MODE;

import org.example.B_InputDatasetProcessing.DICOM.DICOM2SediJSON;
import org.example.B_InputDatasetProcessing.DICOM.TagDictionary;
import org.example.B_InputDatasetProcessing.Tabular.RelationalDB;
import org.example.B_InputDatasetProcessing.Medical.AbbreviationsDictionary;
import org.example.C_POextractor.RDB2OWL.ClassExtractor;
import org.example.C_POextractor.RDB2OWL.DataPropExtractor;
import org.example.C_POextractor.RDB2OWL.ObjectPropExtractor;

import org.example.util.Annotations;
import org.example.util.DatasetDictionary;

import java.util.ArrayList;
import java.util.HashMap;

public class RulesetApplication {

    HashMap<String, HashMap<String, String>> classes = new HashMap<>(2);
    HashMap<String, String> key_isSubclassOf_value = new HashMap<>();
    HashMap<String, Properties> objProperties = new HashMap<>(2);
    Properties dataProperties;


    // for dicom files and medical with abbrev expansion on
    private DatasetDictionary datasetDictionary = null;

    public RulesetApplication(Object dataSource) {
        if(dataSource instanceof RelationalDB)
            applyRules((RelationalDB) dataSource);
        else if (dataSource instanceof ArrayList)
            applyRules((ArrayList<String>) dataSource);
    }



// =====================================================================================================================
    // RELATIONAL DATABASE RULES
// =====================================================================================================================

    public void applyRules(RelationalDB db) {
        /* 1. table classes
         * 2. object properties connecting table classes
         * 3. data properties
         * 4. object properties connecting table classes with attribute classes. if !turnAttrToClasses :empty
         */
        classes.put(Annotations.TABLE_PREFIX, new ClassExtractor(db).getTableClasses()); /*1*/
        objProperties.put(Annotations.PURE_PREFIX, new ObjectPropExtractor(db, classes.get(Annotations.TABLE_PREFIX)).getPureObjProperties());  /*2*/

        DataPropExtractor dpExtr = new DataPropExtractor(db, classes.get(Annotations.TABLE_PREFIX));    /*3*/
        dataProperties = dpExtr.getDataProperties();

        if(config.Out.turnAttributesToClasses) { /*4*/
            classes      .put(Annotations.ATTRIBUTE_PREFIX, dpExtr.getAttrClasses());
            objProperties.put(Annotations.ATTRIBUTE_PREFIX, dpExtr.getAttrObjProperties());
        }

        if(config.Out.applyMedAbbrevExpansion)
            datasetDictionary = new AbbreviationsDictionary(db);
    }


// =====================================================================================================================
    // JSON-LIKE FILE TYPES RULES
// =====================================================================================================================

    public void applyRules(ArrayList<String> files) {
        JSON2OWL json2owl;

        if(config.In.isJSON())
            json2owl = applyRulesToJson(files);

        else if (config.In.isDSON())
            json2owl = applyRulesToDson(files);

        else {
            throw new UnsupportedOperationException("Unsupported file format");
        }
        json2owl.removeNullRanges();                                                                                    if(DEV_MODE) json2owl.print();

        classes.put(Annotations.TABLE_PREFIX, json2owl.tableClasses);
        objProperties.put(Annotations.PURE_PREFIX, json2owl.pureObjProperties);
        dataProperties = json2owl.dataProperties;
        if (config.Out.turnAttributesToClasses) {
            classes.put(Annotations.ATTRIBUTE_PREFIX, json2owl.attrClasses);
            objProperties.put(Annotations.ATTRIBUTE_PREFIX, json2owl.attrObjProperties);
        }

    }


    private JSON2OWL applyRulesToJson(ArrayList<String> jsonFiles) {
        JSON2OWL json2owl = new JSON2OWL();
        jsonFiles.forEach(json2owl::applyRules);
        return json2owl;
    }



    private JSON2OWL applyRulesToDson(ArrayList<String> dicomFiles) {

        DICOM2SediJSON dicom2json = new DICOM2SediJSON(dicomFiles);
        datasetDictionary = dicom2json.getTagDictionary();

        DSON2OWL dson2owl = new DSON2OWL((TagDictionary) datasetDictionary);
        dicom2json.getDsonObjectsCollection().forEach(dson2owl::applyRules);
        // dicom2json.getDsonObjectsCollection().values().forEach(dson2owl::applyRules);
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
        return objProperties.get(Annotations.PURE_PREFIX);
    }
    public Properties getAttrObjProp() {
        return objProperties.get(Annotations.ATTRIBUTE_PREFIX);
    }

    public Properties getDataProperties() {
        return dataProperties;
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
