package org.example.InputPoint.DICOM;

import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;

import java.io.File;
import java.io.IOException;

public class DICOMreader {

    public static void readAttributes(Attributes attributes, boolean isSQ) {
        int[] tags = attributes.tags();

        if(isSQ)
            System.out.println("\t>> Item");

        for (int tag : tags) {
            String value = attributes.getString(tag);
            String tagCode = TagUtils.toString(tag);
            String tagName = ElementDictionary.keywordOf(tag, null);

            VR vr = attributes.getVR(tag);

            System.out.println((isSQ ? "\t " : "") + tagCode + "\t" + tagName + "\t" + vr + "\t" + value);

            if(vr == VR.SQ) {
                Sequence sq = attributes.getSequence(tag);
                System.out.println("\t# items = " + sq.size() + " [");
                for(Attributes sqItem : sq)
                    readAttributes(sqItem, true);
                System.out.println("]");
            }
        }
    }


    public static void main(String[] args) {
        String filePath = "src/main/resources/dicom/IM-0012-0030-0001.dcm";

        try (DicomInputStream dis = new DicomInputStream(new File(filePath))) {
            Attributes attributes = dis.readDataset();
            readAttributes(attributes, false);
            // System.out.println(attributes);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
