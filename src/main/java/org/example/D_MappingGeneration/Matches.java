package org.example.D_MappingGeneration;

import org.example.util.Ontology;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.example.D_MappingGeneration.MappingSelection.MappingSelection.getLocal;

import org.example.util.Ontology;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Matches {
    public static class Match {
        List<URI> match = new ArrayList<>();
        ArrayList<String> path = null;
        double score;

        public Match(String match, double score) {
            this.match = Collections.singletonList(URI.create(match));
            this.score = score;
        }

        public Match(Collection<String> match, double score) {
            this.match = match.stream()
                    .map(URI::create)
                    .collect(Collectors.toCollection(ArrayList::new));
            this.score = score;
        }

        public Match(ArrayList<String> path) {
            this.path = path;
            score = 0;
        }
    }
    // =================================================================================================================

    private HashMap<String, Match> matches = new HashMap<>();

    public int size() {
        return matches.size();
    }


    public void addMatch(String ontoEl, Object match, double score) {
        if(match instanceof String)
            matches.put(ontoEl, new Match((String) match, score));
        else if(match instanceof Collection && ((Collection<?>) match).size() > 0)
            matches.put(ontoEl, new Match((Collection<String>) match, score));
    }


    public double getScore(String ontoEl) {
        return matches.containsKey(ontoEl) ?
                matches.get(ontoEl).score : 0;
    }


    public List<URI> getMatchURI(URI ontoEl) {
        return getMatchURI(ontoEl.toString());
    }
    public List<URI> getMatchURI(String ontoEl) {
        if(matches.containsKey(ontoEl))
            return matches.get(ontoEl).match;
        return new ArrayList<>();
    }

    public void setPath(String ontoEl, ArrayList<String> path) {
        try {
            matches.get(ontoEl).path = path;
        }catch (NullPointerException e) {
            matches.put(ontoEl, new Match(path));
        }
    }
    public List<URI> getPath(String ontoEl) {
        if(matches.containsKey(ontoEl) && matches.get(ontoEl).path != null) {
            List<URI> pathURIs = new ArrayList<>();
            matches.get(ontoEl).path.forEach(pathString -> {
                pathURIs.add(URI.create(pathString));
            });
            return pathURIs;
        }else
            return null;
    }

//-----------------------------------------------------------------------------------------
    @Override
    public String toString() {
        StringBuilder bd = new StringBuilder();
        bd.append("# total matched elements = ").append(matches.size()).append("\n");
        AtomicInteger c = new AtomicInteger();
        matches.forEach((ontoEl, match) -> {
            bd.append(c.getAndIncrement()).append(".\t")
                    .append(Ontology.getLocalName(ontoEl))
                    .append("\t\t")
                    .append(match.match)
                    .append("\t\t")
                    .append(match.score)
                    .append("\n");
        });
        return bd.toString();
    }
}