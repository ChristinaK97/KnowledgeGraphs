package org.example.B_InputDatasetProcessing.DICOM;

import org.dcm4che3.data.VR;

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
