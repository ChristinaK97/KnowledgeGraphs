package org.example.B_InputDatasetProcessing.DICOM;

import static org.example.A_Coordinator.Pipeline.config;
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
import org.example.util.JsonUtil;
import org.example.util.Ontology;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.example.B_InputDatasetProcessing.DICOM.DICOMUtil.*;
import static org.example.util.FileHandler.getProcessedFilePath;
import static org.example.util.Ontology.getLocalName;

public class DICOM2SediJSON {

    private Ontology sedi;

    // for each hasInformationEntity of the InfoObjDefin class definition store the json table into the
    // map to be able to add additional attributes while iterating the file
    private HashMap<String, JsonObject> infoEntitiesDicts;

    // key = tagCode of tagName (if tagCode wasnt found and tagName was) value = the classes of the domain of the
    // property that correspondes to the tag
    private HashMap<String, HashSet<String>> cachedTagCodeDomain;

    // key = the SOPClassUID values, value = [The InfoObjDef class (OntoClass),
    //                                      the InformationEntities related to the InfoObjDef (HashSet<String>)]
    private HashMap<String, Object[]> cachedClassDefinition;
    private PrintWriter logger;
    private boolean log = false;


// ========================================================================================================
    private TagDictionary tagDictionary;
    private HashMap<String, JsonObject> dsonObjectsCollection;

    public TagDictionary getTagDictionary() {
        return tagDictionary;
    }
    public HashMap<String, JsonObject> getDsonObjectsCollection() {
        return dsonObjectsCollection;
    }

// ========================================================================================================


    public DICOM2SediJSON(ArrayList<String> dicomFilePaths){
        sedi = new Ontology(config.DOMap.TgtOntology);
        dsonObjectsCollection = new HashMap<>();
        tagDictionary = new TagDictionary();
        cachedTagCodeDomain = new HashMap<>();
        cachedClassDefinition = new HashMap<>();                                                                        if(log) try { logger = new PrintWriter(config.Out.LogDir + "dcmLog.txt"); } catch (FileNotFoundException e) {log=false;}
        parseDICOMcollection(dicomFilePaths);                                                                           if(log) logger.close();
        // the default root was DICOMObject. Set it as the root attribute of the DICOM Information Def Obj
        // container and then change the Default root to null to show that the dson has a well-defined root
        config.Out.RootClassName = config.In.DefaultRootClassName;
        config.In.DefaultRootClassName = null;
    }


    private void parseDICOMcollection(ArrayList<String> dicomFilePaths) {
        for (String dicomFilePath : dicomFilePaths) {                                                                   if(log) logger.println(dicomFilePath); //System.out.println(dicomFilePath);
            try (DicomInputStream dis = new DicomInputStream(new File(dicomFilePath))) {

                JsonObject dson;
                Attributes attributes = dis.readDataset();
                String processedDSONPath = getProcessedFilePath(dicomFilePath,
                        "json", true);

                if(!Files.exists(Paths.get(processedDSONPath))) {
                    // each dicom file is a different type of image related to specific info entities
                    infoEntitiesDicts = new HashMap<>();
                    dson = parseDICOMfile(attributes);
                    saveProcessedFile(dson, processedDSONPath);
                }else
                    dson = readProcessedDICOM(attributes, processedDSONPath);

                dsonObjectsCollection.put(processedDSONPath, dson);

            } catch (IOException e) {
                System.err.println("Unable to parse " + dicomFilePath); }
        }
    }



// ========================================================================================================
    /** Save the processed file/dson object */
    private void saveProcessedFile(JsonObject dson, String processedDSONPath) {
        JsonUtil.saveToJSONFile(processedDSONPath, dson);
    }

    // For saved processed dson files

    private JsonObject readProcessedDICOM (Attributes attributes, String processedFilePath) {
        JsonObject dson = JsonUtil.readJSON(processedFilePath).getAsJsonObject();
        gatherTagsToDictionary(attributes);
        return dson;
    }

    private void gatherTagsToDictionary(Attributes attributes) {
        for (int tag : attributes.tags()) {
            String tagCode = TagUtils.toString(tag);
            String tagName = ElementDictionary.keywordOf(tag, null);
            VR vr = attributes.getVR(tag);

            if (tagName.equals(""))
                tagName = "Unknown Tag and Data";
            tagDictionary.put(tagCode, tagName, vr);
            if(vr == VR.SQ)
                for (Attributes sqItem : attributes.getSequence(tag))
                    gatherTagsToDictionary(sqItem);
        }
    }

// ========================================================================================================

    // For new dcm files

    /** Retrieve the infoObjectDefinition class and associated info entities according to the value of the SOPClassUID */
    private JsonObject parseDICOMfile(Attributes attributes) {
        /* 1. a json dictionary for the attributes of each info entity
         * 2. add the generated dictionaries of each info entity to the parent infoObjectDefinition
         *    (infoObjectDefinition key is also in the dictionary)
         *    InfoObjectDefClass : {infoEntityURI=Patient : {entityJson}, Study : {}}
         * 3. {root=DICOMObject : container={InfoObjectDefClass : ....}}
         *    DICOMObject -[has InfoObjectDefClass] -> InfoObjectDefClass
         */
        String SOPClassUID = getSOPClassUID(attributes);
        Object[] defClassInfo = getDefinitionClass(SOPClassUID);
        OntClass defClass = (OntClass) defClassInfo[0];
        HashSet<String> infoEntities = (HashSet<String>) defClassInfo[1];

        // 1
        infoEntities.forEach(infoEntity -> infoEntitiesDicts.put(infoEntity, new JsonObject()));
        JsonObject infoObjectDef = infoEntitiesDicts.get(defClass.getURI());

        readAttributes(infoObjectDef, attributes, infoEntities, 0);
        // 2
        infoEntitiesDicts.forEach((infoEntityURI, entityJson) -> {
            if(!infoEntityURI.equals(defClass.toString())){
                infoObjectDef.add(getLocalName(infoEntityURI), entityJson);                                                                                   if(log) logger.println("ADD TO BASE " + getLocalName(infoEntityURI));
            }});

        // 3
        JsonObject container = new JsonObject();
        container.add(getLocalName(defClass), infoObjectDef);
        JsonObject dicom2json = new JsonObject();
        dicom2json.add(config.In.DefaultRootClassName, container);                                                                                                    if(log) logger.println(new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(dicom2json));
        return dicom2json;
    }

    private void readAttributes(JsonElement prevElem, Attributes attributes, HashSet<String> infoEntities, int depth) {
        /* 1. The info entity were the tag is assign to, eg for personName is person.
         *    Each tag has different info entity, but the info entity groups related tags
         * 2. private tag
         * 3. simple value
         *      3.1 if we are not inside a sequence, add the attribute to the info entity's dictionary
         *      3.2 if inside a sequence, attach to the parent node (some attributes have SequenceItem as domain)
         *          that we got from recursive call
         * 4. inside a nested sq
         *      4.1 parse attributes and or sqs of the current sq
         *      4.2 moved out from sq
         *
         */
        for (int tag : attributes.tags()) {
            String tagCode = TagUtils.toString(tag);
            String tagName = ElementDictionary.keywordOf(tag, null);
            VR vr = attributes.getVR(tag);
            String domain = getTagDomainIntersection(tagName, tagCode, infoEntities);  //1

            if(domain == null && tagName.equals("")) { //2
                domain = "#Unknown";
                tagName = "Unknown Tag and Data";
            }
            tagDictionary.put(tagCode, tagName, vr);                                                                                                        if(log) logger.println(depth + "\t" + tagCode + " " + tagName + " " + " " + (vr!=VR.SQ ? attributes.getString(tag) : "SQ") + "\t" + domain);

            if (vr != VR.SQ) {  //3
                String value = parseForTime(attributes.getString(tag), vr);
                if (depth == 0 && domain != null) { /*3.1*/
                    infoEntitiesDicts.get(domain).getAsJsonObject().addProperty(tagCode, value);                                                            if(log) logger.println("\t\tStuck on " + getLocalName(domain));
                }else{ /*3.2*/
                    prevElem.getAsJsonObject().addProperty(tagCode, value);                                                                                 if(log) logger.println("\t\tStuck on " + prevElem);
                }
            }else { //4
                ++depth;
                Sequence sq = attributes.getSequence(tag);    /*4.1*/                                                                                       if(log) logger.println("\t# items = " + sq.size() + " [");

                JsonElement valueElement = new JsonArray();
                for(Attributes sqItem : sq) {
                    JsonObject nestedObj = new JsonObject();
                    readAttributes(nestedObj, sqItem, infoEntities, depth);
                    valueElement.getAsJsonArray().add(nestedObj);
                }
                --depth;  //4.2

                if(depth == 0 && domain != null) {                                                                                                           if(log) logger.println("\t\tStuck on " + getLocalName(domain));
                    infoEntitiesDicts.get(domain).add(tagCode, valueElement);
                }else {                                                                                                                                      if(log) logger.println("\t\tStuck on " + prevElem);
                    prevElem.getAsJsonObject().add(tagCode, valueElement); }                                                                                 if(log) logger.println("]\n");
            }
        }
    }


    /** get the sop class uid value of the dicom file */
    private String getSOPClassUID(Attributes ats) {
        for(int tag : ats.tags())
            if(TagUtils.toString(tag).equals(SOPClassUIDtag))
                return ats.getString(tag);
        return null;
    }


    private Object[] getDefinitionClass(String SOPClassUID) {
        /* 1. retrieve the infoObjectDefinition class with sop class uid
         *    (annotation: "infoObjectDefinition hasSOPClassUID SOPClassUIDvalue")
         *    and a hash set of the associated info entities
         * 2. the info entities can be retrieve by the equivalence restriction of the info obj definition  class
         *    eqClass an intersection (and) of restrictions where onProperty=hasInfoEntity onClass=InfoEntity Class
         *    retrieve all the values of the onClass in the complex equivalence restriction
         * 3. the info obj def class included for attaching elements directly to the base image class (eg MRImage),
         *    #Unknown info entity to group the private tags
         *    3.1 cache to avoid running the queries for the same type of file repeatedly
         */
        // 1
        if(!cachedClassDefinition.containsKey(SOPClassUID)) {
            String query = Ontology.swPrefixes() +
                    "\nSELECT ?resource" +
                    "\n where {" +
                    "\n?resource <" + hasSOPClassUID + "> ?label . " +
                    "\nFILTER (str(?label) = '" + SOPClassUID + "')" +
                    "\n}";

            String defClassURI = sedi.runQuery(query, new String[]{"resource"})
                                     .stringColumn("resource").get(0);
            OntClass defClass = sedi.getOntClass(defClassURI);                                                                                                  if(log) logger.println(defClass);

            // 2
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
            // 3
            informationEntities.add(defClassURI);
            informationEntities.add("#Unknown");                                                                                                    if(log) informationEntities.forEach(logger::println);
            // 3.1
            cachedClassDefinition.put(SOPClassUID, new Object[]{defClass, informationEntities});
        }
         return cachedClassDefinition.get(SOPClassUID);

    }


    private String getTagDomainIntersection(String tagName, String tagCode, HashSet<String> infoEntities) {
        /* the tag domain is a union of many info entities and sequence items
         * given the info entities of the current image type, find the appropriate domain class
         * where the tag attribute will be attached.
         * all the domain classes in the union domain are cached to avoid running the queries for the same type of file repeatedly

         * in the simplest case the domain can be retrieved by having the tagCode property
         * but for some properties the tagCode is incorrect -> look for the union domain  based on the
         * equivalent property of the tagName
         * cache according to the property (code or name) that was used to retrieve the union domain
         */
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
        intersection.retainAll(infoEntities);                                                                                   if(log) logger.println("INTERSECTION " + intersection);
        if(intersection.size() > 0)
            return intersection.iterator().next();
        else
            return null;

    }

    /** Retrieve the union domain from the tagCode property (this is the one with the domain definition) */
    private HashSet<String> getFromCode(String tagCode) {
        /* 1. the tagCode property has the code (GGGG,EEEE) as rdfs:label
         * 2. domain is indeed a union of Info entities and or sequence items
         *      2.1 the classes in the union definition are added to the union domain set
         * 4. the domain is a single class -> set it as the union domain set
         */
        HashSet<String> newTagDomain = new HashSet<>();
        // 1
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
                if (sediProp.getDomain().canAs(UnionClass.class)) { // 2
                    UnionClass unionClass = domain.as(UnionClass.class);
                    for (OntClass operand : unionClass.listOperands().toList())  // 2.1
                        newTagDomain.add(operand.getURI());
                } else // 4
                    newTagDomain.add(domain.getURI());
            }
        } catch (Exception ignored) {}
        return newTagDomain;
    }


    /** retrieve union domain from the tagName property in case the search of the tagCode property based on the tagCode failed */
    private HashSet<String> getFromName(String tagName) {
        // The tagName property can be retrieved using the tagName
        // but the tagName property doesnt have domain definition
        // the tagName property is equivalent to the tagCode property that has the desired domain definition
        // so retrieve the tagCode property based on the equivalence definition and then retrieve the union
        // domain from the tagCode property
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


