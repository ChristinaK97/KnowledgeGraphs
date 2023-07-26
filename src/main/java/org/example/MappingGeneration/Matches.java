package org.example.MappingGeneration;

import org.example.util.Util;

import java.net.URI;
import java.util.HashMap;

public class Matches {
    public static class Match {
        String match;
        double score;

        public Match(String match, double score) {
            this.match = match;
            this.score = score;
        }
    }

    private HashMap<String, Match> matches = new HashMap<>();

    public int size() {
        return matches.size();
    }

    public boolean addMatch(String ontoEl, String match, double score) {
        if(!matches.containsKey(ontoEl)) {
            matches.put(ontoEl, new Match(match, score));
            return true;
        }
        else if (matches.get(ontoEl).score < score) {
            matches.replace(ontoEl, new Match(match, score));
            return true;
        }
        return false;
    }

    public URI getMatchURI(URI ontoEl) {
        return getMatchURI(ontoEl.toString());
    }
    public URI getMatchURI(String ontoEl) {
        if(matches.containsKey(ontoEl))
            return URI.create(matches.get(ontoEl).match);
        return URI.create("");
    }

    @Override
    public String toString() {
        StringBuilder bd = new StringBuilder();
        matches.forEach((ontoEl, match) -> {
            bd.append(Util.getLocalName(ontoEl))
                    .append("\t")
                    .append(Util.getLocalName(match.match))
                    .append("\t")
                    .append(match.score)
                    .append("\n");
        });
        return bd.toString();
    }
}