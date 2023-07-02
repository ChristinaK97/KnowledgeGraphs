package org.example.InputPoint.DICOM;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import java.io.File;
import java.io.IOException;

public class DICOMreader {


    public static void main(String[] args) {
        String filePath = "src/main/resources/dicom/IM-0012-0030-0001.dcm";

        try (DicomInputStream dis = new DicomInputStream(new File(filePath))) {
            Attributes attributes = dis.readDataset();

            System.out.println(attributes);

            String patientWeight = attributes.getString(Tag.PatientWeight);
            System.out.println("Patient Weight: " + patientWeight);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
