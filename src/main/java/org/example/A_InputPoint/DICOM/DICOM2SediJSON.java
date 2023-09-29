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

    Ontology sedi;

    // for each hasInformationEntity of the InfoObjDefin class definition store the json table into the
    // map to be able to add additional attributes while iterating the file
    HashMap<String, JsonObject> infoEntitiesDicts;

    // key = tagCode of tagName (if tagCode wasnt found and tagName was) value = the classes of the domain of the
    // property that correspondes to the tag
    HashMap<String, HashSet<String>> cachedTagCodeDomain;

    // key = the SOPClassUID values, value = [The InfoObjDef class (OntoClass),
    //                                      the InformationEntities related to the InfoObjDef (HashSet<String>)]
    HashMap<String, Object[]> cachedClassDefinition;
    PrintWriter log;


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
            // each dicom file is a different type of image related to specific info entities
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
        // retrieve the infoObjectDefinition class and associated info entities according to the value of the SOPClassUID
        String SOPClassUID = getSOPClassUID(attributes);
        Object[] defClassInfo = getDefinitionClass(SOPClassUID);
        OntClass defClass = (OntClass) defClassInfo[0];
        HashSet<String> infoEntities = (HashSet<String>) defClassInfo[1];

        // a json dictionary for the attributes of each info entity
        infoEntities.forEach(infoEntity -> infoEntitiesDicts.put(infoEntity, new JsonObject()));
        JsonObject infoObjectDef = infoEntitiesDicts.get(defClass.getURI());

        readAttributes(infoObjectDef, attributes, infoEntities, 0);
        // add the generated dictionaries of each info entity to the parent infoObjectDefinition (infoObjectDefinition key is also in the dictionary)
        // InfoObjectDefClass : {infoEntityURI=Patient : {entityJson}, Study : {}}
        infoEntitiesDicts.forEach((infoEntityURI, entityJson) -> {
            if(!infoEntityURI.equals(defClass.toString())){
                infoObjectDef.add(getLocalName(infoEntityURI), entityJson);
                log.println("ADD TO BASE " + getLocalName(infoEntityURI));
            }});

        // {root=DICOMObject : container={InfoObjectDefClass : ....}}
        // DICOMObject -[has InfoObjectDefClass] -> InfoObjectDefClass
        JsonObject container = new JsonObject();
        container.add(getLocalName(defClass), infoObjectDef);
        JsonObject dicom2json = new JsonObject();
        dicom2json.add("DICOMObject", container);

        log.println(new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(dicom2json));
        return dicom2json;
    }

    private void readAttributes(JsonElement prevElem, Attributes attributes, HashSet<String> infoEntities, int depth) {
        for (int tag : attributes.tags()) {   //1
            String tagCode = TagUtils.toString(tag);  // 2
            String tagName = ElementDictionary.keywordOf(tag, null);
            VR vr = attributes.getVR(tag);
            // the info entity were the tag is assign to, eg for personName is person. Each tag has different info entity, but the info entity groups related tags
            String domain = getTagDomainIntersection(tagName, tagCode, infoEntities);

            if(domain == null && tagName.equals("")) {
                // private tag
                domain = "#Unknown";
                tagName = "Unknown Tag and Data";
            }
            tagDictionary.put(tagCode, tagName, vr);

            log.println(depth + "\t" + tagCode + " " + tagName + " " + " " + (vr!=VR.SQ ? attributes.getString(tag) : "SQ") + "\t" + domain);

            if (vr != VR.SQ) {
                // simple value
                String value = parseForTime(attributes.getString(tag), vr);
                if (depth == 0 && domain != null) {
                    // if we are not inside a sequence, add the attribute to the info entity's dictionary
                    infoEntitiesDicts.get(domain).getAsJsonObject().addProperty(tagCode, value);
                    log.println("\t\tStuck on " + getLocalName(domain));
                }else{
                    // if inside a sequence, attach to the parent node (some attributes have SequenceItem as domain) that we got from recursive call
                    prevElem.getAsJsonObject().addProperty(tagCode, value);
                    log.println("\t\tStuck on " + prevElem);
                }
            }else {
                // inside a nested sq
                ++depth;
                // parse attributes and or sqs of the current sq
                Sequence sq = attributes.getSequence(tag);
                log.println("\t# items = " + sq.size() + " [");

                JsonElement valueElement = new JsonArray();
                for(Attributes sqItem : sq) {
                    JsonObject nestedObj = new JsonObject();
                    readAttributes(nestedObj, sqItem, infoEntities, depth);
                    valueElement.getAsJsonArray().add(nestedObj);
                }
                // moved out from sq
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
        // get the sop class uid value of the dicom file
        for(int tag : ats.tags())
            if(TagUtils.toString(tag).equals(DICOMUtil.SOPClassUIDtag))
                return ats.getString(tag);
        return null;
    }


    private Object[] getDefinitionClass(String SOPClassUID) {
        // retrieve the infoObjectDefinition class with sop class uid (annotation: "infoObjectDefinition hasSOPClassUID SOPClassUIDvalue")
        // and a hash set of the associated info entities

        if(!cachedClassDefinition.containsKey(SOPClassUID)) {
            String query = Ontology.swPrefixes() +
                    "\nSELECT ?resource" +
                    "\n where {" +
                    "\n?resource <" + DICOMUtil.hasSOPClassUID + "> ?label . " +
                    "\nFILTER (str(?label) = '" + SOPClassUID + "')" +
                    "\n}";

            Table result = sedi.runQuery(query, new String[]{"resource"});
            String defClassURI = result.stringColumn("resource").get(0);
            OntClass defClass = sedi.getOntClass(defClassURI);
            log.println(defClass);

            // the info entities can be retrieve by the equivalence restriction of the info obj definition  class
            // eqClass an intersection (and) of restrictions where onProperty=hasInfoEntity onClass=InfoEntity Class
            // retrieve all the values of the onClass in the complex equivalence restriction
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
                operands.close();
            }
            // the info obj def class included for attaching elements directly to the base image class (eg MRImage),
            // #Unknown info entity to group the private tags
            informationEntities.add(defClassURI);
            informationEntities.add("#Unknown");
            informationEntities.forEach(log::println);
            // cache to avoid running the queries for the same type of file repeatedly
            cachedClassDefinition.put(SOPClassUID, new Object[]{defClass, informationEntities});
        }
         return cachedClassDefinition.get(SOPClassUID);

    }


    private String getTagDomainIntersection(String tagName, String tagCode, HashSet<String> infoEntities) {
        // the tag domain is a union of many info entities and sequence items
        // given the info entities of the current image type, find the appropriate domain class
        // where the tag attribute will be attached.
        // all the domain classes in the union domain are cached to avoid running the queries for the same type of file repeatedly

        // in the simplest case the domain can be retrieved by having the tagCode property
        // but for some properties the tagCode is incorrect -> look for the union domain  based on the
        // equivalent property of the tagName
        // cache according to the property (code or name) that was used to retrieve the union domain
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

        // the set of info entities of the infoObjDef is intersected with the union domain to
        // get the appropriate class where the tag attribute will be attached
        // eg info entity={ImageMRImage} union domain = {ImageMRImage, ImageCTImage} intersection={ImageMRImage}<-
        Set<String> intersection = new HashSet<>(cachedTagCodeDomain.get(searchElement));
        intersection.retainAll(infoEntities);
        log.println("INTERSECTION " + intersection);
        if(intersection.size() > 0)
            return intersection.iterator().next();
        else
            return null;

    }

    private HashSet<String> getFromCode(String tagCode) {
        // retrieve the union domain from the tagCode property (this is the one with the domain definition)
        HashSet<String> newTagDomain = new HashSet<>();

        // the tagCode property has the code (GGGG,EEEE) as rdfs:label
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
                if (sediProp.getDomain().canAs(UnionClass.class)) { // domain is indeed a union of Info entities and or sequence items
                    UnionClass unionClass = domain.as(UnionClass.class);
                    for (OntClass operand : unionClass.listOperands().toList())
                        // the classes in the union definition are added to the union domain set
                        newTagDomain.add(operand.getURI());
                } else
                    // the domain is a single class -> set it as the union domain set
                    newTagDomain.add(domain.getURI());
            }
        } catch (Exception ignored) {}
        return newTagDomain;
    }


    private HashSet<String> getFromName(String tagName) {
        // retrieve union domain from the tagName property in case the search of the tagCode property based on the tagCode
        // failed. The tagName property can be retrieved using the tagName
        // but the tagName property doesnt have domain definition
        // the tagName property is equivalent to the tagCode property that has the desired domain definition
        // so retrieve the tagCode property based on the equivalence definition and then retrieve the union domain from the tagCode property
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


