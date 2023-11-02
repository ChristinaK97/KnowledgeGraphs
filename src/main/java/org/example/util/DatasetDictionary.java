package org.example.util;
import java.util.ArrayList;
import java.util.HashMap;

public abstract class DatasetDictionary {

    public abstract class DatasetElementInfo {
        protected String elementName = null;

        public String getElementName() {
            return elementName;
        }
    }

    protected HashMap<String, DatasetElementInfo> datasetDictionary = new HashMap<>();

    public DatasetElementInfo getElementInfo(String elementCode) {
        return datasetDictionary.get(elementCode);
    }

    public String getElementName(String elementCode) {
        return datasetDictionary.get(elementCode).getElementName();
    }

    public ArrayList<String> getAdditionalAnnotations(String elementCode) {
        return new ArrayList<>(0);
    }
}
