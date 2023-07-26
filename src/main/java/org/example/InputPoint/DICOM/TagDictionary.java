package org.example.InputPoint.DICOM;

import org.dcm4che3.data.VR;
import org.example.util.DICOMUtil;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagDictionary {

    static class TagInfo {
        String tagName;
        VR vr;
        String xsd_datatype;

        public TagInfo(String tagName, VR vr) {
            this.tagName = splitCamelCase(tagName);
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

    private HashMap<String, TagInfo> tagDictionary = new HashMap<>();

    public void put(String tagCode, String tagName, VR vr) {
        if (!tagDictionary.containsKey(tagCode))
            tagDictionary.put(tagCode, new TagInfo(tagName, vr));
    }

    public TagInfo getTagInfo(String tagCode) {
        return tagDictionary.get(tagCode);
    }

    public String getTagName(String tagCode) {
        return tagDictionary.get(tagCode).tagName;
    }
    public VR getVr(String tagCode) {
        return tagDictionary.get(tagCode).vr;
    }
    public String getXsd_datatype(String tagCode) {
        return tagDictionary.get(tagCode).xsd_datatype;
    }


}
