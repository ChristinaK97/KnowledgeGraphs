package org.example.A_Coordinator.Inputs;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class PreprocessingNotification {
    private String document_id;
    private String domain;
    private int file_size;
    private String filename;
    private String hash;
    private List<String> piis;
    private HashSet<String> tableNames = new HashSet<>();

    public PreprocessingNotification() {
        filename = ".";
        document_id = String.valueOf(new Random().nextInt());
        hash = String.valueOf(new Random().nextInt());
    }

// Getters and setters

    public String getDocument_id() {
        return document_id;
    }
    public void setDocument_id(String document_id) {
        this.document_id = document_id;
    }

    public String getDomain() {
        return domain;
    }
    public void setDomain(String domain) {
        this.domain = domain;
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
        return "PreprocessingNotification{" +
                "\n\tdocument_id='" + document_id + '\'' +
                "\n\tdomain='" + domain + '\'' +
                "\n\tfile_size=" + file_size +
                "\n\tfilename='" + filename + '\'' +
                "\n\thash='" + hash + '\'' +
                "\n\tpiis=" + piis +
                '}';
    }
}

