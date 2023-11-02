package org.example.util;

import com.google.gson.JsonPrimitive;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.dcm4che3.data.VR;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;

public class XSDmappers {


    public static HashSet<Resource> decimalDatatypes = new HashSet<>(List.of(new Resource[]{XSD.xdouble, XSD.xfloat, XSD.decimal}));
    public static HashSet<Resource> intDatatypes     = new HashSet<>(List.of(new Resource[]{XSD.integer, XSD.unsignedInt, XSD.unsignedShort, XSD.xshort, XSD.positiveInteger, XSD.nonPositiveInteger, XSD.nonNegativeInteger}));
    public static HashSet<Resource> dateDatatypes    = new HashSet<>(List.of(new Resource[]{XSD.date, XSD.dateTime, XSD.dateTimeStamp, XSD.time}));

    public static String SQL2XSD(String sqlType) {
        switch (sqlType.toLowerCase()) {
            case "int":
            case "integer":
            case "tinyint":
            case "smallint":
            case "mediumint":
            case "bigint":
                return "xsd:integer";
            case "float":
            case "double":
            case "decimal":
            case "numeric":
                return "xsd:decimal";
            case "date":
                return "xsd:date";
            case "time":
                return "xsd:time";
            case "datetime":
            case "timestamp":
                return "xsd:dateTime";
            case "year":
                return "xsd:gYear";
            case "char":
            case "varchar":
            case "text":
            case "tinytext":
            case "mediumtext":
            case "longtext":
            case "string":
                return "xsd:string";
            case "binary":
            case "varbinary":
            case "blob":
            case "tinyblob":
            case "mediumblob":
            case "longblob":
                return "xsd:base64Binary";
            case "boolean":
            case "bit":
                return "xsd:boolean";
            default:
                return "unknown";
        }
    }


    public static String JSON2XSD(JsonPrimitive value) {
        if (value == null)
            return null;
        try {
            value.getAsInt();
            return "xsd:integer";
        }catch (NumberFormatException e1) {
            try {
                value.getAsFloat();
                return "xsd:decimal";
            }catch (NumberFormatException e2) {
                try {
                    value.getAsDouble();
                    return "xsd:decimal";
                }catch (NumberFormatException ignored) {
                    if(value.isBoolean())
                        return "xsd:boolean";
                    else if (value.isString())
                        return "xsd:string";
                }
            }
        }
        return null;
    }

    public static String DICOM2XSD(VR vr) {
        if (vr == null)
            return "xsd:string";

        switch (vr) {

            case AE:    // Application Entity
            case AS:    // Age String
            case AT:    // Attribute Tag
            case CS:    // Code String
            case DS:    // Decimal String
            case IS:    // Integer String
            case LO:    // Long String
            case LT:    // Long Text
            case PN:    // Person Name
            case SH:    // Short String
            case ST:    // Short Text
            case UI:    // Unique Identifier

            case OB:    // Other Byte String
            case OW:    // Other Word String
            case OF:    // Other Float String
            case SQ:    // Sequence
            case UT:    // Unlimited Text
            case UN:    // Unknown
                return "xsd:string";

            case FL:    // Floating Point Single
                return "xsd:float";
            case FD:    // Floating Point Double
                return "xsd:double";

            case SL:    // Signed Long
                return "xsd:long";
            case UL:    // Unsigned Long
                return "xsd:unsignedLong";

            case SS:    // Signed Short
                return "xsd:short";
            case US:    // Unsigned Short
                return "xsd:unsignedShort";

            case DA:    // Date
                return "xsd:date";
            case DT:    // Date Time
                return "xsd:dateTime";
            case TM:    // Time
                return "xsd:time";
            default:
                return "xsd:string";
        }
    }


    public static String fixDateFormat(String columnValue, String inputFormat) {
        // System.out.println("\tFIX DATE FORMAT");
        try {
            SimpleDateFormat originalDateFormat = new SimpleDateFormat(inputFormat);
            SimpleDateFormat targetDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            java.util.Date date = originalDateFormat.parse(columnValue);
            return targetDateFormat.format(date);
        } catch (ParseException e) {
            return (columnValue + ".01").replaceAll("\\.", "-");
        }
    }
}
