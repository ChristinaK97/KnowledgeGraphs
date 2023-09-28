package org.example.A_InputPoint.DICOM;

import com.google.gson.*;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.ontology.UnionClass;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.example.A_InputPoint.InputDataSource;
import org.example.A_InputPoint.JsonUtil;
import org.example.util.Ontology;
import tech.tablesaw.api.Table;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.example.A_InputPoint.DICOM.DICOMUtil.parseForTime;
import static org.example.util.Ontology.getLocalName;

public class DICOM2SediJSON {

    String hasSOPClassUID = "http://semantic-dicom.org/rema#hasSOPClassUID";
    Ontology sedi;
    HashMap<String, JsonObject> infoEntitiesDicts;
    HashMap<String, HashSet<String>> cachedTagCodeDomain;
    HashMap<String, Object[]> cachedClassDefinition;
    PrintWriter log;

    boolean p = true;

    private HashMap<String, JsonObject> dson;
    private TagDictionary tagDictionary;


    public HashMap<String, JsonObject> getDson () {
        return dson;
    }

    public ArrayList<JsonObject> getDsonAsList() {
        return new ArrayList<>(dson.values());
    }

    public TagDictionary getTagDictionary() {
        return tagDictionary;
    }


    public DICOM2SediJSON(ArrayList<String> dicomFilePaths, boolean saveDsonFiles){
        sedi = new Ontology(InputDataSource.DOontology);
        dson = new HashMap<>();
        tagDictionary = new TagDictionary();
        cachedTagCodeDomain = new HashMap<>();
        cachedClassDefinition = new HashMap<>();
        try {
            log = new PrintWriter("log.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        parseDICOMcollection(dicomFilePaths, saveDsonFiles);
        log.close();
    }

    private void parseDICOMcollection(ArrayList<String> dicomFilePaths, boolean saveDsonFiles) {
        for (String dicomFilePath : dicomFilePaths) {
            infoEntitiesDicts = new HashMap<>();
            log.println(dicomFilePath);
            //System.out.println(dicomFilePath);
            try (DicomInputStream dis = new DicomInputStream(new File(dicomFilePath))) {
                Attributes attributes = dis.readDataset();
                JsonObject dicom2json = parseDICOMfile(attributes);

                String jsonPath = dicomFilePath.substring(0, dicomFilePath.lastIndexOf(".")) + ".json";
                if(saveDsonFiles)
                    JsonUtil.saveToJSONFile(jsonPath, dicom2json);
                dson.put(dicomFilePath.substring(dicomFilePath.lastIndexOf("/")+1), dicom2json);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private JsonObject parseDICOMfile(Attributes attributes) {
        String SOPClassUID = getSOPClassUID(attributes);
        Object[] defClassInfo = getDefinitionClass(SOPClassUID);
        OntClass defClass = (OntClass) defClassInfo[0];
        HashSet<String> infoEntities = (HashSet<String>) defClassInfo[1];

        infoEntities.forEach(infoEntity -> infoEntitiesDicts.put(infoEntity, new JsonObject()));
        JsonObject dicom2json = new JsonObject();
        JsonObject infoObjectDef = infoEntitiesDicts.get(defClass.getURI());

        readAttributes(infoObjectDef, attributes, infoEntities, 0);
        infoEntitiesDicts.forEach((infoEntityURI, entityJson) -> {
            if(!infoEntityURI.equals(defClass.toString())){
                infoObjectDef.add(getLocalName(infoEntityURI), entityJson);
                log.println("ADD TO BASE " + getLocalName(infoEntityURI));
            }});
        JsonObject container = new JsonObject();
        container.add(getLocalName(defClass), infoObjectDef);
        dicom2json.add("DICOMObject", container);

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        String prettyJson = gson.toJson(dicom2json);
        log.println(prettyJson);
        return dicom2json;
    }

    private void readAttributes(JsonElement prevElem, Attributes attributes, HashSet<String> infoEntities, int depth) {
        for (int tag : attributes.tags()) {   //1
            String tagCode = TagUtils.toString(tag);  // 2
            String tagName = ElementDictionary.keywordOf(tag, null);
            VR vr = attributes.getVR(tag);
            String domain = getTagDomainIntersection(tagName, tagCode, infoEntities);

            if(domain == null && tagName.equals("")) {
                domain = "#Unknown";
                tagName = "Unknown Tag and Data";
            }
            tagDictionary.put(tagCode, tagName, vr);

            log.println(depth + "\t" + tagCode + " " + tagName + " " + " " + (vr!=VR.SQ ? attributes.getString(tag) : "SQ") + "\t" + domain);

            if (vr != VR.SQ) {
                String value = parseForTime(attributes.getString(tag), vr);
                if (depth == 0 && domain != null) {
                    infoEntitiesDicts.get(domain).getAsJsonObject().addProperty(tagCode, value);
                    log.println("\t\tStuck on " + getLocalName(domain));
                }else{
                    prevElem.getAsJsonObject().addProperty(tagCode, value);
                    log.println("\t\tStuck on " + prevElem);
                }
            }else {
                ++depth;
                Sequence sq = attributes.getSequence(tag);
                log.println("\t# items = " + sq.size() + " [");

                JsonElement valueElement = new JsonArray();

                for(Attributes sqItem : sq) {
                    JsonObject nestedObj = new JsonObject();
                    readAttributes(nestedObj, sqItem, infoEntities, depth);
                    valueElement.getAsJsonArray().add(nestedObj);
                }
                --depth;

                if(depth == 0 && domain != null) {
                    log.println("\t\tStuck on " + getLocalName(domain));
                    infoEntitiesDicts.get(domain).add(tagCode, valueElement);
                }else {
                    log.println("\t\tStuck on " + prevElem);
                    prevElem.getAsJsonObject().add(tagCode, valueElement);
                }
                log.println("]\n");
            }
        }
    }


    private String getSOPClassUID(Attributes ats) {
        for(int tag : ats.tags())
            if(TagUtils.toString(tag).equals("(0008,0016)"))
                return ats.getString(tag);
        return null;
    }


    private Object[] getDefinitionClass(String SOPClassUID) {

        if(!cachedClassDefinition.containsKey(SOPClassUID)) {
            String query = Ontology.swPrefixes() +
                    "\nSELECT ?resource" +
                    "\n where {" +
                    "\n?resource <" + hasSOPClassUID + "> ?label . " +
                    "\nFILTER (str(?label) = '" + SOPClassUID + "')" +
                    "\n}";

            Table result = sedi.runQuery(query, new String[]{"resource"});
            String defClassURI = result.stringColumn("resource").get(0);
            OntClass defClass = sedi.getOntClass(defClassURI);
            log.println(defClass);
            OntClass restriction = defClass.getEquivalentClass();
            HashSet<String> informationEntities = new HashSet<>();
            if (restriction != null && restriction.isIntersectionClass()) {
                ExtendedIterator<? extends OntClass> operands = restriction.asIntersectionClass().listOperands();
                while (operands.hasNext()) {
                    OntClass operand = operands.next();
                    RDFNode onClassValue = operand.asRestriction().getPropertyResourceValue(sedi.getOntProperty(OWL.NS + "onClass"));
                    if (onClassValue != null && onClassValue.isResource())
                        informationEntities.add(onClassValue.as(OntClass.class).getURI());
                }
            }
            informationEntities.add(defClassURI);
            informationEntities.add("#Unknown");
            informationEntities.forEach(log::println);
            cachedClassDefinition.put(SOPClassUID, new Object[]{defClass, informationEntities});
        }
         return cachedClassDefinition.get(SOPClassUID);

    }


    private String getTagDomainIntersection(String tagName, String tagCode, HashSet<String> infoEntities) {
        String searchElement = tagCode;
        if(!cachedTagCodeDomain.containsKey(tagCode) && !cachedTagCodeDomain.containsKey(tagName)) {
            HashSet<String> newTagDomain = getFromCode(tagCode);
            if (newTagDomain.size() == 0) {
                newTagDomain = getFromName(tagName);
                if (newTagDomain.size() > 0) {
                    searchElement = tagName;
                }
            }
            cachedTagCodeDomain.put(searchElement, newTagDomain);
        }else if (cachedTagCodeDomain.containsKey(tagCode)) {
            searchElement = tagCode;
        } else if (cachedTagCodeDomain.containsKey(tagName)) {
            searchElement = tagName;
        }

        Set<String> intersection = new HashSet<>(cachedTagCodeDomain.get(searchElement));
        intersection.retainAll(infoEntities);
        log.println("INTERSECTION " + intersection);
        if(intersection.size() > 0)
            return intersection.iterator().next();
        else
            return null;

    }

    private HashSet<String> getFromCode(String tagCode) {
        HashSet<String> newTagDomain = new HashSet<>();

        String query = Ontology.swPrefixes() +
                "\nSELECT ?resource" +
                "\n where {" +
                "\n?resource rdfs:label ?label . " +
                "\nFILTER (str(?label) = '" + tagCode + "')" +
                "\n}";
        try {
            OntProperty sediProp = sedi.getOntProperty(
                    sedi.runQuery(query, new String[]{"resource"}).stringColumn("resource").get(0));
            if (sediProp != null) {
                OntResource domain = sediProp.getDomain();
                if (sediProp.getDomain().canAs(UnionClass.class)) {
                    UnionClass unionClass = domain.as(UnionClass.class);
                    for (OntClass operand : unionClass.listOperands().toList())
                        newTagDomain.add(operand.getURI());
                } else
                    newTagDomain.add(domain.getURI());
            }
        } catch (Exception ignored) {}
        return newTagDomain;
    }


    private HashSet<String> getFromName(String tagName) {
        try {
            OntProperty tagNameProp = sedi.getOntProperty("http://semantic-dicom.org/dcm#" + tagName);
            String query = Ontology.swPrefixes() + "\n" +
                    "select ?tagCode where { \n" +
                    "?equiv rdfs:domain ?d .\n" +
                    "?equiv rdfs:label ?tagCode .\n"+
                    "?equiv owl:equivalentProperty <" + tagNameProp.getURI() + "> .}\n";
            String tagCode = sedi.runQuery(query, new String[]{"tagCode"}).stringColumn("tagCode").get(0);
            return getFromCode(tagCode);
        }catch (NullPointerException e) {
            return new HashSet<>();
        }
    }


}


