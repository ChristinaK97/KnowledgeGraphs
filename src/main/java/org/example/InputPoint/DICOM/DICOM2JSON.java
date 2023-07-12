package org.example.InputPoint.DICOM;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.example.other.JsonUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class DICOM2JSON {

    private TagDictionary tagDictionary = new TagDictionary();

    public DICOM2JSON (ArrayList<String> dicomFilePaths) {

        for (String dicomFilePath : dicomFilePaths) {                                                                   //System.out.println(dicomFilePath);
            try (DicomInputStream dis = new DicomInputStream(new File(dicomFilePath))) {
                JsonObject dicom2json = new JsonObject();

                Attributes attributes = dis.readDataset();
                readAttributes(dicom2json, attributes);                                                                 // , false, "root");                                        //System.out.println(attributes);

                String jsonPath = dicomFilePath.substring(0, dicomFilePath.lastIndexOf(".")) + ".json";
                JsonUtil.saveToJSONFile(jsonPath, dicom2json);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void readAttributes(JsonElement prevElem, Attributes attributes){                                           // , boolean isSQ, String prev) {
        int[] tags = attributes.tags();                                                                                 //if(isSQ) System.out.println("\t>> Item");

        for (int tag : tags) {
            String tagCode = TagUtils.toString(tag);
            String tagName = ElementDictionary.keywordOf(tag, null);
            VR vr = attributes.getVR(tag);

            tagDictionary.put(tagCode, tagName, vr);

            if ("".equals(tagName))
                tagName = tagCode;
                                                                                                                         //System.out.println((isSQ ? "\t " : "") + tagCode + "\t" + tagName + "\t" + vr + (isSQ ? "\t" + attributes.getString(tag) : "") + "\tprev = " + prev + "\ttype = " + (prevElem instanceof JsonArray ? "Array" : "Dictionary"));
            if(vr == VR.SQ) {
                Sequence sq = attributes.getSequence(tag);                                                              //System.out.println("\t# items = " + sq.size() + " [");

                JsonElement valueElement;
                if (sq.size() == 1) {
                    valueElement = new JsonObject();
                    for(Attributes sqItem : sq)
                        readAttributes(valueElement, sqItem);                                                           // , true, tagName);

                }else {
                    valueElement = new JsonArray();
                    for(Attributes sqItem : sq) {
                        JsonObject nestedObj = new JsonObject();
                        readAttributes(nestedObj, sqItem);                                                              //, true, tagName);
                        valueElement.getAsJsonArray().add(nestedObj);
                    }
                }
                prevElem.getAsJsonObject().add(tagName, valueElement);                                                  //System.out.println("]");
            }else {
                String value = attributes.getString(tag);
                prevElem.getAsJsonObject().addProperty(tagName, value);
            }

        }
    }



    public static void main(String[] args) {
        ArrayList<String> dicomFilePaths = new ArrayList<>();
        // complex file
        dicomFilePaths.add("src/main/resources/dicom/IM-0002-0006-0001.dcm");
        // simple file
        dicomFilePaths.add("src/main/resources/dicom/IM-0012-0030-0001.dcm");
        new DICOM2JSON(dicomFilePaths);
    }
}
