package org.example.B_InputDatasetProcessing.DICOM;

import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.StandardElementDictionary;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DICOMUtil {


    public static String hasSOPClassUID = "http://semantic-dicom.org/rema#hasSOPClassUID";
    public static String SOPClassUIDtag = "(0008,0016)";
    public static String InformationObjectDefinition = "InformationObjectDefinition";
    public static String InformationEntity = "InformationEntity";
    public static String hasInformationEntity = "hasInformationEntity";
    public static String SequenceItemURI = "http://semantic-dicom.org/seq#SequenceItem";
    public static String hasItemURI = "http://semantic-dicom.org/seq#hasItem";

    /** Get tag name from dicom tag code, eg in="(0010,0010)" out="PatientName"
     * getNameFromCode("(0010,0010)") -> PatientName
     * @param tagCode: "(GGGG,EEEE)" format
     * @return the tag name in camel case. For Private tag codes, returns "Unknown Tag and Data"
     */
    public static String getNameFromCode(String tagCode) {
        tagCode = tagCode.replaceAll("[(,)]", "");
        int tag = TagUtils.forName(tagCode);
        if(tag != -1) {
            String tagName = ElementDictionary.keywordOf(tag, null);
            return tagName;
        }else
            return "Unknown Tag and Data";

    }

    /** Get the dicom tag code from the tag name
     * eg, in= "Patient Name" or "PatientName" out="(0010,0010)"
     * getCodeFromName("Patient Name") -> (0010,0010)
     * getCodeFromName("PatientName")  -> (0010,0010)
     * For private tags ("Unknown Tag and Data" returns the tagName back)
     */
    public static String getCodeFromName(String tagName) {
        if("Unknown Tag and Data".equals(tagName))
            return tagName;
        else if(tagName.contains(" ")) // is not camel case -> first turn to camel case
            return getCodeFromCamelCaseName(tagName.replaceAll(" ", ""));
        else
            return getCodeFromCamelCaseName(tagName);
    }

    /** Get the dicom tag code from the tag name in camel case
     * eg, in= "PatientName" out="(0010,0010)"
     * For private tags ("Unknown Tag and Data" returns the tagName back)
     */
    public static String getCodeFromCamelCaseName(String tagName) {
        int tag = StandardElementDictionary.INSTANCE.tagForKeyword(tagName);
        if (tag != -1) {
            //String tagCode = TagUtils.toHexString(tag); //"00100010" not formatted
            String formattedTag = TagUtils.toString(tag);
            return formattedTag;
        } else {
            return tagName;
        }
    }


    public static String parseForTime(String value, VR vr) {
        try {
            if (vr == null)
                return value;
            switch (vr) {
                case DA:    // Date
                    return convertToXSDDate(value);
                case DT:    // Date Time
                    return convertToXSDDatetime(value);
                case TM:    // Time
                    return convertToHHMMSS(value);
                default:
                    return value;
            }
        }catch (Exception e) {
            return value;
        }
    }

    public static String convertToXSDDate(String date) {
        // Parse DICOM date string to LocalDate
        DateTimeFormatter dicomFormatter = DateTimeFormatter.ofPattern("uuuuMMdd");
        LocalDate localDate = LocalDate.parse(date, dicomFormatter);

        // Format LocalDate to XSD date string
        DateTimeFormatter xsdFormatter = DateTimeFormatter.ISO_DATE;
        String xsdDate = localDate.format(xsdFormatter);
        return xsdDate;
    }
    public static String convertToXSDDatetime(String datetime) {
        // Parse DICOM datetime string to LocalDateTime
        DateTimeFormatter dicomFormatter = DateTimeFormatter.ofPattern("uuuuMMddHHmmss.SSSSSS");
        LocalDateTime localDateTime = LocalDateTime.parse(datetime, dicomFormatter);

        // Format LocalDateTime to XSD datetime string
        DateTimeFormatter xsdFormatter = DateTimeFormatter.ISO_DATE_TIME;
        String xsdDatetime = localDateTime.format(xsdFormatter);

        return xsdDatetime;
    }

    public static String convertToHHMMSS(String time) {
        // Remove trailing spaces
        time = time.trim();

        // Parse hours, minutes, and seconds
        int hours = Integer.parseInt(time.substring(0, 2));
        int minutes = Integer.parseInt(time.substring(2, 4));
        int seconds = Integer.parseInt(time.substring(4, 6));

        // Format as HH:MM:SS
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String replaceTagsWithNames(String input, TagDictionary tagDictionary) {
        Pattern pattern = Pattern.compile("\\(.*?\\)");
        Matcher matcher = pattern.matcher(input);

        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tagCode = matcher.group();
            String tagName = tagDictionary.getElementName(tagCode);
            matcher.appendReplacement(result, Matcher.quoteReplacement(tagName != null ? tagName : tagCode));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
