package org.example.util;

public class Pair<Class1, Class2> {
    private Class1 el1;
    private Class2 el2;

    public Pair(Class1 el1, Class2 el2) {
        this.el1 = el1;
        this.el2 = el2;
    }

    public Class1 el1() {return el1;}
    public Class2 el2() {return el2;}

    //-----------------------------------------------
    public Class1 tableClass() {
        return el1;
    }

    public Class2 hasPath() {
        return el2;
    }

    //-----------------------------------------------
    public Class1 pathElement() {
        return el1;
    }

    public Class2 createNewIndiv() {
        return el2;
    }

    //-----------------------------------------------
    public Class1 annotationPropIRI() {
        return el1;
    }

    public Class2 label() {
        return el2;
    }
    //-----------------------------------------------
    public Class1 children() {return el1;}
    public Class2 maxDepth() {return el2;}
    public void setMaxDepth(Class2 el2) {
        this.el2 = el2;
    }
    public Class1 closestCommonAnc() {return el1;}
    public void setClosestCommonAnc(Class1 el1) {this.el1 = el1;}
    //-----------------------------------------------
    @Override
    public String toString() {
        return String.format("%s\t%s", el1, el2);
    }

}
