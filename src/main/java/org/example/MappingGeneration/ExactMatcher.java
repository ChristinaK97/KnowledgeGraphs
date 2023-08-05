package org.example.MappingGeneration;

import org.apache.jena.ontology.OntResource;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.example.InputPoint.InputDataSource;
import org.example.MappingGeneration.FormatSpecific.DICOMspecificRules;
import org.example.MappingGeneration.FormatSpecific.FormatSpecificRules;
import org.example.MappingsFiles.SetMappingsFile;
import tech.tablesaw.api.Table;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;

import static org.example.MappingGeneration.Ontology.DATAPROPS;
import static org.example.MappingGeneration.Ontology.ONTELEMENTS;

public class ExactMatcher {


    private Ontology srcOnto;
    private Ontology trgOnto;
    private Matches matches = new Matches();
    private boolean removePunct = true;


    public ExactMatcher(String srcOntoPath, String trgOntoPath, ArrayList<String> annotationPropertiesIRIs) throws FileNotFoundException {

        srcOnto = new Ontology(srcOntoPath, annotationPropertiesIRIs, removePunct);
        trgOnto = new Ontology(trgOntoPath, annotationPropertiesIRIs, removePunct);

        for(int ONTELEMENT : ONTELEMENTS)
            match(ONTELEMENT);
        System.out.println("# total matches = " + matches.size());

        FormatSpecificRules spRules;
        if(InputDataSource.isDSON())
            spRules = new DICOMspecificRules();
        else
            spRules = null; //TODO add other types if this matcher is used for input data except dicom files

        spRules.addAdditionalMatches(srcOnto, trgOnto, matches);
        new SetMappingsFile(matches, spRules, getTableOntoEl());

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
                if (isExactMatch(srcR, srcAnnots, trgR, ONTELEMENT))
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

    private boolean isExactMatch(OntResource srcR, ArrayList<String> srcAnnots, OntResource trgR, int ONTELEMENT) {
        ArrayList<String> trgAnnots = srcOnto.getResourceAnnotations(trgR);
        double score = 0;
        for(String srcAnnot : srcAnnots) {
            for(String trgAnnot : trgAnnots) {

                if(srcAnnot.equals(trgAnnot)) {
                    score = 1;
                    if(ONTELEMENT == DATAPROPS && ! trgOnto.hasDomRan(trgR.asProperty()))
                        score = 0.5;
                    if(matches.getScore(srcR.getURI()) < score)
                        matches.addMatch(srcR.getURI(), trgR.getURI(), score);
                    return score == 1;
                }
        }}
        return score == 1;
    }

    private HashSet<String> getTableOntoEl() {
        HashSet<String> tableClassesURIs = new HashSet<>();
        String prefix = srcOnto.getBasePrefix();
        String queryString = Ontology.swPrefixes()
                + "SELECT ?tableURI WHERE {\n"
                + "     ?tableURI a owl:Class ; \n"
                + "               rdfs:subClassOf <" + prefix + "TableClass> . \n"
                + "}";
        Table table = srcOnto.runQuery(queryString, new String[]{"tableURI"});
        table.forEach(row -> tableClassesURIs.add(row.getString("tableURI")));
        return tableClassesURIs;
    }


    public static void main(String[] args) throws FileNotFoundException {
        new ExactMatcher("src/main/resources/POntology.ttl",
            "C:\\Users\\karal\\OneDrive\\Documents\\Σχολείο\\Σχολή\\Μεταπτυχιακό\\Project\\10. Health\\ontologies\\dicomOnto.ttl",
            null);
    }


}
