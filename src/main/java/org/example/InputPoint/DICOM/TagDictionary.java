package org.example.InputPoint.DICOM;

import org.dcm4che3.data.VR;

import java.util.HashMap;

public class TagDictionary {

    static class TagInfo {
        String tagName;
        VR vr;

        public TagInfo(String tagName, VR vr) {
            this.tagName = tagName;
            this.vr = vr;
        }
    }

    private HashMap<String, TagInfo> tagDictionary = new HashMap<>();

    public void put(String tagCode, String tagName, VR vr) {
        tagDictionary.put(tagCode, new TagInfo(tagName, vr));
    }
}
