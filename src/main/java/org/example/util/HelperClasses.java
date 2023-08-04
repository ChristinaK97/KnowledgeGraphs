package org.example.util;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntResource;

public class HelperClasses {

    public static class Pair<Class1, Class2> {
        private Class1 el1;
        private Class2 el2;

        public Pair(Class1 el1, Class2 el2) {
            this.el1 = el1;
            this.el2 = el2;
        }

        public Class1 tableClass() {return el1;}
        public Class2 hasPath() {return el2;}

        public Class1 pathElement() {return el1;}
        public Class2 createNewIndiv() {return el2;}

        @Override
        public String toString() {
            return String.format("%s\t%s", el1, el2);
        }

    }


}
