package org.example.temp.replaced;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.example.B_InputDatasetProcessing.DICOM.TagDictionary;
import org.example.util.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import static org.example.B_InputDatasetProcessing.DICOM.DICOMUtil.parseForTime;

public class DICOM2JSON {

    private HashMap<String, JsonObject> dson = new HashMap<>();
    private TagDictionary tagDictionary = new TagDictionary();

    /**
     * Transform the input dicom files' metadata/tags to json files
     * @param dicomFilePaths A list of dicom files
     */
    public DICOM2JSON (ArrayList<String> dicomFilePaths, boolean saveDsonFiles) {

        for (String dicomFilePath : dicomFilePaths) {                                                                   //System.out.println(dicomFilePath);
            try (DicomInputStream dis = new DicomInputStream(new File(dicomFilePath))) {
                JsonObject dicom2json = new JsonObject();

                Attributes attributes = dis.readDataset();
                readAttributes(dicom2json, attributes);                                                                 // , false, "root");                                        //System.out.println(attributes);

                String jsonPath = dicomFilePath.substring(0, dicomFilePath.lastIndexOf(".")) + ".json";
                if(saveDsonFiles)
                    JsonUtil.saveToJSONFile(jsonPath, dicom2json);
                dson.put(dicomFilePath.substring(dicomFilePath.lastIndexOf("/")+1), dicom2json);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public HashMap<String, JsonObject> getDson () {
        return dson;
    }

    public ArrayList<JsonObject> getDsonAsList() {
        return new ArrayList<>(dson.values());
    }

    public TagDictionary getTagDictionary() {
        return tagDictionary;
    }

    /**
     * Recursive method that reads the attribute list of a dicom file.
     * 1. For each tag in the file's attributes :
     *      2. Retrieve its code (GGGG,AAAA), its string name and its Value Representation
     *      3. Unknown/Private Tags don't have a name
     *      4. If the tag is a sequence -> turn the sequence to a nested json element -> valueElement
     *          5. If the sequence contains a single item, the nested element will be a dictionary whose tags will be
     *             the nested tags sqItem
     *          6. Else if the sequence contains multiple nested items, the nested element will be a json array
     * @param prevElem : The previous json element where the current element is nested inside
     * @param attributes : The attributes list of the current element
     */
    private void readAttributes(JsonElement prevElem, Attributes attributes){                                           // , boolean isSQ, String prev) {
        int[] tags = attributes.tags();                                                                                 //if(isSQ) System.out.println("\t>> Item");

        for (int tag : tags) {   //1
            String tagCode = TagUtils.toString(tag);  // 2
            String tagName = ElementDictionary.keywordOf(tag, null);
            VR vr = attributes.getVR(tag);

            if ("".equals(tagName))  // 3
                tagName = "Unknown Tag and Data";

            tagDictionary.put(tagCode, tagName, vr);
                                                                                                                         //System.out.println((isSQ ? "\t " : "") + tagCode + "\t" + tagName + "\t" + vr + (isSQ ? "\t" + attributes.getString(tag) : "") + "\tprev = " + prev + "\ttype = " + (prevElem instanceof JsonArray ? "Array" : "Dictionary"));
            if(vr == VR.SQ) {   // 4
                Sequence sq = attributes.getSequence(tag);                                                              //System.out.println("\t# items = " + sq.size() + " [");

                JsonElement valueElement;

                valueElement = new JsonArray();
                for(Attributes sqItem : sq) {
                    JsonObject nestedObj = new JsonObject();
                    readAttributes(nestedObj, sqItem);                                                              //, true, tagName);
                    valueElement.getAsJsonArray().add(nestedObj);
                }

                prevElem.getAsJsonObject().add(tagCode, valueElement);                                                  //System.out.println("]");
            }else {
                String value = parseForTime(attributes.getString(tag), vr);
                prevElem.getAsJsonObject().addProperty(tagCode, value);
            }
        }
    }



    /* public static void main(String[] args) {
        ArrayList<String> dicomFilePaths = new ArrayList<>();
        // complex file
        dicomFilePaths.add("src/main/resources/dicom/complex");
        // simple.dcm file
        dicomFilePaths.add("src/main/resources/dicom/simple.dcm");
        new DICOM2JSON(dicomFilePaths, true);
    }*/
}
