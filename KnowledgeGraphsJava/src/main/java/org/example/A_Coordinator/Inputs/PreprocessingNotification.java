package org.example.A_Coordinator.Inputs;

import java.util.List;

public class PreprocessingNotification {
    private String document_id;
    private String domain;
    private int file_size;
    private String filename;
    private String hash;
    private List<String> piis;

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

