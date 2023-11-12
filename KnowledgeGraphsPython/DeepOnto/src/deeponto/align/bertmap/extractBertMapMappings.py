import json
import re
from itertools import product
from typing import Union
from xml.sax import SAXParseException

from rdflib import Graph, URIRef, BNode
from rdflib.term import Node
from thefuzz import fuzz
from tqdm import tqdm

OntoEl = 'ontoEl'
OntoElMaps = 'OntoElMaps'
TGTCand = 'TGTCand'
BES = 'BES'
BESRank = 'BESRank'
PJRank = 'PJRank'
PJ = 'PJ'
PJPerc = 'PJPerc'
cLen = "cLen"


class MappingSelector:
    baseElements = {"TableClass", "PureProperty, AttributeClass", "AttributeClass", "hasValueProperty"}
    swNamespaces = """
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX owl: <http://www.w3.org/2002/07/owl#>
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
    """


    def __init__(self, rawMappingsFile, srcOntoPath, tgtOntoPath, srcAnnotProps, tgtAnnotProps, outputFile):
        self.srcOnto = self._loadOntology(srcOntoPath)
        self.srcNs = self._ontoNs(self.srcOnto)
        self.srcAnnotProps = srcAnnotProps
        self.baseElements = {self.srcNs + baseEl for baseEl in MappingSelector.baseElements}
        self.swNamespaces = MappingSelector.swNamespaces + f"PREFIX PO: <{self.srcNs}>\n"

        self.tgtOnto = self._loadOntology(tgtOntoPath)
        self.tgtAnnotProps = tgtAnnotProps

        self.rawMaps = self._readRawMappings(rawMappingsFile)
        self._saveRawMaps(outputFile)



# ======================================================================================================================
    def _loadOntology(self, ontoPath):
        try:
            g = Graph(store="Oxigraph")
            file_format = "application/rdf+xml" if ontoPath.endswith((".rdf", ".owl")) else "turtle"
            g.parse(source=ontoPath, format=file_format)
            return g
        except SAXParseException:
            print("Unsupported ontology file format")
            exit(1)

    def _ontoNs(self, onto):
        return str(list(onto.query(MappingSelector.swNamespaces +
                                   "select ?ns where {\n?ns a owl:Ontology .}"))[0][0])

    def _readRawMappings(self, rawMappingsFile):
        rawMaps = {}
        with open(rawMappingsFile, 'r') as file:
            data = json.load(file)

        for ontoEl, cands in tqdm(data.items()):
            if ontoEl in self.baseElements:
                continue
            # ---------------------------------------------------------------
            ontoElMaps = [{
                    TGTCand : tgtCand[1],
                    BES: tgtCand[2] * 100,
                    BESRank: besRank + 1,
                    **self._partialJaccard(ontoEl, tgtCand[1])
                }for besRank, tgtCand in enumerate(cands)
            ]
            # ---------------------------------------------------------------
            ontoElMaps = sorted(
                ontoElMaps,
                key=lambda cand: (cand[PJ], cand[cLen]), reverse=True
            )
            current_rank, prev_score = 0, None
            for tgtCand in ontoElMaps:
                curr_score = (tgtCand[PJ], tgtCand[cLen])
                if curr_score != prev_score:
                    current_rank += 1
                tgtCand[PJRank] = current_rank
                prev_score = curr_score

            rawMaps[ontoEl] = ontoElMaps
        return rawMaps


    def _getResourceAnnots(self, resource, isSrcResource):
        annotProps, onto = (self.srcAnnotProps, self.srcOnto) if isSrcResource else \
                           (self.tgtAnnotProps, self.tgtOnto)
        query = f"""
            SELECT ?annot
            WHERE {{
                <{resource}> ?predicate ?annot .
                FILTER (?predicate IN (%s))
            }}
        """ % ", ".join(f"<{prop}>" for prop in annotProps)
        resourceAnnots = [str(annot[0].lower()) for annot in onto.query(query)]
        if isSrcResource:
            query = f"""{self.swNamespaces}
                SELECT ?tableClassLabel WHERE {{
                    ?tableClass rdfs:subClassOf PO:TableClass ;
                                rdfs:label ?tableClassLabel .
                    {{
                        ?tableClass rdfs:subClassOf 
                            [ a owl:Restriction ; owl:onProperty <{resource}> ] .                        
                    }}
                    union
                    {{
                        ?tableClass rdfs:subClassOf 
                            [ a owl:Restriction ; owl:someValuesFrom <{resource}>] . 
                    }}
                    union
                    {{
                        ?attributeClass rdfs:subClassOf PO:AttributeClass ,
                            			[a owl:Restriction; owl:onProperty <{resource}> ] .
                        ?tableClass rdfs:subClassOf [ a owl:Restriction ; owl:someValuesFrom ?attributeClass] .  
                    }}            
                }}
            """
            resourceAnnots += [f"{tableClassLabel} {resourceLabel}"
                               for tableClassLabel in [str(r[0].lower()) for r in onto.query(query)]
                               for resourceLabel in resourceAnnots
            ]

        return resourceAnnots


    def _partialJaccard(self, ontoEl, tgtCand):
        srcAnnots = self._getResourceAnnots(ontoEl, True)
        tgtAnnots = self._getResourceAnnots(tgtCand, False)
        pj, pjp, candLen = 0, 0, 0

        for srcAnnot, tgtAnnot in product(srcAnnots, tgtAnnots):
            tgtTokens = re.findall(r'\b(?!(?:has|is)\b)\w+', tgtAnnot)
            pair_pj, pair_pjp  = 0, 0
            for token in tgtTokens:
                if len(token) > 1:
                    level = fuzz.partial_ratio(token, srcAnnot)/100
                    pair_pjp += level
                    if level == 1:
                        pair_pj+=1
            pair_pj /= len(tgtTokens)
            pair_pjp /= len(tgtTokens)

            if pair_pjp > pjp:
                pj = pair_pj
                pjp = pair_pjp
                candLen = len(tgtTokens)

        return {PJ : pj * 100, PJPerc : pjp * 100, cLen : candLen}


    def getLocalName(self, uri):
        # Regular expression to match / or # followed by anything except / and #
        match = re.search(r'[\/#]([^\/#]+)$', uri)
        return match.group(1) if match else None

    def isNamedNode(self, node):
        return not isinstance(node, BNode)

    def _getURIRef(self, resource: Union[str, URIRef, Node]):
        return resource if isinstance(resource, URIRef) else URIRef(resource)

    def _saveRawMaps(self, outputFile):
        with open(outputFile, 'w', encoding='utf-8') as f:
            json.dump(self.rawMaps, f, ensure_ascii=False, indent=4)

