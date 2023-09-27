package org.example.A_InputPoint.DICOM;

import org.dcm4che3.data.VR;
import org.example.util.DatasetDictionary;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagDictionary extends DatasetDictionary {

    class TagInfo extends DatasetElementInfo {
        VR vr;
        String xsd_datatype;

        public TagInfo(String tagName, VR vr) {
            elementName = splitCamelCase(tagName); // tagName
            this.vr = vr;
            this.xsd_datatype = DICOMUtil.DICOM2XSD(vr);
        }


        private String splitCamelCase(String input) {
            // Use a regular expression to find word boundaries in the camel case string
            Pattern pattern = Pattern.compile("[A-Z]?[a-z]+|[A-Z]+(?=[A-Z]|$)");
            Matcher matcher = pattern.matcher(input);

            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                result.append(matcher.group()).append(" ");
            }
            return result.toString().trim();
        }

    }
//======================================================================================================================


    public void put(String tagCode, String tagName, VR vr) {
        if (!datasetDictionary.containsKey(tagCode))
            datasetDictionary.put(tagCode, new TagInfo(tagName, vr));
    }


    public VR getVr(String tagCode) {
        return ((TagInfo) datasetDictionary.get(tagCode)).vr;
    }
    public String getXsd_datatype(String tagCode) {
        return ((TagInfo) datasetDictionary.get(tagCode)).xsd_datatype;
    }


}
