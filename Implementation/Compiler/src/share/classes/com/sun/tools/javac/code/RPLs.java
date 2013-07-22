package com.sun.tools.javac.code;

import static com.sun.tools.javac.code.Flags.STATIC;

import java.util.Iterator;

import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Substitute.Subst;
import com.sun.tools.javac.code.Substitute.SubstRPLs;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Pair;

/**
 * Utility class containing various operations on RPLs.
 * 
 * @author Rob Bocchino
 */

public class RPLs {
    protected static final Context.Key<RPLs> rplsKey =
        new Context.Key<RPLs>();

    final Symtab syms;

    public static RPLs instance(Context context) {
        RPLs instance = context.get(rplsKey);
        if (instance == null)
            instance = new RPLs(context);
        return instance;
    }

    protected RPLs(Context context) {
	syms = Symtab.instance(context);
    }

    /** Single, global RPL for Root */
    public static final RPL ROOT = new RPL(RPLElement.ROOT_ELEMENT);

    /**                                                                         
     * Capture a list of RPLs                                                   
     */
    public static List<RPL> captureRPLs(List<RPL> list) {
        ListBuffer<RPL> lb = ListBuffer.lb();
        for (RPL rpl : list) {
            RPL captureRPL = rpl.capture();
            lb.append(captureRPL);
	}
        return lb.toList();
    }

    /**
     * Disjoint RPLs.  See Section 3.2 of the Tech Report
     */
    public boolean areDisjoint(RPL rpl1, RPL rpl2, List<Pair<RPL,RPL>> constraints) {
	// If rpl1 and rpl2 are included in disjoint RPLs, then they are disjoint.
	for (Pair<RPL,RPL> constraint : constraints) {
	    if (rpl1.isIncludedIn(constraint.fst) && rpl2.isIncludedIn(constraint.snd))
		return true;
	    if (rpl1.isIncludedIn(constraint.snd) && rpl2.isIncludedIn(constraint.fst))
		return true;
	}
	
	// DISJOINT-RIGHT-1
	if (rpl1.elts.last().isDisjointFrom(rpl2.elts.last(), this, 
		constraints)) {
	    return true;
	}
	// DISJOINT-RIGHT-2
	if (areDisjointFromRight(rpl1, rpl2, constraints)) {
	    return true;
	}
	// DISJOINT-LEFT
	RPL bound1 = rpl1.upperBound();
	RPL bound2 = rpl2.upperBound();
	if (areDisjointFromLeft(rpl1, rpl2, constraints) || 
		areDisjointFromLeft(bound1, bound2, constraints)) {
	    return true;
	}
	//System.err.println("Cannot prove that " + rpl1 + " and " + rpl2 + " are disjoint");
	return false;
    }

    public boolean areDisjointFromLeft(RPL rpl1, RPL rpl2, 
	    List<Pair<RPL,RPL>> constraints) {
	if (rpl1.isEmpty() && rpl2.isEmpty()) return false;
	if (rpl1.isEmpty()) {
	    for (RPLElement e : rpl2.elts)
		if (!e.equals(RPLElement.STAR)) return true;
	    return false;
	}
	if (rpl2.isEmpty()) {
	    for (RPLElement e : rpl1.elts)
		if (!e.equals(RPLElement.STAR)) return true;
	    return false;
	}
	if (rpl1.isUnderLocal() != rpl2.isUnderLocal())
	    return true;
	// Distinct local names
	if (rpl1.elts.head.isDisjointFrom(rpl2.elts.head, this, constraints))
	    return true;
	// Do the RPLs start out the same then diverge?
	if (!rpl1.elts.head.equals(rpl2.elts.head)) return false;
	List<RPLElement> elts1 = rpl1.elts.tail;
	List<RPLElement> elts2 = rpl2.elts.tail;
	while (!elts1.isEmpty() || !elts2.isEmpty()) {
	    if (elts1.head == RPLElement.STAR || elts2.head == RPLElement.STAR)
		return false;
	    if (elts1.isEmpty() || elts2.isEmpty()) return true;
	    if (elts1.head.isDisjointFrom(elts2.head, this, constraints))
		return true;
	    elts1 = elts1.tail;
	    elts2 = elts2.tail;
	}
	return false;
    }
    
    public boolean areDisjointFromRight(RPL R1, RPL R2,
	    List<Pair<RPL,RPL>> constraints) {
	if (R1.isUnique() && R2.isUnique() && !equalUniquePrefix(R1,R2)) {
	    return false;
	}
	if (R1.isEmpty() || R2.isEmpty()) return false;
	List<RPLElement> elts1 = R1.elts.reverse();
	List<RPLElement> elts2 = R2.elts.reverse();
	while (!elts1.isEmpty() && !elts2.isEmpty()) {
	    if (elts1.head == RPLElement.STAR || elts2.head == RPLElement.STAR)
		return false;
	    if (elts1.head.isDisjointFrom(elts2.head, this, constraints))
		return true;
	    elts1 = elts1.tail;
	    elts2 = elts2.tail;
	}
	return false;    
    }
    
    /** 
     * Do R1 and R2 share the same unique prefix after runtime parameter 
     * substitution ? 
     */
    public boolean equalUniquePrefix(RPL R1, RPL R2) {
	RPLElement prefix1 = R1.uniquePrefix();
	RPLElement prefix2 = R2.uniquePrefix();
	return (prefix1 != null) && prefix1.equals(prefix2);
    }

    /**
     * Do R1 and R2 definitely not share the same unique prefix after runtime
     * substitution?  True if either or both of R1 and R2 are not unique (i.e.,
     * neither has a unique prefix.  Also true if both R1 and R2 are unique
     * (so they each have a unique prefix) and the prefixes are different. 
     */
    public boolean unequalUniquePrefix(RPL R1, RPL R2, 
	    List<Pair<RPL,RPL>> constraints) {
	if (!R1.isUnique() || !R2.isUnique()) {
	    return false;
	}
	for (Pair<RPL,RPL> constraint : constraints) {
	    RPL C1 = new RPL(constraint.fst.elts.append(RPLElement.STAR));
	    RPL C2 = new RPL(constraint.snd.elts.append(RPLElement.STAR));
	    if (R1.isIncludedIn(C1) && R2.isIncludedIn(C2)) {
		return true;
	    }
	}
	return false;
    }
    
    /**
     * Are the region constraints satisfied after subbing actuals for formals?
     * @param constraints	Constraints that need to be satisfied
     * @param formals		Formals we are subbing for
     * @param actuals		Actuals to sub for formals
     * @param envConstraints	Constraints guaranteed by the environment.  For
     * 				example, if we need the constraint P1 # P2,
     * 				and the environment guarantees P1 # P2, that's OK.
     * @return                  Yes or no.
     */
    public boolean disjointnessConstraintsAreSatisfied(List<Pair<RPL,RPL>> constraints,
	    List<RPL> formals, List<RPL> actuals,
	    List<Pair<RPL,RPL>> envConstraints) {
	for (Pair<RPL,RPL> constraint : constraints) {
	    if (!areDisjoint(constraint.fst.substRPLParams(formals, actuals), 
		    constraint.snd.substRPLParams(formals, actuals),
		    envConstraints))
		return false;
	}
	return true;
    }
    
    /**
     * Are regions being consistently bound to parameters, with regard to atomic?
     */
    public boolean atomicConstraintsAreSatisfied(List<RPL> formals,
	    List<RPL> actuals) {
	while (formals.nonEmpty() && actuals.nonEmpty()) {
	    if (formals.head.isAtomic() != actuals.head.isAtomic())
		return false;
	    formals = formals.tail;
	    actuals = actuals.tail;
	}
	return true;
    }
    
    public List<RPL> substIndices(List<RPL> rpls, List<VarSymbol> from, 
	    List<JCExpression> to) {
	ListBuffer<RPL> buf = ListBuffer.<RPL>lb();
	for (RPL rpl : rpls) {
	    buf.append(rpl.substIndices(from, to));
	}	
	return buf.toList();
    }
 
    /**
     * Substitution classes
     */
    public static final Subst substRPLParams = new Subst<RPL,RPL,RPL>() {
	public RPL basic(RPL rpl, RPL from, RPL to) {
	    return rpl.substRPLParam(from, to);
	}
    };
    public static final Subst substTRParams = new Subst<RPL,Type,Type>() {
	public RPL basic(RPL rpl, Type from, Type to) {
	    return rpl.substTRParam(from, to);
	}
    };
    public static final Subst substRPLsForVars = new Subst<RPL,VarSymbol,RPL>() {
	public RPL basic(RPL rpl, VarSymbol from, RPL to) {
	    return rpl.substRPLForVar(from, to);
	}
    };
    public static final Subst substVars = new Subst<RPL,VarSymbol,VarSymbol>() {
	public RPL basic(RPL rpl, VarSymbol from, VarSymbol to) {
	    return rpl.substVar(from, to);
	}
    };
    public static final Subst substExpsForVars =
	    new Subst<RPL,VarSymbol,JCExpression>() {
	public RPL basic(RPL rpl, VarSymbol from, JCExpression to) {
	    return rpl.substExpForVar(from, to);
	}
    };
    public static final Subst substIndices =
	    new Subst<RPL,VarSymbol,JCExpression>() {
	public RPL basic(RPL rpl, VarSymbol from, JCExpression to) {
	    return rpl.substIndex(from, to);
	}
    };

}
    
