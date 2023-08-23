package org.example.MappingGeneration;

import org.example.util.Ontology;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Matches {
    public static class Match {
        String match;
        ArrayList<String> path = null;
        double score;

        public Match(String match, double score) {
            this.match = match;
            this.score = score;
        }
        public Match(ArrayList<String> path) {
            match = "";
            this.path = path;
            score = 0;
        }
    }

    private HashMap<String, Match> matches = new HashMap<>();

    public int size() {
        return matches.size();
    }

    public void addMatch(String ontoEl, String match, double score) {
        matches.put(ontoEl, new Match(match, score));
    }

    public double getScore(String ontoEl) {
        return matches.containsKey(ontoEl) ?
                matches.get(ontoEl).score : 0;
    }

    public URI getMatchURI(URI ontoEl) {
        return getMatchURI(ontoEl.toString());
    }
    public URI getMatchURI(String ontoEl) {
        if(matches.containsKey(ontoEl))
            return URI.create(matches.get(ontoEl).match);
        return URI.create("");
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

    @Override
    public String toString() {
        StringBuilder bd = new StringBuilder();
        matches.forEach((ontoEl, match) -> {
            bd.append(Ontology.getLocalName(ontoEl))
                    .append("\t")
                    .append(Ontology.getLocalName(match.match))
                    .append("\t")
                    .append(match.score)
                    .append("\n");
        });
        return bd.toString();
    }
}