package org.example.C_MappingGeneration.MappingSelection;

import com.google.common.primitives.Ints;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.ontology.UnionClass;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.example.A_InputPoint.InputDataSource;
import org.example.MappingsFiles.MappingsFileTemplate;
import org.example.MappingsFiles.MappingsFileTemplate.Column;
import org.example.util.Ontology;
import org.example.util.Pair;
import tech.tablesaw.api.*;
import org.example.A_InputPoint.JsonUtil;
import java.util.*;

import static org.example.MappingsFiles.ManageMappingsFile.readMapJSON;
import static org.example.util.Ontology.getLocalName;

public class MappingSelection {

//======================================================================================================================
    static String TGTCand = "TGTCand";
    static String BES     = "BES";
    static String BESRank = "BESRank";
    static String PJRank  = "PJRank";
    static String PJ      = "PJ";
    static String PJPerc  = "PJPerc";

    static String OBJ_MAP   = "objMap";
    static String CLASS_MAP = "classMap";
    static String DATA_MAP  = "dataMap";

    final double BES_HIGH_THRS = 85;
    final double BES_LOW_THRS  = 0.1;
    final double PJ_HIGH_THRS  = 50;
    final int DEPTH_THRS = 3;

    HashSet<Resource> decimalDatatypes = new HashSet<>(List.of(new Resource[]{XSD.xdouble, XSD.xfloat, XSD.decimal}));
    HashSet<Resource> intDatatypes     = new HashSet<>(List.of(new Resource[]{XSD.integer, XSD.unsignedInt, XSD.unsignedShort, XSD.xshort, XSD.positiveInteger, XSD.nonPositiveInteger, XSD.nonNegativeInteger}));
    HashSet<Resource> dateDatatypes    = new HashSet<>(List.of(new Resource[]{XSD.date, XSD.dateTime, XSD.dateTimeStamp, XSD.time}));
//======================================================================================================================

    private HashMap<String, Table> rawMaps = new HashMap<>();
    private List<MappingsFileTemplate.Table> tablesList;
    private HashMap<String, String> tableClassMaps = new HashMap<>();

    private Ontology srcOnto, tgtOnto;

    public MappingSelection(String bertmapMappingsFile) {
        srcOnto = new Ontology(InputDataSource.POontology);
        tgtOnto = new Ontology(InputDataSource.DOontology);

        readMappingsJSON(bertmapMappingsFile);
        tablesList = readMapJSON();
        selectTableMaps();
        selectTableColumnMaps();
    }

    private void readMappingsJSON(String bertmapMappingsFile){
        JsonObject bertmapJson = JsonUtil.readJSON(bertmapMappingsFile).getAsJsonObject();
        for (String ontoEl : bertmapJson.keySet()) {
            JsonArray candsList = bertmapJson.getAsJsonArray(ontoEl);

            StringColumn tgtCand = StringColumn.create(TGTCand);
            DoubleColumn besColumn = DoubleColumn.create(BES);
            IntColumn besRankColumn = IntColumn.create(BESRank);
            DoubleColumn pjColumn = DoubleColumn.create(PJ);
            DoubleColumn pjPercColumn = DoubleColumn.create(PJPerc);
            IntColumn pjRankColumn = IntColumn.create(PJRank);
            for (JsonElement element : candsList) {
                JsonObject cand = element.getAsJsonObject();
                tgtCand.append(cand.get(TGTCand).getAsString());
                besColumn.append(cand.get(BES).getAsDouble());
                besRankColumn.append(cand.get(BESRank).getAsInt());
                pjColumn.append(cand.get(PJ).getAsDouble());
                pjPercColumn.append(cand.get(PJPerc).getAsDouble());
                pjRankColumn.append(cand.get(PJRank).getAsInt());
            }
            Table table = Table.create();
            table.addColumns(tgtCand, besColumn, besRankColumn, pjColumn, pjPercColumn, pjRankColumn);
            rawMaps.put(ontoEl, table);
        }
    }

//======================================================================================================================
//======================================================================================================================
    private void selectTableMaps() {
        for(MappingsFileTemplate.Table table : tablesList) {
            String tablePOClass = table.getMapping().getOntoElURI().toString();
            Table tMap = rawMaps.get(tablePOClass);
            String tableMapping = null;

            try {
                // BES >= BES_HIGH_THRS or (BES >= BES_LOW_THRS and PJ >= PJ_HIGH_THRS
                tMap = tMap
                        .where(tMap.numberColumn(BES).isGreaterThanOrEqualTo(BES_HIGH_THRS)
                           .or(     tMap.numberColumn(BES).isGreaterThanOrEqualTo(BES_LOW_THRS)
                               .and(tMap.numberColumn(PJ) .isGreaterThanOrEqualTo(PJ_HIGH_THRS))
                           )
                );
                tMap = tMap.where(tMap.numberColumn(PJRank).isEqualTo(tMap.numberColumn(PJRank).min()));
                tMap = tMap.where(tMap.numberColumn(PJPerc).isEqualTo(tMap.numberColumn(PJPerc).max()));
                tableMapping = tMap.getString(0, TGTCand);
            }catch (IndexOutOfBoundsException ignored) {}
            tableClassMaps.put(tablePOClass, tableMapping);
            // System.out.printf(">> %s:\n%s\n%s\n\n", tablePOClass, tMap, tableMapping);
        }
    }

//======================================================================================================================
//======================================================================================================================
    private void selectTableColumnMaps() {
        for(MappingsFileTemplate.Table table : tablesList) {
            String tableMap = tableClassMaps.get(table.getMapping().getOntoElURI().toString());
            for(Column col : table.getColumns()) {
                System.out.printf(">> For table = <%s> with selected table map = <%s> : Select map for col <%s>\n", table.getTable(), (tableMap!=null?getLocalName(tableMap):null), col.getColumn());
                String objProp   = col.getObjectPropMapping().getOntoElURI().toString();
                String colClass  = col.getClassPropMapping().getOntoElURI().toString();
                String dataProp  = col.getDataPropMapping().getOntoElURI().toString();
                selectTableColumnMaps(tableMap, objProp, colClass, dataProp);
                System.out.println("\n=============================================================================================================================\n\n");
            }
        }
    }

    private void selectTableColumnMaps(String tableMap, String objProp, String colClass, String dataProp) {
        Table objMap   = rawMaps.get(objProp);
        Table classMap = rawMaps.get(colClass);
        Table dataMap  = rawMaps.get(dataProp);                                                                         //System.out.println("Before\n" + objMap);
        objMap  = filterObjMap(tableMap, objMap);                                                                        //System.out.println("After\n" + objMap);
        dataMap = filterDataMap(dataProp, dataMap);
        findNaryTriples(objMap, classMap, dataMap);
    }

//======================================================================================================================
    private Table filterObjMap(String tableClass, Table objMap) {
        if(tableClass == null) // TODO think of a rule for this case
            return objMap;

        if(objMap == null) // bertmap didn't discover any mapping for the attr obj prop
            return null;

        ArrayList<Integer> toRmv = new ArrayList<>();
        int rowID = -1;
        for(String objCand : objMap.stringColumn(TGTCand)) {
            ++rowID;
            OntResource domain = tgtOnto.getInfereredDomRan(tgtOnto.getOntProperty(objCand), true);
            if(!areCompatible(domain, tableClass, true)) {                                                                    //System.out.println("Are not compatible " + getLocalName(tableClass) + " " + getLocalName(objCand));
                toRmv.add(rowID);
            }
        }                                                                                                               //System.out.println("DROP " + toRmv);
        return toRmv.size()>0 ? objMap.dropRows(Ints.toArray(toRmv)) : objMap;
    }

    private Table filterDataMap(String dataProp, Table dataMap) {
        if(dataMap == null)
            return null;

        OntResource POrange = srcOnto.getOntProperty(dataProp).getRange();
        ArrayList<Integer> toRmv = new ArrayList<>();
        int rowID = -1;
        for(String dataCand : dataMap.stringColumn(TGTCand)) {
            ++rowID;
            boolean areCompatible = false;
            OntResource DOrange = tgtOnto.getInfereredDomRan(tgtOnto.getOntProperty(dataCand), false);
            if(DOrange!=null && !DOrange.toString().startsWith(XSD.NS)) {
                if(DOrange.asClass().getEquivalentClass().asUnionClass().listOperands().toSet().contains(POrange)){
                    areCompatible = true;
            }}else {
                areCompatible =
                        DOrange == null ||
                        DOrange.equals(XSD.xstring) ||
                        DOrange.equals(POrange) ||
                        ((decimalDatatypes.contains(DOrange) || intDatatypes.contains(DOrange)) && intDatatypes.contains(POrange)) ||
                        (decimalDatatypes.contains(DOrange) && decimalDatatypes.contains(POrange)) ||
                        (dateDatatypes.contains(DOrange) && dateDatatypes.contains(POrange));
            }                                                                                                                                   //System.out.printf("%s (%s) - %s (%s) ? %s\n",getLocalName(dataProp), getLocalName(POrange), getLocalName(dataCand), (DOrange!=null?getLocalName(DOrange):"-"), areCompatible);
            if(!areCompatible)
                toRmv.add(rowID);
      }
        return toRmv.size()>0 ? dataMap.dropRows(Ints.toArray(toRmv)) : dataMap;
    }

//======================================================================================================================

    private void findNaryTriples(Table objMap, Table classMap, Table dataMap) {
        System.out.println("Column candidates (elMap) =\n" + objMap+"\n"+classMap+"\n"+dataMap + "\n");

        if(objMap != null && classMap != null) {
            System.out.println("Discover n-ary paths...");

            Table mapPaths = Table.create();
            mapPaths.addColumns(StringColumn.create(OBJ_MAP), StringColumn.create(CLASS_MAP));

            for(String objCand : objMap.stringColumn(TGTCand)) {
                OntResource range = tgtOnto.getInfereredDomRan(tgtOnto.getOntProperty(objCand), false);
                for(String classCand : classMap.stringColumn(TGTCand)) {
                    if(areCompatible(range, classCand, false)) {
                        mapPaths.stringColumn(OBJ_MAP).append(objCand);                                                             //System.out.printf("\t%s -> %s\n", getLocalName(objCand),getLocalName(classCand));
                        mapPaths.stringColumn(CLASS_MAP).append(classCand);
                    }
            }}
            if(mapPaths.rowCount() > 0)
                analysePaths(mapPaths, objMap, classMap);
        }
    }


    private void analysePaths(Table mapPaths, Table objMap, Table classMap) {                                                        System.out.println("Discovered paths:");for(Row path:mapPaths) System.out.printf("\t%s -> %s\n",getLocalName(path.getString(OBJ_MAP)),getLocalName(path.getString(CLASS_MAP)));
        analyseMapElement(mapPaths, objMap, OBJ_MAP);                                                                                System.out.println("\n- Analyze column " + OBJ_MAP);
        analyseMapElement(mapPaths, classMap, CLASS_MAP);                                                                            System.out.println("\n- Analyze column " + CLASS_MAP);
        mapPaths = mapPaths.dropDuplicateRows();                                                                                     System.out.println("\nProcessed paths:");for(Row path:mapPaths) System.out.printf("\t%s -> %s\n",getLocalName(path.getString(OBJ_MAP)),getLocalName(path.getString(CLASS_MAP)));
    }

    private void analyseMapElement(Table mapPaths, Table elMap, String colEl){
        HashMap<String, String> trfs = generalizationAndSpecializationTrfs(elMap,
                findHierarchicalRelations(mapPaths.stringColumn(colEl).unique().asSet()));                                           System.out.println("Trfs :"); trfs.forEach((cand,trfsTo)->{System.out.printf("\t%s => %s\n",getLocalName(cand),getLocalName(trfsTo));});

        for(int i=0; i<mapPaths.rowCount(); ++i){
            String cand = mapPaths.getString(i, colEl);
            mapPaths.stringColumn(colEl).set(i, trfs.getOrDefault(cand,cand));
        }
    }

//======================================================================================================================

    /**
     *      objCand                  classCand
     *      hasDate               -> Date
     *      hasDate               -> FloatingRateNoteDate
     *      hasDateOfRegistration -> Date
     *-
     * ISA relationship:
     *      Closest Common Ancestor = hasDate	Depth = 0		Children = [hasDateOfRegistration (2), hasDate (2) ]
     *      Closest Common Ancestor = Date		Depth = 0		Children = [FloatingRateNoteDate (2), Date (2) ]
     * -----------------------------------------------------------------------------------------------------------------
     *      hasCorrespondingAccount      -> Account
     *      isLinkedToAccount            -> Account
     *      appliesToAccount             -> Account
     *      appliesToAccount             -> DemandDepositAccount
     *-
     * Have common ancestor relationship:
     *      Closest Common Ancestor = relatesTo		        Depth = 1	Children = [isLinkedToAccount (2), hasCorrespondingAccount (2), ]
     * Has no common ancestors with the rest of the candidates:
     *      Closest Common Ancestor = appliesToAccount		Depth = 0	Children = [appliesToAccount (1), ]
     * ISA relationship:
     *      Closest Common Ancestor = Account	Depth = 0	Children = [Account (2), DemandDepositAccount (2), ]
     */
    private HashMap<HashSet<String>, Pair<String, Integer>> findHierarchicalRelations(Set<String> candidates) {

        HashMap<String, Pair<HashSet<String>, Integer>> commonAncestors = new HashMap<>();
        for(String candidate: candidates){
            tgtOnto.getAncestors(candidate, true).forEach((ancestor, depth) -> {
                if(commonAncestors.containsKey(ancestor)) {
                    commonAncestors.get(ancestor).children().add(candidate);
                    int currDepth = commonAncestors.get(ancestor).maxDepth();
                    int updatedDepth = (currDepth==0 || depth==0) ? 0 : Math.max(currDepth, depth);
                    commonAncestors.get(ancestor).setMaxDepth(updatedDepth);
                }else
                    commonAncestors.put(ancestor, new Pair<>(new HashSet<>(){{add(candidate);}}, depth));
        });}

        //*p*/System.out.printf("Cands = %s\n", getLocal(candidates));  commonAncestors.forEach((ancestor, p) -> {System.out.printf("Ans = %s\t\tD = %d\t\tChn = %s\n", getLocalName(ancestor), p.maxDepth(), getLocal(p.children()));});/*p*/

        HashMap<HashSet<String>, Pair<String, Integer>> closestCommonAnc = new HashMap<>();
        HashMap<String,Integer> candGroupSize = new HashMap<>(){{for(String c:candidates) put(c,1);}};
        commonAncestors.forEach((ancestor, p) -> {
            Pair<String,Integer> ccAncOfChildren = closestCommonAnc.get(p.children());
            if(ccAncOfChildren == null)
                closestCommonAnc.put(p.children(), new Pair<>(ancestor, p.maxDepth()));
            else if(ccAncOfChildren.maxDepth() > p.maxDepth()) {
                ccAncOfChildren.setClosestCommonAnc(ancestor);
                ccAncOfChildren.setMaxDepth(p.maxDepth());
            }
            for(String c : p.children())
                candGroupSize.put(c, Math.max(candGroupSize.get(c), p.children().size()));
        });
        closestCommonAnc.entrySet().removeIf(group -> {
            for(String candidate : group.getKey())
                if(group.getKey().size() != candGroupSize.get(candidate))
                    return true;
            return false;
        });
        /*p*/closestCommonAnc.forEach((children, p) -> {System.out.printf("Clos Anc = %s\t\tD = %d\t\tChn = [", getLocalName(p.closestCommonAnc()), p.maxDepth()); for(String c:children) System.out.printf("%s (%d), ", getLocalName(c), candGroupSize.get(c)); System.out.println("]");});System.out.println();/*p*/
        return closestCommonAnc;
    }


    private HashMap<String, String> generalizationAndSpecializationTrfs(
            Table elMap, HashMap<HashSet<String>, Pair<String, Integer>> hierarchies) {

        // maxDepth==0 <=> ISA relationship. Should specialize to some subclass?
        // maxDepth>0 and group size > 1 <=> Have-common-ancestors relationship. Is there some sibling that
        //      is better than the rest, or should generalize to the ancestor
        HashMap<String, String> trfs = new HashMap<>();
        hierarchies.forEach((group, p) -> {
            if(group.size()>1) {
                if(p.maxDepth()==0)
                    trfs.putAll(specialize(p.closestCommonAnc(), group, elMap));
                else
                    trfs.putAll(generalize(p.closestCommonAnc(), group, elMap));
            }
        });
        return trfs;
    }

    private HashMap<String, String> specialize(String ancestorURI, HashSet<String> group, Table elMap) {
        // maxDepth==0 <=> ISA relationship. Should specialize to some subclass?
       HashMap<String, String> trfs = new HashMap<>();                                                                                      System.out.printf("[sp] ISA relations in group = %s\n => Should specialize to some subclass of ancestor = <%s> ? \n", getLocal(group), getLocalName(ancestorURI));

        Table ancestor = elMap.where(elMap.stringColumn(TGTCand).isEqualTo(ancestorURI));
        group.remove(ancestorURI);
        Table groupT = elMap.where(elMap.stringColumn(TGTCand).isIn(group));
        Table tops = groupT
                .where(groupT.intColumn(PJRank).isLessThanOrEqualTo(ancestor.intColumn(PJRank).get(0))
                  .and(groupT.doubleColumn(PJ).isGreaterThanOrEqualTo(ancestor.doubleColumn(PJ).get(0)))
        );
        if(tops.rowCount()>0) {                                                                                                             System.out.printf("[sp] Found %d equally good with ancestor, top = %s\n => Check if there is a top sibling, or if should generalize to the ancestor <%s>.\n", tops.rowCount(), getLocal(tops.stringColumn(TGTCand).asList()), getLocalName(ancestorURI));
            trfs.putAll(generalize(ancestorURI, tops));
        }else {                                                                                                                             System.out.printf("[sp] No generalize to ancestor <%s>\n", getLocalName(ancestorURI));
            for(String cand : group)
                trfs.put(cand, ancestorURI);
        }
        return trfs;
    }

    private HashMap<String, String> generalize(String ancestorURI, HashSet<String> group, Table elMap) {
        // maxDepth>0 and group size > 1 <=> Have-common-ancestors relationship. Is there some sibling that
        //                                   is better than the rest, or should generalize to the ancestor
        Table groupT = elMap.where(elMap.stringColumn(TGTCand).isIn(group));                                                                System.out.printf("[gn] Have-common-ancestor relation in group = %s\n => Is there a top sibling or should generalize to ancestor = <%s> ?\n", getLocal(group), getLocalName(ancestorURI));
        return generalize(ancestorURI, groupT);
    }


    private HashMap<String, String> generalize(String ancestorURI, Table filtered) {
        HashMap<String, String> trfs = new HashMap<>();

        Table tops = filtered
                .where(filtered.intColumn(PJRank).isLessThanOrEqualTo(filtered.intColumn(PJRank).min())
                  .and(filtered.doubleColumn(PJ).isGreaterThanOrEqualTo(filtered.doubleColumn(PJ).max()))
        );
        String trfsTo = (tops.rowCount() == 1) ? tops.getString(0, TGTCand) : ancestorURI;                                                if(tops.rowCount()==1)System.out.printf("[gn] YES best found <%s>\n", getLocalName(trfsTo)); else System.out.printf("[gn] NO generalize to ancestor = <%s>\n", getLocalName(ancestorURI));

        for(String cand : filtered.stringColumn(TGTCand).unique())
            trfs.put(cand, trfsTo);
        trfs.put(ancestorURI, trfsTo);
        return trfs;
    }


//======================================================================================================================
//======================================================================================================================
    private boolean areCompatible(OntResource domRan, String classURI, boolean missingDomRanIsCompatible) {
        boolean areCompatible = false;
        if(domRan == null)
            areCompatible = missingDomRanIsCompatible;
        else {
            List<? extends OntClass> domainOps = domRan.canAs(UnionClass.class) ?
                    domRan.as(UnionClass.class).listOperands().toList() :
                    Collections.singletonList(domRan.as(OntClass.class));

            for(OntClass domainOperand : domainOps) {
                if(areCompatible(domainOperand.getURI(), classURI, true)) {
                    areCompatible = true;
                    break;
            }}
        }
        return areCompatible;
    }


    private boolean areCompatible(String class1, String class2, boolean isSelfSuperclass) {
        return tgtOnto.getAncestors(class1, isSelfSuperclass).containsKey(class2) ||
               tgtOnto.getAncestors(class2, isSelfSuperclass).containsKey(class1);
    }



//======================================================================================================================
//======================================================================================================================
    private String getLocal(Collection<String> group) {
        StringBuilder s = new StringBuilder("[");
        for(String el:group)
            s.append(getLocalName(el)).append(", ");
        s.append("]");
        return s.toString();
    }

    public static void main(String[] args) {
        String bertmapMappingsFile = "C:/Users/karal/progr/onto_workspace/pythonProject/BertMapMappings.json";
        new MappingSelection(bertmapMappingsFile);
    }
}













