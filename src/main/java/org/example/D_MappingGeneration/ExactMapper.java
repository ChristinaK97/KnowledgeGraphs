package org.example.D_MappingGeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.util.iterator.ExtendedIterator;

import static org.example.A_Coordinator.Runner.config;
import org.example.D_MappingGeneration.FormatSpecific.DICOMspecificRules;
import org.example.D_MappingGeneration.FormatSpecific.FormatSpecificRules;
import org.example.MappingsFiles.SetMappingsFile;
import org.example.util.Ontology;
import tech.tablesaw.api.Table;

import static org.example.util.Annotations.TABLE_CLASS;
import static org.example.util.Ontology.DATAPROPS;
import static org.example.util.Ontology.ONTELEMENTS;

public class ExactMapper {


    private Ontology srcOnto;
    private Ontology trgOnto;
    private Matches matches = new Matches();
    private boolean removePunct = true;


    public ExactMapper(ArrayList<String> annotationPropertiesIRIs) {

        srcOnto = new Ontology(config.Out.POntology);
        srcOnto.findAnnotationProperties(annotationPropertiesIRIs, removePunct);
        trgOnto = new Ontology(config.Out.DOntology);
        trgOnto.findAnnotationProperties(annotationPropertiesIRIs, removePunct);

        for(int ONTELEMENT : ONTELEMENTS)
            match(ONTELEMENT);

        FormatSpecificRules spRules;                                                                                                    System.out.println("# total matches = " + matches.size());
        if(config.In.isDSON())
            spRules = new DICOMspecificRules();
        else
            spRules = null; //TODO add other types if this matcher is used for input data except dicom files

        spRules.addAdditionalMatches(srcOnto, trgOnto, matches);
        new SetMappingsFile(matches, spRules /*,getTableOntoEl()*/);
        srcOnto.saveChanges();
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
        trgOnto.resetCached();                                                                                                      System.out.printf("Ontology element code = %s\n# Src ontology = %d\n# Trg ontology = %d\n# new matches = %d\n", ONTELEMENT,srcSize.get(),trgRs.size(), (matches.size() - prevNumOfMatches));
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

    private Set<String> getTableOntoEl() {
        String prefix = srcOnto.getBasePrefix();
        String queryString = Ontology.swPrefixes()
                + "SELECT ?tableURI WHERE {\n"
                + "     ?tableURI a owl:Class ; \n"
                + "               rdfs:subClassOf <" + prefix + TABLE_CLASS + "> . \n"
                + "}";
        return srcOnto.runQuery(queryString, new String[]{"tableURI"})
                      .stringColumn("tableURI").asSet();
    }




}
