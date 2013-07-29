

package com.sun.tools.javac.code;

import java.util.Iterator;

import com.sun.tools.javac.code.Substitute.AsMemberOf;
import com.sun.tools.javac.code.Substitute.SubstRPLs;
import com.sun.tools.javac.code.Symbol.EffectParameterSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Pair;

/** A class to represent a DPJ effect
 */
public abstract class Effect implements
	SubstRPLs<Effect>,
	AsMemberOf<Effects>
{
    
    /** Is this an atomic effect? */
    protected boolean isAtomic;
    public boolean isAtomic() { return isAtomic && !isNonint; }
    
    /** Is this a nonint effect? */
    protected boolean isNonint;
    public boolean isNonint() { return isNonint; }
   
    /** Is this a valid effect? */
    public boolean isValid(Constraints constraints) { 
	    return true; 
    }

    public RPLs rpls;
    
    protected Effect(RPLs rpls, boolean isAtomic, boolean isNonint) {
	this.rpls = rpls;
	this.isNonint = isNonint;
	this.isAtomic = isAtomic && !isNonint;
    }
    

    /**
     * Do all the RPL and effect parameter substitutions implied by the bindings of t
     */
    public Effects substAllParams(Type t) {
	Effect e = Substitute.allRPLParams(this, t);
    	return e.substEffectParams(t.tsym.type.getEffectArguments(), 
    		t.getEffectArguments());
    }

    public Effect substRPLParams(Iterable<RPL> from, 
	    Iterable<RPL> to) {
	return this;
    }
    
    public Effect substTRParams(Iterable<Type> from, 
	    Iterable<Type> to) {
	return this;
    }
    
    public Effects substEffectParams(Iterable<Effects> from, 
	    Iterable<Effects> to) {
	return new Effects(this);
    }
    
    public Effect substRPLForVar(VarSymbol from, RPL to) {
	return this;
    }
    
    public Effect substVars(Iterable<VarSymbol> from, 
	    Iterable<VarSymbol> to) {
	return this;
    }
    
    public Effect substExpsForVars(Iterable<VarSymbol> from, 
	    Iterable<JCExpression> to) {
	return this;
    }
    
    public Effect substIndex(VarSymbol from, JCExpression to) {
	return substIndices(List.of(from), List.of(to));
    }
    
    public Effect substIndices(Iterable<VarSymbol> from, 
	    Iterable<JCExpression> to) {
	return this;
    }
    
    public Effect inEnvironment(Resolve rs, Env<AttrContext> env, 
	    boolean pruneLocalEffects) {
	return this;
    }
    
    public Effects asMemberOf(Type t, Types types) {
	return new Effects(this);
    }

    /**
     * Subeffects -- See Section 1.4.2 of the DPJ Tech Report
     */
    public abstract boolean isSubeffectOf(Effect e);
    
    public boolean isSubeffectOf(Effects effects) {
	// SE-UNION-1
	if (effects.isEmpty()) return false;
	Effect e = effects.first();
	if (this.isSubeffectOf(e)) return true;
	return this.isSubeffectOf(effects.without(e));
    }
    
    /**
     * SE-ATOMIC-1, SE-ATOMIC 2
     */
    public static boolean isSubeffectAtomic(Effect e1, Effect e2) {
	return e1.isAtomic() || !e2.isAtomic();
    }
    
    /**
     * SE-NONINT-1, SE-NONINT-2
     */
    public static boolean isSubeffectNonint(Effect e1, Effect e2) {
	return e2.isNonint() || !e1.isNonint();
    }
    
    /**
     * NONDET-ATOMIC
     */
    public static boolean isNondetAtomic(Effect e1, Effect e2) {
	return e1.isAtomic() && e2.isAtomic();
    }
    
    /**
     * Noninterfering effects -- See section 3.3 of Tech Report
     */
    public boolean isNoninterferingWith(Effect e, 
	    Constraints constraints, boolean atomicOK) {
	    if (atomicOK && isNondetAtomic(this, e)) {
		return true;
	    }
	    if (e instanceof CopyEffect) {
		    return true;
	    }
	    return false;
    }
    
    public boolean isNoninterferingWith(Effects effects,
	    Constraints constraints, boolean atomicOK) { 
	// NI-EMPTY
	if (effects.isEmpty()) return true;
	// NI-UNION
	Effect e = effects.first();
	return this.isNoninterferingWith(e, constraints, atomicOK) && 
		this.isNoninterferingWith(effects.without(e), constraints, atomicOK);
    }
   
    /**
     * Consistent effects --- s. 3.5 of DPJ-UR tech report
     */
    public boolean isConsistentWith(Effect e, Constraints constraints) {
	    return true;
    }

    public boolean isConsistentWith(Effects effects, Constraints constraints) {
	if (effects.isEmpty()) return true;
	Effect e = effects.first();
	return this.isConsistentWith(e, constraints) &&
		this.isConsistentWith(effects.without(e), constraints);
    }

    /**
     * Return this effect as it appears in an atomic statement
     */
    public  Effect inAtomic() {
	return this;
    }
    
    /**
     * Return this effect as it appears in a nonint statement
     */
    public Effect inNonint() {
	return this;
    }
	    
    /**
     * Capture the effect
     */
    public Effect capture() { return new CapturedEffect(this); }
    
    /** A class for read effects
     */
    public static class ReadEffect extends Effect {
	public RPL rpl;
	public ReadEffect(RPLs rpls, RPL rpl, boolean isAtomic,
		boolean isNonint) {
	    super(rpls, isAtomic, isNonint);
	    this.rpl = rpl;
	    if (!rpl.isAtomic()) this.isAtomic = false;
	}
	
	public boolean isSubeffectOf(Effect e) {
	    if (!isSubeffectAtomic(this, e)) return false;
	    if (!isSubeffectNonint(this, e)) return false;
	    if (e instanceof ReadEffect) {
		// SE-READS
		if (this.rpl.isIncludedIn(((ReadEffect) e).rpl))
		    return true;
	    }
	    if (e instanceof WriteEffect) {
		// SE-READS-WRITES
		if (this.rpl.isIncludedIn(((WriteEffect) e).rpl))
		    return true;
	    }	    
	    return false;
	}
	
	@Override
	public boolean isNoninterferingWith(Effect e, Constraints constraints,
		boolean atomicOK) {
	    if (super.isNoninterferingWith(e, constraints, atomicOK)) {
		    return true;
	    }
	    if (e instanceof ReadEffect) {
		// NI-READ
		return true;
	    }
	    if (e instanceof WriteEffect) {
		// NI-WRITE
		if (rpls.areDisjoint(this.rpl, ((WriteEffect) e).rpl, 
			constraints.disjointRPLs))
		    return true;
	    }
	    if (e instanceof InvocationEffect) {
		// NI-INVOKES-1
		if (this.isNoninterferingWith(((InvocationEffect) e).withEffects,
			constraints, atomicOK))
		    return true;
	    }
	    return false;
	}
	
	@Override
	public Effect substRPLParams(Iterable<RPL> from, Iterable<RPL> to) {
	    return new ReadEffect(rpls, rpl.substRPLParams(from, to),
		    this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect substTRParams(Iterable<Type> from, Iterable<Type> to) {
	    return new ReadEffect(rpls, rpl.substTRParams(from, to), 
		    this.isAtomic(), this.isNonint());
	}	    
	
	@Override
	public Effect substVars(Iterable<VarSymbol> from, Iterable<VarSymbol> to) {
	    return new ReadEffect(rpls, Substitute.iterable(RPLs.substVars, rpl, from, to), 
		    this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect substExpsForVars(Iterable<VarSymbol> from, 
		Iterable<JCExpression> to) {
	    return new ReadEffect(rpls, rpl.substExpsForVars(from, to), 
		    this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect substRPLForVar(VarSymbol from, RPL to) {
	    return new ReadEffect(rpls, this.rpl.substRPLForVar(from, to), 
		    this.isAtomic(), this.isNonint());
	}
		
	@Override
	public Effect substIndices(Iterable<VarSymbol> from,
		Iterable<JCExpression> to) {
	    return new ReadEffect(rpls, rpl.substIndices(from, to), 
		    this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effects asMemberOf(Type t, Types types) {
	    RPL memberRPL = rpl.asMemberOf(t, types);
	    return new Effects(memberRPL.equals(rpl) ? this : 
		new ReadEffect(rpls, memberRPL, this.isAtomic(),
			this.isNonint()));
	}
	
	@Override
	public Effect inEnvironment(Resolve rs, Env<AttrContext> env, 
		boolean pruneLocalEffects) {
	    RPL newRPL = rpl.inEnvironment(rs, env, pruneLocalEffects);
	    if (newRPL == null) return null;
	    return newRPL.equals(rpl) ? this : 
		new ReadEffect(rpls, newRPL, this.isAtomic(),
			this.isNonint());
	}

	@Override
	public Effect inAtomic() {
	    if (this.isAtomic() || this.isNonint()) return this;
	    return new ReadEffect(this.rpls, this.rpl, 
		    true, false);
	}
	
	@Override
	public Effect inNonint() {
	    if (this.isNonint()) return this;
	    return new ReadEffect(this.rpls, this.rpl,
		   false, true);
	}
	
	public String toString() {
	    StringBuffer sb = new StringBuffer();
	    sb.append("reads ");
	    if (this.isAtomic())
		sb.append("atomic ");
	    sb.append(rpl);
	    return sb.toString();
	}
	
	@Override
	public int hashCode() {
	    return 3 * this.rpl.hashCode();
	}
	
	public boolean equals(Object o) {
	    if (!(o instanceof ReadEffect))
		return false;
	    return this.rpl.equals(((ReadEffect) o).rpl);
	}

    }
    
    /** A class for write effects
     */
    public static class WriteEffect extends Effect {
	
	public RPL rpl;
	
	public WriteEffect(RPLs rpls, RPL rpl, boolean isAtomic,
		boolean isNonint) {
	    super(rpls, isAtomic, isNonint);
	    this.rpl = rpl;
	    if (!rpl.isAtomic()) this.isAtomic = false;
	}
	
	public boolean isSubeffectOf(Effect e) {
	    if (!isSubeffectAtomic(this, e)) return false;
	    if (!isSubeffectNonint(this, e)) return false;
	    if (e instanceof WriteEffect) {
		// SE-WRITES
		if (this.rpl.isIncludedIn(((WriteEffect) e).rpl))
		    return true;
	    }	    
	    return false;
	}
	
	@Override
	public boolean isNoninterferingWith(Effect e, 
		Constraints constraints, boolean atomicOK) {
	    if (super.isNoninterferingWith(e, constraints, atomicOK)) {
		    return true;
	    }
	    if (e instanceof ReadEffect) {
		// NI-READ
		if (rpls.areDisjoint(this.rpl, ((ReadEffect) e).rpl, 
			constraints.disjointRPLs))
		    return true;
	    }
	    if (e instanceof WriteEffect) {
		// NI-WRITE
		if (rpls.areDisjoint(this.rpl, ((WriteEffect) e).rpl, 
			constraints.disjointRPLs))
		    return true;
	    }
	    if (e instanceof InvocationEffect) {
		// NI-INVOKES-1
		if (this.isNoninterferingWith(((InvocationEffect) e).withEffects,
			constraints, atomicOK))
		    return true;
	    }
	    return false;
	}
	
	@Override
	public Effect substRPLParams(Iterable<RPL> from, Iterable<RPL> to) {
	    return new WriteEffect(rpls, rpl.substRPLParams(from, to), 
		    this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect substTRParams(Iterable<Type> from, Iterable<Type> to) {
	    return new WriteEffect(rpls, rpl.substTRParams(from, to), 
		    this.isAtomic(), this.isNonint());
	}	    

	@Override
	public Effect substVars(Iterable<VarSymbol> from, Iterable<VarSymbol> to) {
	    return new WriteEffect(rpls, 
		    Substitute.iterable(RPLs.substVars, this.rpl, from, to), 
		    this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect substExpsForVars(Iterable<VarSymbol> from, Iterable<JCExpression> to) {
	    return new WriteEffect(rpls, rpl.substExpsForVars(from, to), 
		    this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect substRPLForVar(VarSymbol from, RPL to) {
	    return new WriteEffect(rpls, this.rpl.substRPLForVar(from, to), 
		    this.isAtomic(), this.isNonint());
	}

	@Override
	public Effect substIndices(Iterable<VarSymbol> from, 
		Iterable<JCExpression> to) {
	    Effect result = new WriteEffect(rpls, rpl.substIndices(from, to),
		    this.isAtomic(), this.isNonint());
	    return result;
	}
	
	@Override
	public Effects asMemberOf(Type t, Types types) {
	    RPL memberRPL = rpl.asMemberOf(t, types);
	    return new Effects(memberRPL.equals(rpl) ? this : 
		new WriteEffect(rpls, memberRPL, this.isAtomic(),
			this.isNonint()));
	}
	
	@Override
	public Effect inEnvironment(Resolve rs, Env<AttrContext> env,
		boolean pruneLocalEffects) {
	    RPL newRPL = rpl.inEnvironment(rs, env, pruneLocalEffects);
	    if (newRPL == null) return null;
	    return newRPL.equals(rpl) ? this : 
		new WriteEffect(rpls, newRPL, this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect inAtomic() {
	    if (this.isAtomic() || this.isNonint()) return this;
	    return new WriteEffect(this.rpls, this.rpl, 
		    true, false);
	}

	@Override
	public Effect inNonint() {
	    if (this.isNonint()) return this;
	    return new WriteEffect(this.rpls, this.rpl,
		    false, true);
	}
	
	@Override
	public int hashCode() {
	    return 7 * this.rpl.hashCode();
	}
	
	public boolean equals(Object o) {
	    if (!(o instanceof WriteEffect))
		return false;
	    return this.rpl.equals(((WriteEffect) o).rpl);
	}
	
	public String toString() {
	    StringBuffer sb = new StringBuffer();
	    sb.append("writes ");
	    if (this.isAtomic())
		sb.append("atomic ");
	    sb.append(rpl);
	    return sb.toString();
	}
	
    }
    
    /** A class for invocation effects
     */
    public static class InvocationEffect extends Effect {

	public MethodSymbol methSym;
	
	public Effects withEffects;
	
	public InvocationEffect (RPLs rpls, MethodSymbol methSym, Effects withEffects) {
	    super(rpls, false, false);
	    this.methSym = methSym;
	    this.withEffects = withEffects;
	}
	
	/**
	 * Invocation effects are never "automatically" atomic: they are governed
	 * by their withEffects.
	 */
	public boolean isAtomic() { return false; }

	public boolean isSubeffectOf(Effect e) {
	    if (!isSubeffectAtomic(this, e)) return false;
	    if (!isSubeffectNonint(this, e)) return false;
	    if (e instanceof InvocationEffect) {
		InvocationEffect ie = (InvocationEffect) e;
		// SE-INVOKES-1
		if (this.methSym == ie.methSym && this.withEffects.areSubeffectsOf(ie.withEffects))
		    return true;
	    }
	    Effects effects = new Effects();
	    effects.add(e);
	    if (this.withEffects.areSubeffectsOf(effects))
		return true;
	    return false;
	}
	
	@Override
	public boolean isNoninterferingWith(Effect e, Constraints constraints,
		boolean atomicOK) {
	    if (e.isNoninterferingWith(withEffects, constraints, atomicOK)) return true;
	    if (e instanceof InvocationEffect) {
		InvocationEffect ie = (InvocationEffect) e;
		if (Effects.areNoninterfering(withEffects, ie.withEffects,
			constraints, atomicOK)) {
		    return true;
		}
		if ((methSym == ie.methSym) && 
			((methSym.flags() & Flags.ISCOMMUTATIVE)) != 0) {
		    return true;
		}
	    }
	    return false;
	}
	
	@Override
	public boolean isSubeffectOf(Effects set) {
	    if (super.isSubeffectOf(set)) return true;
	    // SE-INVOKES-2
	    return (this.withEffects.areSubeffectsOf(set));
	}
	
	@Override
	public Effect substRPLParams(Iterable<RPL> from, Iterable<RPL> to) {
	    return new InvocationEffect(rpls, methSym, withEffects.substRPLParams(from, to));
	}
	
	@Override
	public Effect substTRParams(Iterable<Type> from, Iterable<Type> to) {
	    return new InvocationEffect(rpls, methSym, withEffects.substTRParams(from, to));
	}
	
	@Override
	public Effect substVars(Iterable<VarSymbol> from, 
		Iterable<VarSymbol> to) {
	    return new InvocationEffect(rpls, methSym, withEffects.substVars(from, to));
	}
	
	@Override
	public Effect substExpsForVars(Iterable<VarSymbol> from, 
		Iterable<JCExpression> to) {
	    return new InvocationEffect(rpls, methSym, withEffects.substExpsForVars(from, to));
	}
	
	@Override
	public Effect substRPLForVar(VarSymbol from, RPL to) {
	    return new InvocationEffect(rpls, methSym, withEffects.substRPLForVar(from, to));
	}
	
	@Override
	public Effect substIndices(Iterable<VarSymbol> from, 
		Iterable<JCExpression> to) {
	    return new InvocationEffect(rpls, methSym, withEffects.substIndices(from, to));
	}
	
	@Override
	public Effects asMemberOf(Type t, Types types) {
	    Effects memberEffects = withEffects.asMemberOf(t, types);
	    return new Effects((memberEffects == withEffects) ? 
		    this : new InvocationEffect(rpls, methSym, memberEffects));
	}
	
	@Override
	public Effect inEnvironment(Resolve rs, Env<AttrContext> env, boolean pruneLocalEffects) {
	    Effects newEffects = withEffects.inEnvironment(rs, env, pruneLocalEffects);
	    return (newEffects == withEffects) ?
		    this : new InvocationEffect(rpls, methSym, newEffects);
	}
	
	@Override
	public Effects substEffectParams(Iterable<Effects> from, 
		Iterable<Effects> to) {
	    Effects newEffects = withEffects.substEffectParams(from, to);
	    return new Effects(new InvocationEffect(rpls, methSym, newEffects));
	}
	
	@Override
	public Effect inAtomic() {
	    if (this.isAtomic() || this.isNonint()) return this;
	    Effects inAtomicEffects = withEffects.inAtomic();
	    return (inAtomicEffects == withEffects) ? this :
		new InvocationEffect(rpls, methSym, inAtomicEffects);
	}
	
	@Override
	public int hashCode() {
	    return 11 * this.methSym.hashCode() + this.withEffects.hashCode();
	}
	
	public boolean equals(Object o) {
	    if (!(o instanceof InvocationEffect))
		return false;
	    InvocationEffect ie = (InvocationEffect) o;
	    return this.methSym == ie.methSym && this.withEffects.equals(ie.withEffects);
	}
	
	public String toString() {
	    return "invokes " + methSym + " with " + withEffects;
	}
	
    }
    
    /** A class for variable effects
     */
    public static class VariableEffect extends Effect {
	public EffectParameterSymbol sym;
	
	public VariableEffect(EffectParameterSymbol sym) {
	    super(null, false, false);
	    this.sym = sym;
	}

	@Override
	public boolean equals(Object o) {
	    if (!(o instanceof VariableEffect)) return false;
	    VariableEffect ve = (VariableEffect) o;
	    return this.sym == ve.sym;
	}

	@Override
	public int hashCode() {
	    return 13 * this.sym.hashCode();
	}

	@Override
	public boolean isNoninterferingWith(Effect e,
		Constraints constraints, boolean atomicOK) {
	    if (super.isNoninterferingWith(e, constraints, atomicOK)) {
		return true;
	    }
	    for (Pair<Effects,Effects> constraint : constraints.noninterferingEffects) {
		if (this.isSubeffectOf(constraint.fst) &&
			e.isSubeffectOf(constraint.snd)) return true;
		if (this.isSubeffectOf(constraint.snd) &&
			e.isSubeffectOf(constraint.fst)) return true;
	    }
	    return false;
	}

	public final Effect WRITES_ROOT_STAR =
	    new WriteEffect(rpls, 
		    new RPL(List.of(RPLElement.ROOT_ELEMENT, RPLElement.STAR)), 
		    false, true);
	
	@Override
	public boolean isSubeffectOf(Effect e) {
	    if (!isSubeffectAtomic(this, e)) return false;
	    if (!isSubeffectNonint(this, e)) return false;
	    // TODO: We could also allow subeffect constraints
	    if (WRITES_ROOT_STAR.isSubeffectOf(e)) return true;
	    return this.equals(e);
	}
	
	@Override
	public Effects asMemberOf(Type t, Types types) {
	    Symbol owner = this.sym.enclClass();
	    Type base = types.asOuterSuper(t, owner);
            return this.substEffectParams(base.tsym.type.getEffectArguments(),
        	    base.getEffectArguments());
	}
	
	@Override
	public Effects substEffectParams(Iterable<Effects> from, 
		Iterable<Effects> to) {
	    Iterator<Effects> fromI = from.iterator();
	    Iterator<Effects> toI = to.iterator();
	    while (fromI.hasNext() && toI.hasNext()) {
		Effects fromElt = fromI.next();
		Effects toElt = toI.next();
		VariableEffect ve = fromElt.asVariableEffect();
		assert(ve != null);
		if (ve.equals(this)) return toElt;
	    }
	    return new Effects(this);
	}
	
	@Override
	public Effect capture() { return this; }

	public String toString() {
	    return "effect " + sym;
	}
    }
    
    /** A class for rename effects
     */
    public static class RenameEffect extends Effect {
	public RenameEffect(RPLs rpls) {
	    super(rpls, false, false);
	}

	@Override	
	public boolean isSubeffectOf(Effect e) {
	    if (e instanceof RenameEffect) {
		return true;
	    }
	    return false;
	}

	@Override
	public boolean isConsistentWith(Effect e, Constraints constraints) {
		return !(e instanceof CopyEffect);
	}
	
	@Override
	public String toString() {
	    return "renames";
	}
	
	@Override
	public int hashCode() {
	    return 17;
	}
	
	public boolean equals(Object o) {
	    return o instanceof RenameEffect;
	}

    }
   
    public static class CopyEffect extends Effect {
	public RPL source;
	public RPL target;
	public CopyEffect(RPLs rpls, RPL source, RPL target, 
			boolean isAtomic, boolean isNonint) {
	    super(rpls, isAtomic, isNonint);
	    this.source = source;
	    this.target = target;
	    if (!source.isAtomic() || !target.isAtomic()) 
		    this.isAtomic = false;
	}
	
	@Override
	public boolean isSubeffectOf(Effect e) {
	    if (!isSubeffectAtomic(this, e)) return false;
	    if (!isSubeffectNonint(this, e)) return false;
	    if (!(e instanceof CopyEffect)) return false;
	    CopyEffect ce = (CopyEffect) e;
	    if (!source.isUnique()) return false;
	    if (!source.uniquePrefix().equals(target.uniquePrefix()))
		    return false;
	    return this.source.isIncludedIn(ce.target);
	}

	@Override
	public boolean isValid(Constraints constraints) {
	    // EFFECT-COPIES
	    if (!source.isUnique() || !target.isUnique())
		    return false;
	    return rpls.unequalUniquePrefix(source, target,
			    constraints.unequalPrefixes);
	}

	@Override
	public boolean isNoninterferingWith(Effect e, Constraints constraints,
		boolean atomicOK) {
	    return true;
	}
	
	@Override
	public boolean isConsistentWith(Effect e, Constraints constraints) {
		if (e instanceof RenameEffect) return false;
		if (!(e instanceof CopyEffect)) return true;
		CopyEffect ce = (CopyEffect) e;
		// PAIR-EQUAL-PREFIX
		if (rpls.areDisjoint(this.source, ce.source, 
					constraints.disjointRPLs) &&
			rpls.equalUniquePrefix(this.source, ce.source) &&
			rpls.equalUniquePrefix(ce.source, this.target)) {
			return true;
		}	
		// PAIR-UNEQUAL-PREFIX
		if (rpls.unequalUniquePrefix(this.source, ce.source,
				constraints.unequalPrefixes) &&
			rpls.unequalUniquePrefix(this.source, ce.target,
				constraints.unequalPrefixes) &&
			rpls.unequalUniquePrefix(this.target, ce.target,
				constraints.unequalPrefixes) &&
			rpls.unequalUniquePrefix(ce.target, this.source,
				constraints.unequalPrefixes)) {
			return true;
		}
		return false;
	}

	@Override
	public Effect substRPLParams(Iterable<RPL> from, Iterable<RPL> to) {
	    return new CopyEffect(rpls, source.substRPLParams(from, to),
		    target.substRPLParams(from, to), this.isAtomic(), 
		    this.isNonint());
	}
	
	@Override
	public Effect substTRParams(Iterable<Type> from, Iterable<Type> to) {
	    return new CopyEffect(rpls, source.substTRParams(from, to), 
		    target.substTRParams(from, to), this.isAtomic(), 
		    this.isNonint());
	}	    
	
	@Override
	public Effect substVars(Iterable<VarSymbol> from, Iterable<VarSymbol> to) {
	    return new CopyEffect(rpls, 
			Substitute.iterable(RPLs.substVars, source, from, to), 
		    	Substitute.iterable(RPLs.substVars, target, from, to),
		       	this.isAtomic(), this.isNonint());
	}
	
	@Override
	public Effect substExpsForVars(Iterable<VarSymbol> from, 
		Iterable<JCExpression> to) {
	    return new CopyEffect(rpls, source.substExpsForVars(from, to), 
		    target.substExpsForVars(from, to), this.isAtomic(), 
		    this.isNonint());
	}
	
	@Override
	public Effect substRPLForVar(VarSymbol from, RPL to) {
	    return new CopyEffect(rpls, this.source.substRPLForVar(from, to), 
		    this.target.substRPLForVar(from, to),
		    this.isAtomic(), this.isNonint());
	}
		
	@Override
	public Effect substIndices(Iterable<VarSymbol> from,
		Iterable<JCExpression> to) {
	    return new CopyEffect(rpls, source.substIndices(from, to), 
		    target.substIndices(from, to), this.isAtomic(), 
		    this.isNonint());
	}
	
	@Override
	public Effects asMemberOf(Type t, Types types) {
	    RPL newSource = source.asMemberOf(t, types);
	    RPL newTarget = target.asMemberOf(t, types);
	    if (newSource.equals(source) && newTarget.equals(target))
		    return new Effects(this);
	    return new Effects(new CopyEffect(rpls, newSource, newTarget,
				this.isAtomic(), this.isNonint()));
	}
	
	@Override
	public Effect inEnvironment(Resolve rs, Env<AttrContext> env, 
		boolean pruneLocalEffects) {
	    RPL newSource = source.inEnvironment(rs, env, pruneLocalEffects);
	    RPL newTarget = target.inEnvironment(rs, env, pruneLocalEffects);
	    if (newSource == null && newTarget == null) return null;
	    if (newSource.equals(source) && newTarget.equals(target))
		    return this;
	    return new CopyEffect(rpls, source, target, this.isAtomic(),
			    this.isNonint());
	}

	@Override
	public Effect inAtomic() {
	    if (this.isAtomic() || this.isNonint()) return this;
	    return new CopyEffect(this.rpls, this.source,
			this.target, true, false);
	}
	
	@Override
	public Effect inNonint() {
	    if (this.isNonint()) return this;
	    return new CopyEffect(this.rpls, this.source,
			this.target, false, true);
	}
	
	public String toString() {
	    StringBuffer sb = new StringBuffer();
	    sb.append("copies ");
	    sb.append(source);
	    sb.append(" to ");
	    sb.append(target);
	    return sb.toString();
	}
	
	@Override
	public int hashCode() {
	    return 19 * (this.source.hashCode() + this.target.hashCode());
	}
	
	public boolean equals(Object o) {
	    if (!(o instanceof CopyEffect))
		return false;
	    CopyEffect ce = (CopyEffect) o;
	    return this.source.equals(ce.source) &&
		    this.target.equals(ce.target);
	}

    }

    /** A class for captured effects
     */
    public static class CapturedEffect extends Effect {

	public Effect upperBound;
	
	protected CapturedEffect(Effect upperBound) {
	    super(null, false, false);
	    this.upperBound = upperBound;
	}

	@Override
	public boolean isNoninterferingWith(Effect e, Constraints constraints,
		boolean atomicOK) {
	    return e.isNoninterferingWith(upperBound, constraints, atomicOK);
	}

	@Override
	public boolean isSubeffectOf(Effect e) {
	    return upperBound.isSubeffectOf(e);
	}
	
	@Override
	public Effect capture() { return this; }
	
	public String toString() {
	    return "capture of " + upperBound;
	}
    }
}
