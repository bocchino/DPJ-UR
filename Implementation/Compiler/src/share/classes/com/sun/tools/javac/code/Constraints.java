package com.sun.tools.javac.code;

import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Pair;

/** A class for representing RPL and effect constraints
 */
public class Constraints {
    public Constraints(List<Pair<RPL,RPL>> disjointRPLs, 
	    List<Pair<Effects,Effects>> noninterferingEffects,
	    List<Pair<RPL,RPL>> unequalPrefixes) {
	this.disjointRPLs = disjointRPLs;
	this.noninterferingEffects = noninterferingEffects;
	this.unequalPrefixes = unequalPrefixes;
    }
    public Constraints() {}
    public List<Pair<RPL,RPL>> disjointRPLs = List.nil();
    public List<Pair<Effects,Effects>> noninterferingEffects = List.nil();
    public List<Pair<RPL,RPL>> unequalPrefixes = List.nil();
    // We can add others here as necessary, like inclusion or subeffects
}