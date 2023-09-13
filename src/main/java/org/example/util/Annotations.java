package org.example.util;

import org.example.InputPoint.InputDataSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Annotations {


    public static final IRI rdfsLabelIRI = OWLRDFVocabulary.RDFS_LABEL.getIRI();
    public static final IRI skosIRI = IRI.create("http://www.w3.org/2004/02/skos/core#");
    public static final IRI skosAltLabel = IRI.create(skosIRI + "altLabel");
    public static final IRI skosPrefLabel = IRI.create(skosIRI + "prefLabel");


    public static String duplicateAttrClassName(String field) {
        return field + "_ATTR";
    }

    public static String pureObjPropName(String domain, String range) {
        return String.format("p_%s_%s", domain, range);
    }

    public static String getObjectPropertyRawLabel(String range) {
        return getObjectPropertyRawLabel(new HashSet<>(Collections.singleton(range)));
    }
    public static String getObjectPropertyRawLabel(Set<String> range) {
        return "has_" + range;
    }

    public static String symmetricObjPropName(String className) {
        return "has_" + className;
    }

    public static String inverseName(Set<String> range, Set<String> domain) {
        return String.format("p_%s_%s", normalise(range), normalise(domain)).replace(" ","_");
    }

    public static String getInverseName(String propName) {
        String[] parts = propName.split("_");
        return parts[0] + "_" + parts[2] + "_" + parts[1];
    }

    public static String attrObjectPropertyName(String attrClass) {
        return "has_"+attrClass;
    }

    public static String dataPropName(String attributeName) {
        return "has_"+attributeName+"_VALUE";
    }
    public static String directDataPropertyName(String attrClass) {
        return "has_"+attrClass;
    }


    //==================================================================================================================
    // acquire resource label

    public static String normalise(String s, boolean useDson) {
        if(useDson)
            return normaliseDSON(new HashSet<>(Collections.singleton(s)));
        else
            return normalise(new HashSet<>(Collections.singleton(s)));
    }

    public static String normalise(Set<String> s){
        String label =  s.toString()
                .replaceAll("[\\[\\],]","")
                .replaceAll("_", " ")
                .replace("p ", "")
                .replace(" VALUE", "")
                .replace(" ATTR", "");
        if(label.startsWith("has is"))
            label = label.substring(4);
        return label;
    }

    public static String normaliseDSON(Set<String> s){
        return s.toString()
                .replaceAll("[\\[\\]]","")
                .replaceAll("_", " ")
                .replace("p ", "")
                .replace(" VALUE", "")
                .replace(" ATTR", "")
                .replaceFirst("has ", "");
    }

    //==================================================================================================================
    public static final String invalidIRICharsRegex = "[/\\\\%# ]";

    public static String validName(String resourceName) {
        return rmvInvalidIriChars(resourceName);
    }
    public static String rmvInvalidIriChars(String resourceName) {
        return resourceName.replaceAll(invalidIRICharsRegex, "_");
    }

    //==================================================================================================================

    public static ArrayList<Pair<IRI, String>> getLabelSet(String rawLabel, DatasetDictionary datasetDictionary) {
        ArrayList<Pair<IRI, String>> labelSet = new ArrayList<>();
        boolean lookForAdditionalAnnots = datasetDictionary != null;

        if (lookForAdditionalAnnots) {
            try {
                /*                              DSON                              Med csv
                 * elementCodeLabel      tag code (GGGG.EEEE)         normalised header/header property -> rdfs:label
                 * elementCode           tag code (GGGG.EEEE)           raw header/property label       -> to look up dictionary
                 * elementName           tag name                           tokenized header            -> skos:prefLabel
                 * additional annots        -                           abbreviation expansions         -> skos:altLabel
                 */
                String elementCodeLabel = normalise(rawLabel, InputDataSource.isDSON());
                labelSet.add(new Pair<>(rdfsLabelIRI, elementCodeLabel));

                String elementCode = InputDataSource.isDSON() ? elementCodeLabel : rawLabel;
                String elementName = datasetDictionary.getElementName(elementCode);
                if(elementName != null)
                    labelSet.add(new Pair<>(skosPrefLabel, elementName));

                datasetDictionary.getAdditionalAnnotations(elementCode).forEach(annotation -> {
                    labelSet.add(new Pair<>(skosAltLabel, annotation));
                });

            }catch (NullPointerException e) {
                // in case the dictionary doesn't contain the element (for base elements)
                lookForAdditionalAnnots = false;
            }
        }
        if(!lookForAdditionalAnnots)
            labelSet.add(new Pair<>(rdfsLabelIRI, normalise(rawLabel, false)));

        return labelSet;
    }


}
