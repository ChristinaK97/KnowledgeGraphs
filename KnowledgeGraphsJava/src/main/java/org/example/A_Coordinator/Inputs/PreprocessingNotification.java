package org.example.A_Coordinator.Inputs;

import org.example.B_InputDatasetProcessing.DICOM.DICOMUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.example.B_InputDatasetProcessing.DICOM.DICOMUtil.getCodeFromName;

public class PreprocessingNotification {

    // private String document_id; //removed by preprocessing
    private String filename;
    private int file_size;
    private String owner;
    private boolean preprocessed;
    private String domain;
    private String hash;
    private List<String> piis;
    // TODO: metadata_id
    private String metadata_id;

    // for KGs internal use
    private boolean isReceivedFromPreprocessing;
    private HashSet<String> tableNames = new HashSet<>();

    public PreprocessingNotification() {
        this.isReceivedFromPreprocessing = false;
        filename = ".";
        //document_id = String.valueOf(new Random().nextInt());
        owner = "-";
        preprocessed = false;
        hash = String.valueOf(new Random().nextInt());
        metadata_id = String.valueOf(new Random().nextInt());
    }

// Getters and setters


    public boolean isReceivedFromPreprocessing() {
        return isReceivedFromPreprocessing;
    }
    public void setReceivedFromPreprocessing(boolean receivedFromPreprocessing) {
        isReceivedFromPreprocessing = receivedFromPreprocessing;
    }

    /*public String getDocument_id() {
        return document_id;
    }
    public void setDocument_id(String document_id) {
        this.document_id = document_id;
    }*/

    public String getDomain() {
        return domain;
    }
    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getMetadata_id() {
        return metadata_id;
    }
    public void setMetadata_id(String metadata_id) {
        this.metadata_id = metadata_id;
    }

    public int getFile_size() {
        return file_size;
    }
    public void setFile_size(int file_size) {
        this.file_size = file_size;
    }

    public String getFilename() {
        return filename;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getHash() {
        return hash;
    }
    public void setHash(String hash) {
        this.hash = hash;
    }

    public List<String> getPiis() {
        return piis;
    }
    public void setPiis(List<String> piis) {
        this.piis = piis;
    }

    /** DICOM input: assuming input from preprocessing for dicom files is piis = [List of tag names] not tag codes */
    public void turnPiiTagNamesToCodes() {
        // piis[i] <- getCodeFromName(piis[i])
        piis.replaceAll(DICOMUtil::getCodeFromName);
    }


    public void addExtractedTableName(String extractedTableName) {
        tableNames.add(extractedTableName);
    }
    public boolean hasExtractedTable(String extractedTableName) {
        return tableNames.contains(extractedTableName);
    }
    public HashSet<String> getExtractedTableNames() {
        return tableNames;
    }

    public boolean isPii(String colName) {
        for(String pii : piis)
            if(colName.endsWith(pii))
                return true;
        return false;
    }

    @Override
    public String toString() {
        return "PreprocessingNotification {" +
                "\n\tfilename='" + filename + '\'' +
                ",\n\tfile_size=" + file_size +
                ",\n\towner='" + owner + '\'' +
                ",\n\tpreprocessed=" + preprocessed +
                ",\n\tdomain='" + domain + '\'' +
                ",\n\thash='" + hash + '\'' +
                ",\n\tpiis=" + piis +
                ",\n\tmetadata_id='" + metadata_id + '\'' +
                "\n}";
    }
}

