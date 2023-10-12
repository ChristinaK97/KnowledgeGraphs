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
import org.checkerframework.checker.units.qual.A;
import org.example.A_InputPoint.InputDataSource;
import org.example.MappingsFiles.MappingsFileTemplate;
import org.example.util.Ontology;
import org.example.util.Pair;
import tech.tablesaw.api.*;
import org.example.A_InputPoint.JsonUtil;
import tech.tablesaw.columns.Column;

import java.util.*;

import static org.example.MappingsFiles.ManageMappingsFile.readMapJSON;
import static org.example.util.Ontology.getLocalName;

public class MappingSelection {

//======================================================================================================================
    static String LOCALNAME = "LocalName";
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

    boolean logHierarchy=false;
    boolean logNary=true;

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
        selectTableOptimal();
        selectTableColumnOptimal();
    }
//======================================================================================================================
//======================================================================================================================
    private ArrayList<Column> createEmptyColumns() {
        ArrayList<Column> columns = new ArrayList<>(){{
            add(StringColumn.create(LOCALNAME));
            add(DoubleColumn.create(BES));
            add(IntColumn.create(BESRank));
            add(DoubleColumn.create(PJ));
            add(DoubleColumn.create(PJPerc));
            add(IntColumn.create(PJRank));
            add(StringColumn.create(TGTCand));
        }};
        return columns;
    }
    private Table createTable(ArrayList<Column> columns) {
        Table table = Table.create();
        for(Column column : columns)
            table.addColumns(column);
        return table;
    }

    private void readMappingsJSON(String bertmapMappingsFile){
        JsonObject bertmapJson = JsonUtil.readJSON(bertmapMappingsFile).getAsJsonObject();
        for (String ontoEl : bertmapJson.keySet()) {
            JsonArray candsList = bertmapJson.getAsJsonArray(ontoEl);
            ArrayList<Column> columns = createEmptyColumns();
            for (JsonElement element : candsList) {
                JsonObject cand = element.getAsJsonObject();
                String candURI = cand.get(TGTCand).getAsString();
                for(Column column : columns) {
                    if(column.name().equals(TGTCand))
                        column.append(candURI);
                    else if(column.name().equals(LOCALNAME))
                        column.append(getLocalName(candURI));
                    else if(column instanceof DoubleColumn)
                        column.append(cand.get(column.name()).getAsDouble());
                    else if(column instanceof IntColumn)
                        column.append(cand.get(column.name()).getAsInt());
                }
            }
            rawMaps.put(ontoEl, createTable(columns));
        }
    }

//======================================================================================================================
//======================================================================================================================
    private void selectTableOptimal() {
        for(MappingsFileTemplate.Table table : tablesList) {
            String tablePOClass = table.getMapping().getOntoElURI().toString();
            Table tMap = rawMaps.get(tablePOClass);
            String tableOptimal = null;
            try {
                // BES >= BES_HIGH_THRS or (BES >= BES_LOW_THRS and PJ >= PJ_HIGH_THRS
                tMap = tMap
                        .where(tMap.numberColumn(BES).isGreaterThanOrEqualTo(BES_HIGH_THRS)
                           .or(     tMap.numberColumn(BES).isGreaterThanOrEqualTo(BES_LOW_THRS)
                               .and(tMap.numberColumn(PJ) .isGreaterThanOrEqualTo(PJ_HIGH_THRS)))
                );
                tMap = tMap.where(tMap.numberColumn(PJRank).isEqualTo(tMap.numberColumn(PJRank).min()));
                tMap = tMap.where(tMap.numberColumn(PJPerc).isEqualTo(tMap.numberColumn(PJPerc).max()));
                tableOptimal = tMap.getString(0, TGTCand);
            }catch (IndexOutOfBoundsException ignored) {}
            tableClassMaps.put(tablePOClass, tableOptimal);                                                             // System.out.printf(">> %s:\n%s\n%s\n\n", tablePOClass, tMap, tableOptimal);
        }
    }

//======================================================================================================================
//======================================================================================================================
    private void selectTableColumnOptimal() {
        for(MappingsFileTemplate.Table table : tablesList) {
            String tableMap = tableClassMaps.get(table.getMapping().getOntoElURI().toString());
            for(MappingsFileTemplate.Column col : table.getColumns()) {                                                 System.out.printf(">> For table = <%s> with selected table map = <%s> : Select map for col <%s>\n", table.getTable(), (tableMap!=null?getLocalName(tableMap):null), col.getColumn());
                String objProp   = col.getObjectPropMapping().getOntoElURI().toString();
                String colClass  = col.getClassPropMapping().getOntoElURI().toString();
                String dataProp  = col.getDataPropMapping().getOntoElURI().toString();
                selectTableColumnOptimal(tableMap, objProp, colClass, dataProp);

                                                                                                                        System.out.println("\n=============================================================================================================================\n\n");
            }
        }
    }

    private Object[] selectTableColumnOptimal(String tableOptimal, String objProp, String colClass, String dataProp) {
        // retrieve column candidates
        Table objMap   = rawMaps.get(objProp);
        Table classMap = rawMaps.get(colClass);
        Table dataMap  = rawMaps.get(dataProp);
        /*---------------------------------------------------*/
        // filter candidates
        if(hasCands(objMap)) {
            objMap  = filterObjMap(tableOptimal, objMap);
            if(objMap.rowCount()>1)
                objMap = considerHierarchies(objMap);
        }
        if(hasCands(classMap) && classMap.rowCount()>1)
            classMap = considerHierarchies(classMap);
        if(hasCands(dataMap))
            dataMap = filterDataMap(dataProp, dataMap);

        System.out.println("Column candidates (elMap) =\n" + objMap+"\n"+classMap+"\n"+dataMap + "\n");
        /*---------------------------------------------------*/
        // search for n-ary path pattern
        Table mapPaths = findNaryPatterns(objMap, classMap, dataMap);
        if(mapPaths.rowCount()>0)
            return selectFromNaryPaths(mapPaths, objMap, classMap, dataMap);
        /*---------------------------------------------------*/
        else
            return selectFromIncompatiblePaths(tableOptimal, objMap, classMap, dataMap);

    }

    private boolean hasCands(Table elMap){
        return elMap != null && elMap.rowCount() > 0;
    }


    private Object[] selectFromIncompatiblePaths(String tableOptimal, Table objMap, Table clsMap, Table dataMap) {
        Object objOptimal = null, clsOptimal = null, dataOptimal = null;
        boolean hasObjCands   = hasCands(objMap);
        boolean hasClassCands = hasCands(clsMap);
        boolean hasDataCands  = hasCands(dataMap);
        ArrayList<String> compatibleDomain = new ArrayList<>();

        if(hasObjCands && hasClassCands) {
            objOptimal = selectOptimal(objMap, null, false);
            clsOptimal = selectOptimal(clsMap, null, false);
            int objVotes = 0, clsVotes = 0;
            for(String criterion : new String[]{BES, PJ}) {
                double objScore = objMap.where(objMap.stringColumn(TGTCand).isEqualTo((String) objOptimal)).doubleColumn(criterion).get(0);
                double clsScore = clsMap.where(clsMap.stringColumn(TGTCand).isEqualTo((String) clsOptimal)).doubleColumn(criterion).get(0);
                double maxScore = Math.max(objScore, clsScore);
                objVotes += objScore == maxScore ? 1 : 0;
                clsVotes += clsScore == maxScore ? 1 : 0;
            }
            if(objVotes != clsVotes) {
                OntResource objRange = tgtOnto.getInferedDomRan((String) objOptimal, false);
                if(!areCompatible(objRange, clsOptimal.toString(), true, false))
                    if(objVotes > clsVotes)
                        clsOptimal = null;
                    else { // objVotes < clsVotes
                        objOptimal = null;
                        clsOptimal = selectOptimal(clsMap, null, true);
                    }
            }
        }
        else if(hasObjCands)
            objOptimal = selectOptimal(objMap, null, false);
        else if (hasClassCands)
            clsOptimal = selectOptimal(clsMap, null, true);

        System.out.printf("Object* = <%s>\tClass* = <%s>\n", (objOptimal!=null ? getLocalName(objOptimal.toString()):null), (clsOptimal!=null ? (clsOptimal instanceof String ? getLocalName(clsOptimal.toString()) : getLocal((Set<String>)clsOptimal)):null));
        if(hasDataCands)
           dataOptimal = selectDataOptimal(dataMap, compatibleDomain, tableOptimal, (String) objOptimal, clsOptimal);

       System.out.printf("Selected optimal | %s -> %s -> %s |\n", (objOptimal!=null ? getLocalName(objOptimal.toString()):null), (clsOptimal!=null ? (clsOptimal instanceof String ? getLocalName(clsOptimal.toString()) : getLocal((Set<String>)clsOptimal)):null), (dataOptimal!=null?getLocalName(dataOptimal.toString()):null));

       return new Object[]{objOptimal, clsOptimal, dataOptimal};
    }


    private Object selectDataOptimal(Table dataMap, ArrayList<String> compatibleDomain, String tableOptimal, String objOptimal, Object clsOptimal) {
        if(clsOptimal != null)
            compatibleDomain.addAll(clsOptimal instanceof String ?
                    Collections.singletonList((String) clsOptimal) : (Set<String>) clsOptimal);

        else if(objOptimal != null) {
            OntResource objRange = tgtOnto.getInferedDomRan(objOptimal, false);
            if(objRange != null)
                compatibleDomain.add(objRange.getURI());
        }else if(tableOptimal != null)
            compatibleDomain.add(tableOptimal);

        Set<String> dataCands;
        if(compatibleDomain.size() > 0) {
            dataCands = classUsesDataProps(compatibleDomain.get(0), dataMap, true);
            for(int i=1 ; i < compatibleDomain.size() ; ++i)
                dataCands.retainAll(classUsesDataProps(compatibleDomain.get(i), dataMap, true));
        }else
            dataCands = dataMap.stringColumn(TGTCand).asSet();
        return selectOptimal(dataMap, dataCands, false);
    }



    private Object selectOptimal(Table elMap, Set<String> filter, boolean allowUnion) {
        Table tops = filter==null ? elMap.copy() : elMap.where(elMap.stringColumn(TGTCand).isIn(filter));
        tops = tops.where(tops.intColumn(PJRank).isLessThanOrEqualTo(tops.intColumn(PJRank).min()));
        if(tops.rowCount() > 1 && ! allowUnion)
            tops = tops.where(tops.doubleColumn(BES).isEqualTo(tops.doubleColumn(BES).max()));
        return tops.rowCount()==1 ?
               tops.getString(0, TGTCand) :
               tops.stringColumn(TGTCand).asSet();
    }



//======================================================================================================================
    private Table filterObjMap(String tableClass, Table objMap) {
        if(tableClass == null) // TODO think of a rule for this case
            return objMap;

        ArrayList<Integer> toRmv = new ArrayList<>();
        int rowID = -1;
        for(String objCand : objMap.stringColumn(TGTCand)) {
            ++rowID;
            OntResource domain = tgtOnto.getInferedDomRan(objCand, true);
            if(!areCompatible(domain, tableClass, true, false)) {                                                                    //System.out.println("Are not compatible " + getLocalName(tableClass) + " " + getLocalName(objCand));
                toRmv.add(rowID);
            }
        }                                                                                                               //System.out.println("DROP " + toRmv);
        return toRmv.size()>0 ? objMap.dropRows(Ints.toArray(toRmv)) : objMap;
    }

    private Table filterDataMap(String dataProp, Table dataMap) {

        OntResource POrange = srcOnto.getOntProperty(dataProp).getRange();
        ArrayList<Integer> toRmv = new ArrayList<>();
        int rowID = -1;
        for(String dataCand : dataMap.stringColumn(TGTCand)) {
            ++rowID;
            boolean areCompatible = false;
            OntResource DOrange = tgtOnto.getInferedDomRan(dataCand, false);
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

    private Table findNaryPatterns(Table objMap, Table clsMap, Table dataMap) {
        boolean hasObjCands   = hasCands(objMap);
        boolean hasClassCands = hasCands(clsMap);
        boolean hasDataCands  = hasCands(dataMap);

        Table mapPaths = Table.create();
        mapPaths.addColumns(StringColumn.create(OBJ_MAP), StringColumn.create(CLASS_MAP), StringColumn.create(DATA_MAP));

        HashMap<String, Set<String>> classCompatibleDataCands = new HashMap<>();
        if(hasClassCands)
            clsMap.stringColumn(TGTCand).forEach(classCand -> {
                Set<String> compatibleDataCands = hasDataCands ?
                        classUsesDataProps(classCand, dataMap, false) : Collections.singleton("");
                if(compatibleDataCands.size() == 0)
                    compatibleDataCands.add("");
                classCompatibleDataCands.put(classCand, compatibleDataCands);
            });

        if(hasObjCands && hasClassCands) {                                                                                              if(logNary)System.out.println("Discover n-ary paths...");
            for(String objCand : objMap.stringColumn(TGTCand)) {
                OntResource range = tgtOnto.getInferedDomRan(objCand, false);
                for(String classCand : clsMap.stringColumn(TGTCand)) {
                    if(areCompatible(range, classCand, false, false)) {
                        for(String dataCand : classCompatibleDataCands.get(classCand)) {
                            mapPaths.stringColumn(OBJ_MAP)  .append(objCand);
                            mapPaths.stringColumn(CLASS_MAP).append(classCand);
                            mapPaths.stringColumn(DATA_MAP) .append(dataCand);
                    }}
        }}}
        classCompatibleDataCands.forEach((classCand, compDataCands) -> {
            if(compDataCands.size()>0 && !mapPaths.stringColumn(CLASS_MAP).contains(classCand)){
                for(String dataCand : compDataCands) {
                    if(!"".equals(dataCand)) {
                        mapPaths.stringColumn(OBJ_MAP)  .append("");
                        mapPaths.stringColumn(CLASS_MAP).append(classCand);
                        mapPaths.stringColumn(DATA_MAP) .append(dataCand);
        }}}});
        return mapPaths;
    }


    private String[] selectFromNaryPaths(Table mapPaths, Table objMap, Table clsMap, Table dataMap) {
        String SumPJRank = "SumPJRank";
        String objOptimal, clsOptimal, dataOptimal=null;
        Set<String> objTop = findTops(mapPaths.stringColumn(OBJ_MAP),   objMap, hasCands(objMap));
        Set<String> clsTop = findTops(mapPaths.stringColumn(CLASS_MAP), clsMap, hasCands(clsMap));

        mapPaths.addColumns(IntColumn.create(SumPJRank));
        for(Row mapPath : mapPaths) {
            String objCand = mapPath.getString(OBJ_MAP),
                   clsCand = mapPath.getString(CLASS_MAP);
            int pairSumPJRank =
                    (objCand.equals("") ? Integer.MAX_VALUE : objMap.where(objMap.stringColumn(TGTCand).isEqualTo(objCand)).intColumn(PJRank).get(0))
                +   clsMap.where(clsMap.stringColumn(TGTCand).isEqualTo(clsCand)).intColumn(PJRank).get(0);

            mapPath.setInt(SumPJRank, pairSumPJRank);
        }
        if(logNary){for(Row mapPath : mapPaths) {String o = mapPath.getString(OBJ_MAP);String c = mapPath.getString(CLASS_MAP);String d = mapPath.getString(DATA_MAP);System.out.printf("\t%s  ->  %s  ->  %s\t\t%d\n", ("".equals(o)?"\t":getLocalName(o)),getLocalName(c),("".equals(d)?"\t":getLocalName(d)), mapPath.getInt(SumPJRank));}}
        Table topPairs = mapPaths.where(mapPaths.intColumn(SumPJRank).isLessThanOrEqualTo(mapPaths.intColumn(SumPJRank).min()));
        objTop.addAll(topPairs.stringColumn(OBJ_MAP).asList());
        clsTop.addAll(topPairs.stringColumn(CLASS_MAP).asList());
        objTop.remove("");                                                                                                               if(logNary){System.out.println("Top objs = " + getLocal(objTop)); System.out.println("Top class = " + getLocal(clsTop));}
        int nObjTop = objTop.size();
        int nClsTop = clsTop.size();

        if(nObjTop > 1 && nClsTop > 1) {                                                                                                    if(logNary)System.out.println("Reject candidates " + getLocal(objTop) + " " + getLocal(clsTop));
            return new String[]{null, null, null}; }

        objOptimal = (nObjTop == 1) ? objTop.iterator().next() : null;
        clsOptimal = (nClsTop == 1) ? clsTop.iterator().next() : null;

        if (objOptimal != null && nClsTop > 0)
            clsOptimal = (String) selectOptimal(clsMap,
                    mapPaths.where(mapPaths.stringColumn(OBJ_MAP).isEqualTo(objOptimal)).stringColumn(CLASS_MAP).asSet(), false);
        else if(nObjTop > 0 && clsOptimal != null)
            objOptimal = (String) selectOptimal(objMap,
                    mapPaths.where(mapPaths.stringColumn(CLASS_MAP).isEqualTo(clsOptimal)).stringColumn(OBJ_MAP).asSet(), false);

        assert objOptimal != null || clsOptimal != null;

        if(hasCands(dataMap))
            dataOptimal = selectDataOptimal(mapPaths, dataMap, objOptimal, clsOptimal);

        if(logNary){System.out.printf("Selected optimal | %s -> %s -> %s |\n", (objOptimal!=null?getLocalName(objOptimal):null), (clsOptimal!=null?getLocalName(clsOptimal):null), (dataOptimal!=null?getLocalName(dataOptimal):null));}

        return new String[]{objOptimal, clsOptimal, dataOptimal};
    }


    private String selectDataOptimal(Table mapPaths, Table dataMap, String objOptimal, String clsOptimal) {
        String compatibleDomain = null;
        Set<String> dataCands = new HashSet<>(){{add("");}};
        if(clsOptimal != null) {
            compatibleDomain = clsOptimal;
            Table optimalPaths = mapPaths.where(mapPaths.stringColumn(CLASS_MAP).isEqualTo(clsOptimal));
            if(objOptimal != null)
                optimalPaths = optimalPaths.where(optimalPaths.stringColumn(OBJ_MAP).isEqualTo(objOptimal));
            dataCands = optimalPaths.stringColumn(DATA_MAP).asSet();
        }else {
            OntResource objRange = tgtOnto.getInferedDomRan(objOptimal, false);
            if(objRange != null)
                compatibleDomain = objRange.getURI();
        }
        dataCands.remove("");
        if(dataCands.size() == 0)
            dataCands = compatibleDomain != null ?
                    classUsesDataProps(compatibleDomain, dataMap, true) :
                    dataMap.stringColumn(TGTCand).asSet();

        return dataCands.size() > 0 ? (String) selectOptimal(dataMap, dataCands, false) : null;

    }



    private Set<String> findTops(StringColumn mapPathEl, Table elMap, boolean hasElCands) {
        if(!hasElCands)
            return new HashSet<>();
        Table tops = elMap.where(elMap.stringColumn(TGTCand).isIn(mapPathEl));
        tops = tops.where(tops.intColumn(PJRank).isLessThanOrEqualTo(tops.intColumn(PJRank).min()));
        return tops.stringColumn(TGTCand).asSet();
    }

//======================================================================================================================

    private Table considerHierarchies(Table elMap) {
        /* maxDepth==0 <=> ISA relationship. Should specialize to some subclass?
        /* maxDepth>0 and group size > 1 <=> Have-common-ancestors relationship. Is there some sibling that
        /*      is better than the rest, or should generalize to the ancestor */
        ArrayList<Column> updatedCols = createEmptyColumns();                                                           //if(logHierarchy) System.out.println("\nFiltered table:\n" + elMap);

        HashMap<HashSet<String>, Pair<String, Integer>> hierarchies =
                findHierarchicalRelations(elMap.stringColumn(TGTCand).asSet());

        hierarchies.forEach((group, p) -> {                                                                             if(logHierarchy) System.out.println("\nGroup = " + getLocal(group));
            Table groupTable = elMap.where(elMap.stringColumn(TGTCand).isIn(group));
            String trfsTo;
            if (group.size()>1)
                trfsTo = (p.maxDepth() == 0) ?
                        specialize(p.closestCommonAnc(), group, elMap) :
                        generalize(p.closestCommonAnc(), group, elMap);
            else
                trfsTo = group.iterator().next();

            updatedCols.get(0).append(getLocalName(trfsTo));
            updatedCols.get(6).append(trfsTo);
            for(Column column : updatedCols)
                if(column instanceof DoubleColumn)
                    column.append(groupTable.doubleColumn(column.name()).max());
                else if(column instanceof IntColumn)
                    column.append((int) groupTable.intColumn(column.name()).min());
        });
        Table updatedTable = createTable(updatedCols);                                                                  //if(logHierarchy) System.out.println("Updated table:\n" + updatedTable);
        return updatedTable;
    }


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
                                                                                                                        if(logHierarchy) System.out.println("\nFind hierarchical relations in " + getLocal(candidates));
        HashMap<String, Pair<HashSet<String>, Integer>> commonAncestors = new HashMap<>();
        for(String candidate: candidates){
            tgtOnto.getAncestors(candidate, true).forEach((ancestor, depth) -> {
                if(commonAncestors.containsKey(ancestor)) {
                    int currDepth = commonAncestors.get(ancestor).maxDepth();
                    int updatedDepth = (currDepth==0 || depth==0) ? 0 : Math.max(currDepth, depth);
                    if(updatedDepth <= DEPTH_THRS) {
                        commonAncestors.get(ancestor).children().add(candidate);
                        commonAncestors.get(ancestor).setMaxDepth(updatedDepth);
                    }
                }else if (depth <= DEPTH_THRS)
                    commonAncestors.put(ancestor, new Pair<>(new HashSet<>(){{add(candidate);}}, depth));
        });}
                                                                                                                        //*p*/if(logHierarchy) {System.out.printf("Cands = %s\n", getLocal(candidates));  commonAncestors.forEach((ancestor, p) -> {System.out.printf("Ans = %s\t\tD = %d\t\tChn = %s\n", getLocalName(ancestor), p.maxDepth(), getLocal(p.children()));});}/*p*/
        HashMap<HashSet<String>, Pair<String, Integer>> hierarchies = new HashMap<>();
        HashMap<String,Integer> candGroupSize = new HashMap<>(){{for(String c:candidates) put(c,1);}};
        commonAncestors.forEach((ancestor, p) -> {
            Pair<String,Integer> ccAncOfChildren = hierarchies.get(p.children());
            if(ccAncOfChildren == null)
                hierarchies.put(p.children(), new Pair<>(ancestor, p.maxDepth()));
            else if(ccAncOfChildren.maxDepth() > p.maxDepth()) {
                ccAncOfChildren.setClosestCommonAnc(ancestor);
                ccAncOfChildren.setMaxDepth(p.maxDepth());
            }
            for(String c : p.children())
                candGroupSize.put(c, Math.max(candGroupSize.get(c), p.children().size()));
        });
        hierarchies.entrySet().removeIf(group -> {
            for(String candidate : group.getKey())
                if(group.getKey().size() != candGroupSize.get(candidate))
                    return true;
            return false;
        });                                                                                                             /*p*/if(logHierarchy) {hierarchies.forEach((children, p) -> {System.out.printf("Clos Anc = %s\t\tD = %d\t\tChn = [", getLocalName(p.closestCommonAnc()), p.maxDepth()); for(String c:children) System.out.printf("%s (%d), ", getLocalName(c), candGroupSize.get(c)); System.out.println("]");});System.out.println();}/*p*/
        return hierarchies;
    }


    private String specialize(String ancestorURI, HashSet<String> group, Table elMap) {
        // maxDepth==0 <=> ISA relationship. Should specialize to some subclass?
        Table ancestor = elMap.where(elMap.stringColumn(TGTCand).isEqualTo(ancestorURI));
        Table groupT   = elMap.where(elMap.stringColumn(TGTCand).isIn(group)
                                .and(elMap.stringColumn(TGTCand).isNotEqualTo(ancestorURI)));
        Table tops = groupT
                .where(groupT.intColumn(PJRank).isLessThanOrEqualTo(ancestor.intColumn(PJRank).get(0))
                  .and(groupT.doubleColumn(PJ).isGreaterThanOrEqualTo(ancestor.doubleColumn(PJ).get(0)))
        );                                                                                                                              if(logHierarchy){ System.out.printf("[sp] Found %d equally good with ancestor, top = %s\n => Check if there is a top sibling, or if should generalize to the ancestor <%s>.\n", tops.rowCount(), getLocal(tops.stringColumn(TGTCand).asList()), getLocalName(ancestorURI));if(tops.rowCount()==0) System.out.printf("[sp] No generalize to ancestor <%s>\n", getLocalName(ancestorURI));}
        return (tops.rowCount()>0) ? generalize(ancestorURI, tops) : ancestorURI;
    }


    private String generalize(String ancestorURI, HashSet<String> group, Table elMap) {
        // maxDepth>0 and group size > 1 <=> Have-common-ancestors relationship. Is there some sibling that
        //                                   is better than the rest, or should generalize to the ancestor
        Table groupT = elMap.where(elMap.stringColumn(TGTCand).isIn(group));                                                                if(logHierarchy) {System.out.printf("[gn] Have-common-ancestor relation in group = %s\n => Is there a top sibling or should generalize to ancestor = <%s> ?\n", getLocal(group), getLocalName(ancestorURI));}
        return generalize(ancestorURI, groupT);
    }


    private String generalize(String ancestorURI, Table filtered) {

        Table tops = filtered
                .where(filtered.intColumn(PJRank).isLessThanOrEqualTo(filtered.intColumn(PJRank).min())
                  .and(filtered.doubleColumn(PJ).isGreaterThanOrEqualTo(filtered.doubleColumn(PJ).max()))
        );
        String trfsTo = (tops.rowCount() == 1) ? tops.getString(0, TGTCand) : ancestorURI;                                                if(logHierarchy) {if(tops.rowCount()==1)System.out.printf("[gn] YES best found <%s>\n", getLocalName(trfsTo)); else System.out.printf("[gn] NO generalize to ancestor = <%s>\n", getLocalName(ancestorURI));}
        return trfsTo;
    }


//======================================================================================================================
//======================================================================================================================

    private boolean areCompatible(OntResource domRan, String classURI, boolean missingDomRanIsCompatible, boolean checkDisjoint) {
        boolean areCompatible = false;
        if(domRan == null)
            areCompatible = missingDomRanIsCompatible;
        else {
            List<? extends OntClass> domainOps = domRan.canAs(UnionClass.class) ?
                    domRan.as(UnionClass.class).listOperands().toList() :
                    Collections.singletonList(domRan.as(OntClass.class));

            for(OntClass domainOperand : domainOps) {
                if(areCompatible(domainOperand.getURI(), classURI, true, checkDisjoint)) {
                    areCompatible = true;
                    break;
            }}
        }
        return areCompatible;
    }


    private boolean areCompatible(String resource1, String resource2, boolean isSelfSuperResource, boolean checkDisjoint) {
        return resource1.equals(resource2) ||
               tgtOnto.getAncestors(resource1, isSelfSuperResource).containsKey(resource2) ||
               tgtOnto.getAncestors(resource2, isSelfSuperResource).containsKey(resource1) ||
               (checkDisjoint && !tgtOnto.areDisjoint(resource1, resource2));
    }



    private HashSet<String> classUsesDataProps(String classURI, Table dataMap, boolean missingDomainIsCompatible) {
        HashSet<String> compatibleCands = new HashSet<>();
        Set<String> otherCands;

        for(String dataCand : dataMap.stringColumn(TGTCand)) {
            OntResource domain = tgtOnto.getInferedDomRan(dataCand, true);
            if(areCompatible(domain, classURI, missingDomainIsCompatible, false))
                compatibleCands.add(dataCand);
        }
        otherCands = dataMap.where(dataMap.stringColumn(TGTCand).isNotIn(compatibleCands)).stringColumn(TGTCand).asSet();                       //System.out.printf("%s  class\n\tCompatible dataCands = %s.\n\tOther cands = %s\n", getLocalName(classURI), getLocal(compatibleCands), getLocal(otherCands));
        if(otherCands.size() > 0) {
            String query = Ontology.swPrefixes() + "\n"                                     +
                    "select ?restrProp where {"                                             +
                    "   {\n" +
                    "      <" + classURI + "> rdfs:subClassOf [ a owl:Restriction ;\n"      +
                    "                         owl:onProperty ?restrProp ] .\n"              +
                    "   } union {\n" +
                    "       <" + classURI + "> rdfs:subClassOf+ ?superCl .\n"               +
                    "       ?superCl rdfs:subClassOf [ a owl:Restriction ;\n"               +
                    "                                  owl:onProperty ?restrProp ] .\n"     +
                    "   }}";
            Set<String> restrProps = tgtOnto.runQuery(query,new String[]{"restrProp"}).stringColumn("restrProp").asSet();           //System.out.println("\tRestr Props = " + getLocal(restrProps));
            for(String otherCand : otherCands)
                for(String restrProp : restrProps)
                    if(areCompatible(otherCand, restrProp, true, false)) {
                        compatibleCands.add(otherCand);                                                                                         //System.out.printf("\tCand <%s> is compat w restr prop <%s>.\n", getLocalName(otherCand), getLocalName(restrProp));
                        break;
                    }
        }
        return compatibleCands;
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






/*if(p.maxDepth() <= DEPTH_THRS && group.size()>1) {
                String trfsTo = (p.maxDepth() == 0) ?
                             specialize(p.closestCommonAnc(), group, elMap) :
                             generalize(p.closestCommonAnc(), group, elMap);

                 updatedCols.get(0).append(getLocalName(trfsTo));
                 updatedCols.get(6).append(trfsTo);
                 for(Column column : updatedCols)
                     if(column instanceof DoubleColumn)
                         column.append(groupTable.doubleColumn(column.name()).max());
                     else if(column instanceof IntColumn)
                         column.append((int) groupTable.intColumn(column.name()).min());
            }else
                for (Column column : updatedCols)
                    if (column instanceof StringColumn)
                        column.append(groupTable.stringColumn(column.name()));
                    else if (column instanceof DoubleColumn)
                        column.append(groupTable.doubleColumn(column.name()));
                    else if (column instanceof IntColumn)
                        column.append(groupTable.intColumn(column.name()));*/

/*private void analysePaths(Table mapPaths, Table objMap, Table classMap) {                                                        System.out.println("Discovered paths:");for(Row path:mapPaths) System.out.printf("\t%s -> %s\n",getLocalName(path.getString(OBJ_MAP)),getLocalName(path.getString(CLASS_MAP)));
        analyseMapElement(mapPaths, objMap, OBJ_MAP);                                                                                System.out.println("\n- Analyze column " + OBJ_MAP);
        analyseMapElement(mapPaths, classMap, CLASS_MAP);                                                                            System.out.println("\n- Analyze column " + CLASS_MAP);
        mapPaths = mapPaths.dropDuplicateRows();                                                                                     System.out.println("\nProcessed paths:");for(Row path:mapPaths) System.out.printf("\t%s -> %s\n",getLocalName(path.getString(OBJ_MAP)),getLocalName(path.getString(CLASS_MAP)));
    }

    private void analyseMapElement(Table mapPaths, Table elMap, String colEl){
        HashMap<String, String> trfs = generalizationAndSpecializationTrfs(elMap,
                findHierarchicalRelations(mapPaths.stringColumn(colEl).asSet()));                                                 System.out.println("Trfs :"); trfs.forEach((cand,trfsTo)->{System.out.printf("\t%s => %s\n",getLocalName(cand),getLocalName(trfsTo));});

        for(int i=0; i<mapPaths.rowCount(); ++i){
            String cand = mapPaths.getString(i, colEl);
            mapPaths.stringColumn(colEl).set(i, trfs.getOrDefault(cand,cand));
        }
    }*/

/*private HashMap<String, String> generalizationAndSpecializationTrfs(
            Table elMap, HashMap<HashSet<String>, Pair<String, Integer>> hierarchies) {
        // maxDepth==0 <=> ISA relationship. Should specialize to some subclass?
        // maxDepth>0 and group size > 1 <=> Have-common-ancestors relationship. Is there some sibling that
        //      is better than the rest, or should generalize to the ancestor
        HashMap<String, String> trfs = new HashMap<>();
        hierarchies.forEach((group, p) -> {
            if(group.size()>1) {
                String trfsTo = (p.maxDepth()==0) ?
                        specialize(p.closestCommonAnc(), group, elMap) :
                        generalize(p.closestCommonAnc(), group, elMap);
                for(String cand : group)
                    trfs.put(cand, trfsTo);
            }
        });
        return trfs;
    }*/



