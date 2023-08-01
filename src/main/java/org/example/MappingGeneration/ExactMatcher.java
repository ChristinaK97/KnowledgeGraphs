package org.example.MappingGeneration;

import org.apache.jena.ontology.OntResource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.example.MappingGeneration.FormatSpecific.DICOMrules;
import org.example.MappingsFiles.SaveMappings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.MappingGeneration.Ontology.ONTELEMENTS;

public class ExactMatcher {


    private Ontology srcOnto;
    private Ontology trgOnto;
    private Matches matches = new Matches();
    private boolean removePunct = true;


    public ExactMatcher(String srcOntoPath, String trgOntoPath, ArrayList<String> annotationPropertiesIRIs) {
        srcOnto = new Ontology(srcOntoPath, annotationPropertiesIRIs, removePunct);
        trgOnto = new Ontology(trgOntoPath, annotationPropertiesIRIs, removePunct);

        for(int ONTELEMENT : ONTELEMENTS)
            match(ONTELEMENT);
        System.out.println("# total matches = " + matches.size());
        new DICOMrules().addAdditionalMatches(srcOnto, trgOnto, matches);
        new SaveMappings(matches);
        srcOnto.close();
    }

    public void match(int ONTELEMENT) {
        int prevNumOfMatches = matches.size();
        AtomicInteger srcSize = new AtomicInteger();
        ExtendedIterator<? extends OntResource> srcRs = srcOnto.listResources(ONTELEMENT);
        List<? extends OntResource> trgRs = trgOnto.listResources(ONTELEMENT).toList();

        srcRs.forEachRemaining(srcR -> {
            srcSize.incrementAndGet();
            ArrayList<String> srcAnnots = srcOnto.getResourceAnnotations(srcR);
            for(OntResource trgR : trgRs) {
                if (isExactMatch(srcR, srcAnnots, trgR))
                    break;
            }
        });
        srcOnto.resetCached();
        trgOnto.resetCached();

        System.out.println("Ontology element code = " + ONTELEMENT);
        System.out.println("# Src ontology = " + srcSize.get());
        System.out.println("# Trg ontology = " + trgRs.size());
        System.out.println("# new matches = " + (matches.size() - prevNumOfMatches));

    }

    private boolean isExactMatch(OntResource srcR, ArrayList<String> srcAnnots, OntResource trgR) {
        ArrayList<String> trgAnnots = srcOnto.getResourceAnnotations(trgR);

        for(String srcAnnot : srcAnnots) {
            for(String trgAnnot : trgAnnots) {
                if(srcAnnot.equals(trgAnnot)) {
                    matches.addMatch(srcR.getURI(), trgR.getURI(), 1);
                    return true;
                }
        }}
        return false;
    }


    public static void main(String[] args) {
        new ExactMatcher("src/main/resources/POntology.ttl",
            "C:\\Users\\karal\\OneDrive\\Documents\\Σχολείο\\Σχολή\\Μεταπτυχιακό\\Project\\10. Health\\ontologies\\dicomOnto.ttl",
            null);
    }


}
