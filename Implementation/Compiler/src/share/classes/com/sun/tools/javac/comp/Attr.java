/*
 * Copyright 1999-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.comp;

import static com.sun.tools.javac.code.Flags.ABSTRACT;
import static com.sun.tools.javac.code.Flags.ANNOTATION;
import static com.sun.tools.javac.code.Flags.BLOCK;
import static com.sun.tools.javac.code.Flags.COMPOUND;
import static com.sun.tools.javac.code.Flags.DEPRECATED;
import static com.sun.tools.javac.code.Flags.ENUM;
import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.GENERATEDCONSTR;
import static com.sun.tools.javac.code.Flags.HASINIT;
import static com.sun.tools.javac.code.Flags.INTERFACE;
import static com.sun.tools.javac.code.Flags.NATIVE;
import static com.sun.tools.javac.code.Flags.NOOUTERTHIS;
import static com.sun.tools.javac.code.Flags.PROPRIETARY;
import static com.sun.tools.javac.code.Flags.PUBLIC;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Flags.UNATTRIBUTED;
import static com.sun.tools.javac.code.Flags.VARARGS;
import static com.sun.tools.javac.code.Kinds.AMBIGUOUS;
import static com.sun.tools.javac.code.Kinds.EFFECT;
import static com.sun.tools.javac.code.Kinds.ERR;
import static com.sun.tools.javac.code.Kinds.ERRONEOUS;
import static com.sun.tools.javac.code.Kinds.MTH;
import static com.sun.tools.javac.code.Kinds.NIL;
import static com.sun.tools.javac.code.Kinds.PCK;
import static com.sun.tools.javac.code.Kinds.RPL_ELT;
import static com.sun.tools.javac.code.Kinds.TYP;
import static com.sun.tools.javac.code.Kinds.VAL;
import static com.sun.tools.javac.code.Kinds.VAR;
import static com.sun.tools.javac.code.TypeTags.ARRAY;
import static com.sun.tools.javac.code.TypeTags.BYTE;
import static com.sun.tools.javac.code.TypeTags.CLASS;
import static com.sun.tools.javac.code.TypeTags.ERROR;
import static com.sun.tools.javac.code.TypeTags.FORALL;
import static com.sun.tools.javac.code.TypeTags.INT;
import static com.sun.tools.javac.code.TypeTags.METHOD;
import static com.sun.tools.javac.code.TypeTags.PACKAGE;
import static com.sun.tools.javac.code.TypeTags.TYPEVAR;
import static com.sun.tools.javac.code.TypeTags.VOID;
import static com.sun.tools.javac.code.TypeTags.WILDCARD;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.tools.javac.code.BoundKind;
import com.sun.tools.javac.code.Constraints;
import com.sun.tools.javac.code.Effect;
import com.sun.tools.javac.code.Effects;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.RPL;
import com.sun.tools.javac.code.RPLElement;
import com.sun.tools.javac.code.RPLElement.NameRPLElement;
import com.sun.tools.javac.code.RPLElement.RPLParameterElement;
import com.sun.tools.javac.code.RPLElement.VarRPLElement;
import com.sun.tools.javac.code.RPLs;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Substitute.AsMemberOf;
import com.sun.tools.javac.code.Substitute.SubstIndex;
import com.sun.tools.javac.code.Substitute.SubstRPLForVar;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.EffectParameterSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.OperatorSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.RegionNameSymbol;
import com.sun.tools.javac.code.Symbol.RegionParameterSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.ErrorType;
import com.sun.tools.javac.code.Type.ForAll;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.DPJAtomic;
import com.sun.tools.javac.tree.JCTree.DPJCobegin;
import com.sun.tools.javac.tree.JCTree.DPJEffect;
import com.sun.tools.javac.tree.JCTree.DPJFinish;
import com.sun.tools.javac.tree.JCTree.DPJForLoop;
import com.sun.tools.javac.tree.JCTree.DPJNonint;
import com.sun.tools.javac.tree.JCTree.DPJParamInfo;
import com.sun.tools.javac.tree.JCTree.DPJRegionDecl;
import com.sun.tools.javac.tree.JCTree.DPJRegionParameter;
import com.sun.tools.javac.tree.JCTree.DPJRegionPathList;
import com.sun.tools.javac.tree.JCTree.DPJRegionPathListElt;
import com.sun.tools.javac.tree.JCTree.DPJRenames;
import com.sun.tools.javac.tree.JCTree.DPJUniqueRegionDecl;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCArrayAccess;
import com.sun.tools.javac.tree.JCTree.JCArrayTypeTree;
import com.sun.tools.javac.tree.JCTree.JCAssert;
import com.sun.tools.javac.tree.JCTree.JCAssign;
import com.sun.tools.javac.tree.JCTree.JCAssignOp;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCBreak;
import com.sun.tools.javac.tree.JCTree.JCCase;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCConditional;
import com.sun.tools.javac.tree.JCTree.JCContinue;
import com.sun.tools.javac.tree.JCTree.JCDoWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCEnhancedForLoop;
import com.sun.tools.javac.tree.JCTree.JCErroneous;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCForLoop;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCInstanceOf;
import com.sun.tools.javac.tree.JCTree.JCLabeledStatement;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewArray;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCReturn;
import com.sun.tools.javac.tree.JCTree.JCSkip;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCSwitch;
import com.sun.tools.javac.tree.JCTree.JCSynchronized;
import com.sun.tools.javac.tree.JCTree.JCThrow;
import com.sun.tools.javac.tree.JCTree.JCTry;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCUnary;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree.JCWhileLoop;
import com.sun.tools.javac.tree.JCTree.JCWildcard;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.Position;
import com.sun.tools.javac.util.Warner;

/** This is the main context-dependent analysis phase in GJC. It
 *  encompasses name resolution, type checking and constant folding as
 *  subtasks. Some subtasks involve auxiliary classes.
 *  @see Check
 *  @see Resolve
 *  @see ConstFold
 *  @see Infer        ListBuffer<T> lb = new ListBuffer<T>();
        for (T tree: trees)
            lb.append(copy(tree, p));
        return lb.toList();

 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Attr extends JCTree.Visitor {
    protected static final Context.Key<Attr> attrKey =
        new Context.Key<Attr>();

    public final Name.Table names;
    final Log log;
    final protected Symtab syms;
    public final Resolve rs;
    final Check chk;
    final MemberEnter memberEnter;
    final TreeMaker make;
    final ConstFold cfolder;
    final Enter enter;
    final Target target;
    public final Types types;
    final Annotate annotate;
    final RPLs rpls;
    final DPJAttrPrePass dpjAttrPrePass;
    
    /**
     * A small pass that happens after Enter and before Attr.  It scans the tree and
     * does the following: 
     * 
     * 1. Resolve all declared method effects.
     * 
     * 2. Attribute and record RPL and effect constraints for class definitions.
     * 
     * 3. Check satisfaction of RPL and effect constraints for types appearing in
     *    class members.
     * 
     * Because RPLs use global symbols, these operations must wait until after Enter
     * is finished, or the symbols they need may not be in scope.  Yet they cannot 
     * happen during attribution, because their results are needed by attribution 
     * and/or effect checking (which is interleaved with attribution on a 
     * class-by-class basis).  So we insert a pass between Enter and Attr to take 
     * care of these things.
     *
     * @author Rob Bocchino
     */    
    public static class DPJAttrPrePass extends EnvScanner {
	protected static final Context.Key<DPJAttrPrePass> dpjAttrPrePassKey =
	    new Context.Key<DPJAttrPrePass>();

	Attr attr;
	RPLs rpls;
	
	public static DPJAttrPrePass instance(Context context) {
	    DPJAttrPrePass instance = context.get(dpjAttrPrePassKey);
	    if (instance == null)
		instance = new DPJAttrPrePass(context);
	    return instance;
	}
	
	protected DPJAttrPrePass(Context context) {
	    super(context);
	    context.put(dpjAttrPrePassKey, this);
	    attr = Attr.instance(context);
	    rpls = RPLs.instance(context);
	}
	
        @Override public void visitVarDef(JCVariableDecl tree) {
            Type varType = tree.vartype.type;
            if (varType != null) {
                // Compute the cell types for all field types now.                                                          
                attr.computeCellType(parentEnv, varType.tsym, varType);
            }
            super.visitVarDef(tree);
        }

	@Override
	public void visitMethodDef(JCMethodDecl tree) {
	    MethodSymbol m = tree.sym;
	    if (m != null) {
		Scope enclScope = enter.enterScope(parentEnv);
		Env<AttrContext> savedEnv = parentEnv;

		// Make a new environment for the method scope
		parentEnv = memberEnter.methodEnv(tree, parentEnv);	
		
	        // Enter type, region, and effect parameters into the local scope.
	        attr.enterMethodParams(tree, parentEnv);

	        // Enter value parameters into the local method scope.
	        for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
	            scan(l.head);
	        }
	        attr.env = parentEnv;

	        if (tree.effects != null) {
	            // Attribute method effects now!
	            attr.visitEffect(tree.effects);

	            // Store resolved effects in the method symbol
	            m.effects = tree.effects.effects;
	            
	            // Set default effect for implicit constructors
	            if ((m.effects == Effects.UNKNOWN) && attr.inConstructor(parentEnv))
	        	m.effects = attr.defaultEffects();
	        }
	        
		parentEnv = savedEnv;
	    }
	    // Finish scanning the tree
	    super.visitMethodDef(tree);
	}
	
	@Override
	public void visitClassDef(JCClassDecl tree) {

	    // Attribute and set the region param constraints here.
	    Env<AttrContext> env = enter.typeEnvs.get(tree.sym);
	    // Ignore local inner classes initially (they will be processed after they're entered during Attr)
	    if (env == null) return;
	    attr.enterClassParams(tree, env);
	    
	    // Finish scanning the tree
	    super.visitClassDef(tree);
	}
	
	@Override
	public void visitTypeApply(JCTypeApply tree) {
	    if (tree.functor.getSymbol() instanceof ClassSymbol) {
		ClassType ct = (ClassType) tree.type;
		ClassSymbol cs = (ClassSymbol) tree.functor.getSymbol();
        	if (cs.constraints != null) {
        	    // Check that disjointness constraints on region args are satisfied
        	    if (!rpls.disjointnessConstraintsAreSatisfied(cs.constraints.disjointRPLs, 
        		    ct.tsym.type.getRPLArguments(), ct.getRPLArguments(), 
        		    parentEnv.info.constraints.disjointRPLs)) {
        		enter.log.warning(tree, "rpl.constraints");
        	    }
        	    // Check that nonint constraints on effect vars are satisfied
        	    if (!Effects.nonintConstraintsAreSatisfied(cs.constraints.noninterferingEffects,
        		    ct, parentEnv.info.constraints)) {
        		enter.log.warning(tree, "effect.constraints");
        	    }
        	}
        	if (!rpls.atomicConstraintsAreSatisfied(ct.tsym.type.getRPLArguments(),
        		ct.getRPLArguments())) {
        	    enter.log.error(tree, "atomic.constraints");
        	}
	    }
	    super.visitTypeApply(tree);
	}
	
    }
    
    public static Attr instance(Context context) {
        Attr instance = context.get(attrKey);
        if (instance == null)
            instance = new Attr(context);
        return instance;
    }

    protected Attr(Context context) {
        context.put(attrKey, this);

        names = Name.Table.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        chk = Check.instance(context);
        memberEnter = MemberEnter.instance(context);
        make = TreeMaker.instance(context);
        enter = Enter.instance(context);
        cfolder = ConstFold.instance(context);
        target = Target.instance(context);
        types = Types.instance(context);
        annotate = Annotate.instance(context);
        rpls = RPLs.instance(context);
        dpjAttrPrePass = DPJAttrPrePass.instance(context);
        
        Options options = Options.instance(context);

        Source source = Source.instance(context);
        allowGenerics = source.allowGenerics();
        allowVarargs = source.allowVarargs();
        allowEnums = source.allowEnums();
        allowBoxing = source.allowBoxing();
        allowCovariantReturns = source.allowCovariantReturns();
        allowAnonOuterThis = source.allowAnonOuterThis();
        relax = (options.get("-retrofit") != null ||
                 options.get("-relax") != null);
        useBeforeDeclarationWarning = options.get("useBeforeDeclarationWarning") != null;
    }

    /** Switch: Warn about unsound casts?
     */
    boolean warnCast;
    
    /** Switch: relax some constraints for retrofit mode.
     */
    boolean relax;

    /** Switch: support generics?
     */
    boolean allowGenerics;

    /** Switch: allow variable-arity methods.
     */
    boolean allowVarargs;

    /** Switch: support enums?
     */
    boolean allowEnums;

    /** Switch: support boxing and unboxing?
     */
    boolean allowBoxing;

    /** Switch: support covariant result types?
     */
    boolean allowCovariantReturns;

    /** Switch: allow references to surrounding object from anonymous
     * objects during constructor call?
     */
    boolean allowAnonOuterThis;

    /**
     * Switch: warn about use of variable before declaration?
     * RFE: 6425594
     */
    boolean useBeforeDeclarationWarning;

    /** Check kind and type of given tree against protokind and prototype.
     *  If check succeeds, store type in tree and return it.
     *  If check fails, store errType in tree and return it.
     *  No checks are performed if the prototype is a method type.
     *  Its not necessary in this case since we know that kind and type
     *  are correct.
     *
     *  @param tree     The tree whose kind and type is checked
     *  @param owntype  The computed type of the tree
     *  @param ownkind  The computed kind of the tree
     *  @param pkind    The expected kind (or: protokind) of the tree
     *  @param pt       The expected type (or: prototype) of the tree
     */
    Type check(JCTree tree, Type owntype, int ownkind, int pkind, Type pt) {
	if (ownkind == VAR && pkind == RPL_ELT) {
	    // OK, using a variable as a z region RPL element
	} else
	if (owntype.tag != ERROR && pt.tag != METHOD && pt.tag != FORALL) {
            if ((ownkind & ~pkind) == 0) {
                owntype = chk.checkType(tree.pos(), owntype, pt);
            } else {
                log.error(tree.pos(), "unexpected.type",
                          Resolve.kindNames(pkind),
                          Resolve.kindName(ownkind));
                owntype = syms.errType;
            }
        }
        tree.type = owntype;
        return owntype;
    }

    /** Is given blank final variable assignable, i.e. in a scope where it
     *  may be assigned to even though it is final?
     *  @param v      The blank final variable.
     *  @param env    The current environment.
     */
    boolean isAssignableAsBlankFinal(VarSymbol v, Env<AttrContext> env) {
        Symbol owner = env.info.scope.owner;
           // owner refers to the innermost variable, method or
           // initializer block declaration at this point.
        return
            v.owner == owner
            ||
            ((owner.name == names.init ||    // i.e. we are in a constructor
              owner.kind == VAR ||           // i.e. we are in a variable initializer
              (owner.flags() & BLOCK) != 0)  // i.e. we are in an initializer block
             &&
             v.owner == owner.owner
             &&
             ((v.flags() & STATIC) != 0) == Resolve.isStatic(env));
    }

    /** Check that variable can be assigned to.
     *  @param pos    The current source code position.
     *  @param v      The assigned varaible
     *  @param base   If the variable is referred to in a Select, the part
     *                to the left of the `.', null otherwise.
     *  @param env    The current environment.
     */
    void checkAssignable(DiagnosticPosition pos, VarSymbol v, JCTree base, Env<AttrContext> env) {
        if ((v.flags() & FINAL) != 0 &&
            ((v.flags() & HASINIT) != 0
             ||
             !((base == null ||
               (base.getTag() == JCTree.IDENT && TreeInfo.name(base) == names._this)) &&
               isAssignableAsBlankFinal(v, env)))) {
            log.error(pos, "cant.assign.val.to.final.var", v);
        }
    }

    /** Does tree represent a static reference to an identifier?
     *  It is assumed that tree is either a SELECT or an IDENT.
     *  We have to weed out selects from non-type names here.
     *  @param tree    The candidate tree.
     */
    boolean isStaticReference(JCTree tree) {
        if (tree.getTag() == JCTree.SELECT) {
            Symbol lsym = TreeInfo.symbol(((JCFieldAccess) tree).selected);
            if (lsym == null || lsym.kind != TYP) {
                return false;
            }
        }
        return true;
    }

    /** Is this symbol a type?
     */
    static boolean isType(Symbol sym) {
        return sym != null && sym.kind == TYP;
    }

    /** The current `this' symbol.
     *  @param env    The current environment.
     */
    Symbol thisSym(DiagnosticPosition pos, Env<AttrContext> env) {
        return rs.resolveSelf(pos, env, env.enclClass.sym, names._this);
    }

    /** Attribute a parsed identifier.
     * @param tree Parsed identifier name
     * @param topLevel The toplevel to use
     */
    public Symbol attribIdent(JCTree tree, JCCompilationUnit topLevel) {
        Env<AttrContext> localEnv = enter.topLevelEnv(topLevel);
        localEnv.enclClass = make.ClassDef(make.Modifiers(0),
                                           syms.errSymbol.name,
                                           null, null, null, null, null);
        localEnv.enclClass.sym = syms.errSymbol;
        return tree.accept(identAttributer, localEnv);
    }
    // where
        private TreeVisitor<Symbol,Env<AttrContext>> identAttributer = new IdentAttributer();
        private class IdentAttributer extends SimpleTreeVisitor<Symbol,Env<AttrContext>> {
            @Override
            public Symbol visitMemberSelect(MemberSelectTree node, Env<AttrContext> env) {
                Symbol site = visit(node.getExpression(), env);
                if (site.kind == ERR)
                    return site;
                Name name = (Name)node.getIdentifier();
                if (site.kind == PCK) {
                    env.toplevel.packge = (PackageSymbol)site;
                    return rs.findIdentInPackage(env, (TypeSymbol)site, name, TYP | PCK);
                } else {
                    env.enclClass.sym = (ClassSymbol)site;
                    return rs.findMemberType(env, site.asType(), name, (TypeSymbol)site);
                }
            }
    
            @Override
            public Symbol visitIdentifier(IdentifierTree node, Env<AttrContext> env) {
                return rs.findIdent(env, (Name)node.getName(), TYP | PCK);
            }
        }

    public Type coerce(Type etype, Type ttype) {
        return cfolder.coerce(etype, ttype);
    }

    public Type attribType(JCTree node, TypeSymbol sym) {
        Env<AttrContext> env = enter.typeEnvs.get(sym);
        Env<AttrContext> localEnv = env.dup(node, env.info.dup());
        return attribTree(node, localEnv, Kinds.TYP, Type.noType);
    }  

    public Env<AttrContext> attribExprToTree(JCTree expr, Env<AttrContext> env, JCTree tree) {
        breakTree = tree;
        JavaFileObject prev = log.useSource(null);
        try {
            attribExpr(expr, env);
        } catch (BreakAttr b) {
            return b.env;
        } finally {
            breakTree = null;
            log.useSource(prev);
        }
        return env;
    }    

    public Env<AttrContext> attribStatToTree(JCTree stmt, Env<AttrContext> env, JCTree tree) {
        breakTree = tree;
        JavaFileObject prev = log.useSource(null);
        try {
            attribStat(stmt, env);
        } catch (BreakAttr b) {
            return b.env;
        } finally {
            breakTree = null;
            log.useSource(prev);
        }
        return env;
    }
    
    private JCTree breakTree = null;
    
    private static class BreakAttr extends RuntimeException {
        static final long serialVersionUID = -6924771130405446405L;
        private Env<AttrContext> env;
        private BreakAttr(Env<AttrContext> env) {
            this.env = env;
        }
    }
   
    /**
     * Convert an expression to an RPL.  Used in substituting for 'this' in expressions
     * like e.f, where f is declared in region this.
     */
    public RPL exprToRPL(JCExpression tree) {
	if (tree == null) return null;
	// Conversion only makes sense for class types
	if (!(tree.type instanceof ClassType)) return null;
	Symbol sym = tree.getSymbol();
	// If the expression is a final local variable, use it as the RPL
	if (sym instanceof VarSymbol && 
		(sym.owner.kind == Kinds.MTH || sym.name.equals(names._this))
		&& (sym.flags() & Flags.FINAL) != 0) {
	    return new RPL(List.<RPLElement>of(new RPLElement.VarRPLElement((VarSymbol) sym)));
	}
	// Otherwise, use the owner region
	RPL owner = ((ClassType) tree.type).getOwner();
	return new RPL(owner.elts.append(RPLElement.STAR));
    }

    /**
     * Are we inside a constructor?  If so, we need to keep track of the effects
     * visible at the constructor interface, so we can check the constructor
     * effect summary.
     * @param env
     * @return
     */
    public boolean inConstructor(Env<AttrContext> env) {
	Symbol owner = env.info.scope.owner;
	return (owner.kind == MTH) && (owner.name == names.init);
    }
    
    /**
     * Are we inside a class definition?
     */
    public boolean inClassDef(Env<AttrContext> env) {
	Symbol owner = env.info.scope.owner;
	return (owner.kind == TYP);
    }
    

/* ************************************************************************
 * Visitor methods
 *************************************************************************/

    /** Visitor argument: the current environment.
     */
    protected Env<AttrContext> env;
    
    // FIXME: Added just to let the porting tool access method entry environments.
    protected Env<AttrContext> methodEntryEnv; 

    /** Visitor argument: the currently expected proto-kind.
     */
    int pkind;

    /** Visitor argument: the currently expected proto-type.
     */
    Type pt;

    /** Visitor result: the computed type.
     */
    Type result;

    /** Visitor method: attribute a tree, catching any completion failure
     *  exceptions. Return the tree's type.
     *
     *  @param tree    The tree to be visited.
     *  @param env     The environment visitor argument.
     *  @param pkind   The protokind visitor argument.
     *  @param pt      The prototype visitor argument.
     */
    Type attribTree(JCTree tree, Env<AttrContext> env, int pkind, Type pt) {
	Env<AttrContext> prevEnv = this.env;
        int prevPkind = this.pkind;
        Type prevPt = this.pt;
        try {
            this.env = env;
            this.pkind = pkind;
            this.pt = pt;
            tree.accept(this);
            if (tree == breakTree)
                throw new BreakAttr(env);
    	    return result;
        } catch (CompletionFailure ex) {
            tree.type = syms.errType;
            return chk.completionError(tree.pos(), ex);
        } finally {
            this.env = prevEnv;
            this.pkind = prevPkind;
            this.pt = prevPt;
        }
    }

    /** Derived visitor method: attribute an expression tree.
     */
    public Type attribExpr(JCTree tree, Env<AttrContext> env, Type pt) {
        return attribTree(tree, env, VAL, pt.tag != ERROR ? pt : Type.noType);
    }

    /** Derived visitor method: attribute an expression tree with
     *  no constraints on the computed type.
     */
    Type attribExpr(JCTree tree, Env<AttrContext> env) {
        return attribTree(tree, env, VAL, Type.noType);
    }

    /** Derived visitor method: attribute a type tree.
     */
    Type attribType(JCTree tree, Env<AttrContext> env) {
	Type result = attribTree(tree, env, TYP, Type.noType);
        return result;
    }

    /** Derived visitor method: attribute a statement or definition tree.
     */
    public Type attribStat(JCTree tree, Env<AttrContext> env) {
        return attribTree(tree, env, NIL, Type.noType);
    }

    /** Attribute a list of expressions, returning a list of types.
     */
    List<Type> attribExprs(List<JCExpression> trees, Env<AttrContext> env, Type pt) {
        ListBuffer<Type> ts = new ListBuffer<Type>();
        for (List<JCExpression> l = trees; l.nonEmpty(); l = l.tail)
            ts.append(attribExpr(l.head, env, pt));
        return ts.toList();
    }

    /** Attribute a list of statements, returning nothing.
     */
    <T extends JCTree> void attribStats(List<T> trees, Env<AttrContext> env) {
        for (List<T> l = trees; l.nonEmpty(); l = l.tail)
            attribStat(l.head, env);
    }

    /** Attribute the arguments in a method call, returning a list of types.
     */
    List<Type> attribArgs(List<JCExpression> trees, Env<AttrContext> env) {
        ListBuffer<Type> argtypes = new ListBuffer<Type>();
        for (List<JCExpression> l = trees; l.nonEmpty(); l = l.tail)
            argtypes.append(chk.checkNonVoid(
                l.head.pos(), types.upperBound(attribTree(l.head, env, VAL, Infer.anyPoly))));
        return argtypes.toList();
    }

    /** Attribute a type argument list, returning a list of types.
     */
    List<Type> attribTypes(List<JCExpression> trees, Env<AttrContext> env) {
        ListBuffer<Type> argtypes = new ListBuffer<Type>();
        for (List<JCExpression> l = trees; l.nonEmpty(); l = l.tail) {
            argtypes.append(chk.checkRefType(l.head.pos(), attribType(l.head, env)));
        }
        return argtypes.toList();
    }

    List<RPL> attribRPLs(List<DPJRegionPathList> trees) {
	ListBuffer<RPL> buf = ListBuffer.lb();
	for (DPJRegionPathList tree : trees) {
	    attribTree(tree, env, NIL, Type.noType);
	    buf.append(tree.rpl);
	}
        return buf.toList();
    }
    
    List<Effects> attribEffects(List<DPJEffect> trees) {
	//if (trees == null) return List.nil();
	ListBuffer<Effects> buf = ListBuffer.lb();
	for (DPJEffect tree : trees) {
	    attribTree(tree, env, Kinds.EFFECT, Type.noType);
	    buf.append(tree.effects);
	}
	return buf.toList();
    }
    
    /**
     * Attribute type variables (of generic classes or methods).
     * Compound types are attributed later in attribBounds.
     * @param typarams the type variables to enter
     * @param env      the current environment
     */
    void attribTypeVariables(List<JCTypeParameter> typarams, Env<AttrContext> env) {
        for (JCTypeParameter tvar : typarams) {
            TypeVar a = (TypeVar)tvar.type;
            if (!tvar.bounds.isEmpty()) {
                List<Type> bounds = List.of(attribType(tvar.bounds.head, env));
                for (JCExpression bound : tvar.bounds.tail)
                    bounds = bounds.prepend(attribType(bound, env));
                types.setBounds(a, bounds.reverse());
            } else {
                // if no bounds are given, assume a single bound of
                // java.lang.Object.
                types.setBounds(a, List.of(syms.objectType));
            }
        }
        for (JCTypeParameter tvar : typarams)
            chk.checkNonCyclic(tvar.pos(), (TypeVar)tvar.type);
        attribStats(typarams, env);
    }

    void attribBounds(List<JCTypeParameter> typarams) {
        for (JCTypeParameter typaram : typarams) {
            Type bound = typaram.type.getUpperBound();
            if (bound != null && bound.tsym instanceof ClassSymbol) {
                ClassSymbol c = (ClassSymbol)bound.tsym;
                if ((c.flags_field & COMPOUND) != 0) {
                    assert (c.flags_field & UNATTRIBUTED) != 0 : c;
                    attribClass(typaram.pos(), c);
                }
            }
        }
    }

    /**
     * Attribute the type references in a list of annotations.
     */
    void attribAnnotationTypes(List<JCAnnotation> annotations,
                               Env<AttrContext> env) {
        for (List<JCAnnotation> al = annotations; al.nonEmpty(); al = al.tail) {
            JCAnnotation a = al.head;
            attribType(a.annotationType, env);
        }
    }

    /** Attribute type reference in an `extends' or `implements' clause.
     *
     *  @param tree              The tree making up the type reference.
     *  @param env               The environment current at the reference.
     *  @param classExpected     true if only a class is expected here.
     *  @param interfaceExpected true if only an interface is expected here.
     */
    Type attribBase(JCTree tree,
                    Env<AttrContext> env,
                    boolean classExpected,
                    boolean interfaceExpected,
                    boolean checkExtensible) {
        Type t = attribType(tree, env);
        return checkBase(t, tree, env, classExpected, interfaceExpected, checkExtensible);
    }
    Type checkBase(Type t,
                   JCTree tree,
                   Env<AttrContext> env,
                   boolean classExpected,
                   boolean interfaceExpected,
                   boolean checkExtensible) {
        if (t.tag == TYPEVAR && !classExpected && !interfaceExpected) {
            // check that type variable is already visible
            if (t.getUpperBound() == null) {
                log.error(tree.pos(), "illegal.forward.ref");
                return syms.errType;
            }
        } else {
            t = chk.checkClassType(tree.pos(), t, checkExtensible|!allowGenerics);
        }
        if (interfaceExpected && (t.tsym.flags() & INTERFACE) == 0) {
            log.error(tree.pos(), "intf.expected.here");
            // return errType is necessary since otherwise there might
            // be undetected cycles which cause attribution to loop
            return syms.errType;
        } else if (checkExtensible &&
                   classExpected &&
                   (t.tsym.flags() & INTERFACE) != 0) {
            log.error(tree.pos(), "no.intf.expected.here");
            return syms.errType;
        }
        if (checkExtensible &&
            ((t.tsym.flags() & FINAL) != 0)) {
            log.error(tree.pos(),
                      "cant.inherit.from.final", t.tsym);
        }
        chk.checkNonCyclic(tree.pos(), t);
        return t;
    }

    public void visitClassDef(JCClassDecl tree) {
	boolean doPrePass = false;
        // Local classes have not been entered yet, so we need to do it now:
        if ((env.info.scope.owner.kind & (VAR | MTH)) != 0) {
            enter.classEnter(tree, env);
            doPrePass = true;
        }

        ClassSymbol c = tree.sym;
        if (c == null) {
            // exit in case something drastic went wrong during enter.
            result = null;
        } else {
            // make sure class has been completed:
            c.complete();

            // If this class appears as an anonymous class
            // in a superclass constructor call where
            // no explicit outer instance is given,
            // disable implicit outer instance from being passed.
            // (This would be an illegal access to "this before super").
            if (env.info.isSelfCall &&
                env.tree.getTag() == JCTree.NEWCLASS &&
                ((JCNewClass) env.tree).encl == null)
            {
                c.flags_field |= NOOUTERTHIS;
            }
            
            if (doPrePass)
        	dpjAttrPrePass.scan(tree);
            
            attribClass(tree.pos(), c);
            result = tree.type = c.type;
        }
    }

    /**
     * Enter method region and type parameters into a local scope
     */
    public void enterMethodParams(JCMethodDecl tree, Env<AttrContext> localEnv) {
	// Enter region and effect parameter info
	if (tree.paramInfo != null) {
            tree.sym.constraints = 
        	enterRegionParamInfo(tree.paramInfo, localEnv);
	}

        // Enter all type parameters into the local method scope.
        for (List<JCTypeParameter> l = tree.typarams; l.nonEmpty(); l = l.tail) {
            localEnv.info.scope.enterIfAbsent(l.head.type.tsym);
            // Enter type region params
            for (DPJRegionParameter rplparam : l.head.rplparams) {
        	localEnv.info.scope.enterIfAbsent(rplparam.sym);
            }
        }
        
    }
    
    /**
     * Enter class region and type parameters into a local scope
     */
    public void enterClassParams(JCClassDecl tree, Env<AttrContext> localEnv) {
	// Enter region parameter info
	if (tree.paramInfo != null) {
	    // TODO: Store all constraints
	    tree.sym.constraints = 
		enterRegionParamInfo(tree.paramInfo, localEnv);
        }

        // Enter all type parameters into the local method scope.
        for (List<JCTypeParameter> l = tree.typarams; l.nonEmpty(); l = l.tail)
            localEnv.info.scope.enterIfAbsent(l.head.type.tsym);
    }
    
    /**
     * Enter region param info from a class or method into a local scope
     */
    public Constraints 
    	enterRegionParamInfo(DPJParamInfo tree, Env<AttrContext> localEnv) {
	
	// Enter all region params into the local method scope
	for (DPJRegionParameter param : tree.rplParams) {
	    RegionParameterSymbol sym = param.sym;
	    if (chk.checkUnique(param.pos(), sym, localEnv.info.scope)) {
		localEnv.info.scope.enter(sym);
	    }
	}
	
	// Enter all effect params into the local method scope
	for (JCIdent param : tree.effectParams) {
	    EffectParameterSymbol sym = (EffectParameterSymbol) param.sym;
	    if (chk.checkUnique(param.pos(), sym, localEnv.info.scope)) {
		localEnv.info.scope.enter(sym);
	    }
	}
	
	// Attribute RPL constraints and check for validity
	for (Pair<DPJRegionPathList,DPJRegionPathList> constraint : tree.rplConstraints) {
	    attribTree(constraint.fst, localEnv, NIL, Type.noType);
	    attribTree(constraint.snd, localEnv, NIL, Type.noType);	    
	    if (constraint.fst.rpl.isIncludedIn(constraint.snd.rpl) ||
		    constraint.snd.rpl.isIncludedIn(constraint.fst.rpl)) {
		log.error(constraint.fst.pos(), "rpls.not.disjoint");
	    }
	}
 
        // Enter RPL constraints
	ListBuffer<Pair<RPL,RPL>> rplBuf = ListBuffer.lb();
	for (Pair<DPJRegionPathList,DPJRegionPathList> treeConstraint : tree.rplConstraints) {
	    Pair<RPL,RPL> constraint = 
		new Pair<RPL,RPL>(treeConstraint.fst.rpl,
				  treeConstraint.snd.rpl);
	    rplBuf.append(constraint);
	}
	List<Pair<RPL,RPL>> rplConstraints = rplBuf.toList();
	localEnv.info.constraints.disjointRPLs =
	    localEnv.info.constraints.disjointRPLs.appendList(rplConstraints);

	// Attribute effect constraints
	for (Pair<DPJEffect,DPJEffect> constraint : tree.effectConstraints) {
	    attribTree(constraint.fst, localEnv, NIL, Type.noType);
	    attribTree(constraint.snd, localEnv, NIL, Type.noType);
	}

	// Enter effect constraints
	ListBuffer<Pair<Effects,Effects>> effectBuf = ListBuffer.lb();
	for (Pair<DPJEffect,DPJEffect> treeConstraint : tree.effectConstraints) {
	    Pair<Effects,Effects> constraint = 
		new Pair<Effects,Effects>(treeConstraint.fst.effects,
			treeConstraint.snd.effects);
	    effectBuf.append(constraint);
	}
	List<Pair<Effects,Effects>> effectConstraints = effectBuf.toList();
	localEnv.info.constraints.noninterferingEffects =
	    localEnv.info.constraints.noninterferingEffects.appendList(effectConstraints);
	
	// Return constraints
	return new Constraints(rplConstraints, effectConstraints);
    }
    
    public void visitMethodDef(JCMethodDecl tree) {
	MethodSymbol m = tree.sym;

        Lint lint = env.info.lint.augment(m.attributes_field, m.flags());
        Lint prevLint = chk.setLint(lint);
        try {
            chk.checkDeprecatedAnnotation(tree.pos(), m);

            attribBounds(tree.typarams);


            // Create a new environment with local scope
            // for attributing the method.
            Env<AttrContext> localEnv = memberEnter.methodEnv(tree, env);

            localEnv.info.lint = lint;

            // Enter region and type parameters into the local scope
            enterMethodParams(tree, localEnv);

            ClassSymbol owner = env.enclClass.sym;
            if ((owner.flags() & ANNOTATION) != 0 &&
                tree.params.nonEmpty())
                log.error(tree.params.head.pos(),
                          "intf.annotation.members.cant.have.params");

            // Attribute all value parameters.
            for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                attribStat(l.head, localEnv);
            }

            // Check that type parameters are well-formed.
            chk.validateTypeParams(tree.typarams);
            if ((owner.flags() & ANNOTATION) != 0 &&
                tree.typarams.nonEmpty())
                log.error(tree.typarams.head.pos(),
                          "intf.annotation.members.cant.have.type.params");

            // Check that result type is well-formed.
            chk.validate(tree.restype);
            if ((owner.flags() & ANNOTATION) != 0)
                chk.validateAnnotationType(tree.restype);

            if ((owner.flags() & ANNOTATION) != 0)
                chk.validateAnnotationMethod(tree.pos(), m);

            // Check that all exceptions mentioned in the throws clause extend
            // java.lang.Throwable.
            if ((owner.flags() & ANNOTATION) != 0 && tree.thrown.nonEmpty())
                log.error(tree.thrown.head.pos(),
                          "throws.not.allowed.in.intf.annotation");
            for (List<JCExpression> l = tree.thrown; l.nonEmpty(); l = l.tail)
                chk.checkType(l.head.pos(), l.head.type, syms.throwableType);

            methodEntryEnv =
                localEnv.dup(tree, localEnv.info.dup(localEnv.info.scope.dupUnshared()));
            
            if (tree.body == null) {
                // Empty bodies are only allowed for
                // abstract, native, or interface methods, or for methods
                // in a retrofit signature class.
                if ((owner.flags() & INTERFACE) == 0 &&
                    (tree.mods.flags & (ABSTRACT | NATIVE)) == 0 &&
                    !relax)
                    log.error(tree.pos(), "missing.meth.body.or.decl.abstract");
                if (tree.defaultValue != null) {
                    if ((owner.flags() & ANNOTATION) == 0)
                        log.error(tree.pos(),
                                  "default.allowed.in.intf.annotation.member");
                }
            } else if ((owner.flags() & INTERFACE) != 0) {
                log.error(tree.body.pos(), "intf.meth.cant.have.body");
            } else if ((tree.mods.flags & ABSTRACT) != 0) {
                log.error(tree.pos(), "abstract.meth.cant.have.body");
            } else if ((tree.mods.flags & NATIVE) != 0) {
                log.error(tree.pos(), "native.meth.cant.have.body");
            } else {
                // Add an implicit super() call unless an explicit call to
                // super(...) or this(...) is given
                // or we are compiling class java.lang.Object.
                if (tree.name == names.init && owner.type != syms.objectType) {
                    JCBlock body = tree.body;
                    if (body.stats.isEmpty() ||
                        !TreeInfo.isSelfCall(body.stats.head)) {
                        body.stats = body.stats.
                            prepend(memberEnter.SuperCall(make.at(body.pos),
                                                          List.<Type>nil(),
                                                          List.<JCVariableDecl>nil(),
                                                          false));
                    } else if ((env.enclClass.sym.flags() & ENUM) != 0 &&
                               (tree.mods.flags & GENERATEDCONSTR) == 0 &&
                               TreeInfo.isSuperCall(body.stats.head)) {
                        // enum constructors are not allowed to call super
                        // directly, so make sure there aren't any super calls
                        // in enum constructors, except in the compiler
                        // generated one.
                        log.error(tree.body.stats.head.pos(),
                                  "call.to.super.not.allowed.in.enum.ctor",
                                  env.enclClass.sym);
                    }
                }

                // Attribute method body.
                attribStat(tree.body, localEnv);

            }
            
            /*
            if (tree.rgnParamInfo != null) {
        	attribTree(tree.rgnParamInfo, localEnv, NIL, Type.noType);
            }
            */
	
            if (tree.effects != null) {
        	attribTree(tree.effects, localEnv, NIL, Type.noType);
            }
            // Compute the effect set from the syntactic effect specification
            // and store it in the method symbol for later use in effect
            // checking
            // computeDeclaredEffects(tree, m);

            localEnv.info.scope.leave();

            // If we override any other methods, check that we do so properly.
            // JLS ???
            chk.checkOverride(tree, m);

            result = tree.type = m.type;
            chk.validateAnnotations(tree.mods.annotations, m);

        }
        finally {
            chk.setLint(prevLint);
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        // Local variables have not been entered yet, so we need to do it now:
        if (env.info.scope.owner.kind == MTH) {
            if (tree.rpl != null) {
        	log.error(tree.pos, "local.var.in.region");
            }
            if (tree.sym != null) {
                // parameters have already been entered
                env.info.scope.enter(tree.sym);
            } else {
                memberEnter.memberEnter(tree, env);
                annotate.flush();
            }
        }

        // Check that the variable's declared type is well-formed.
        chk.validate(tree.vartype);

        VarSymbol v = tree.sym;
        Lint lint = env.info.lint.augment(v.attributes_field, v.flags());
        Lint prevLint = chk.setLint(lint);

        try {
            chk.checkDeprecatedAnnotation(tree.pos(), v);

            if (tree.init != null) {
                if ((v.flags_field & FINAL) != 0 && tree.init.getTag() != JCTree.NEWCLASS) {
                    // In this case, `v' is final.  Ensure that its initializer is
                    // evaluated.
                    v.getConstValue(); // ensure initializer is evaluated
                } else {
                    // Attribute initializer in a new environment
                    // with the declared variable as owner.
                    // Check that initializer conforms to variable's declared type.
                    Env<AttrContext> initEnv = memberEnter.initEnv(tree, env);
                    initEnv.info.lint = lint;
                    // In order to catch self-references, we set the variable's
                    // declaration position to maximal possible value, effectively
                    // marking the variable as undefined.
                    v.pos = Position.MAXPOS;
                    attribExpr(tree.init, initEnv, v.type);
                    v.pos = tree.pos;
                }
            }
            result = tree.type = v.type;
            chk.validateAnnotations(tree.mods.annotations, v);
        }
        finally {
            chk.setLint(prevLint);
        }
    }

    public void visitSkip(JCSkip tree) {
        result = null;
    }

    public void visitBlock(JCBlock tree) {
        if (env.info.scope.owner.kind == TYP) {
            // Block is a static or instance initializer;
            // let the owner of the environment be a freshly
            // created BLOCK-method.
            Env<AttrContext> localEnv =
                env.dup(tree, env.info.dup(env.info.scope.dupUnshared()));
            localEnv.info.scope.owner =
                new MethodSymbol(tree.flags | BLOCK, names.empty, null,
                                 env.info.scope.owner);
            if ((tree.flags & STATIC) != 0) localEnv.info.staticLevel++;
            attribStats(tree.stats, localEnv);
        } else {
            // Create a new local environment with a local scope.
            Env<AttrContext> localEnv =
                env.dup(tree, env.info.dup(env.info.scope.dup()));
            attribStats(tree.stats, localEnv);
            localEnv.info.scope.leave();
        }
        result = null;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        attribStat(tree.body, env.dup(tree));
        attribExpr(tree.cond, env, syms.booleanType);
        result = null;
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        attribStat(tree.body, env.dup(tree));
        result = null;
    }

    public void visitForLoop(JCForLoop tree) {
        Env<AttrContext> loopEnv =
            env.dup(env.tree, env.info.dup(env.info.scope.dup()));
        attribStats(tree.init, loopEnv);
        if (tree.cond != null) attribExpr(tree.cond, loopEnv, syms.booleanType);
        loopEnv.tree = tree; // before, we were not in loop!
        attribStats(tree.step, loopEnv);
        attribStat(tree.body, loopEnv);
        loopEnv.info.scope.leave();
        result = null;
    }
    
    public void visitForeachLoop(JCEnhancedForLoop tree) {
        Env<AttrContext> loopEnv =
            env.dup(env.tree, env.info.dup(env.info.scope.dup()));
        attribStat(tree.var, loopEnv);
        Type exprType = types.upperBound(attribExpr(tree.expr, loopEnv));
        chk.checkNonVoid(tree.pos(), exprType);
        Type elemtype = types.elemtype(exprType); // perhaps expr is an array?
        if (elemtype == null) {
            // or perhaps expr implements Iterable<T>?
            Type base = types.asSuper(exprType, syms.iterableType.tsym);
            if (base == null) {
                log.error(tree.expr.pos(), "foreach.not.applicable.to.type");
                elemtype = syms.errType;
            } else {
                List<Type> iterableParams = base.alltyparams();
                elemtype = iterableParams.isEmpty()
                    ? syms.objectType
                    : types.upperBound(iterableParams.head);
            }
        }
        chk.checkType(tree.expr.pos(), elemtype, tree.var.sym.type);
        loopEnv.tree = tree; // before, we were not in loop!
        attribStat(tree.body, loopEnv);
        loopEnv.info.scope.leave();
        result = null;
    }

    public void visitLabelled(JCLabeledStatement tree) {
        // Check that label is not used in an enclosing statement
        Env<AttrContext> env1 = env;
        while (env1 != null && env1.tree.getTag() != JCTree.CLASSDEF) {
            if (env1.tree.getTag() == JCTree.LABELLED &&
                ((JCLabeledStatement) env1.tree).label == tree.label) {
                log.error(tree.pos(), "label.already.in.use",
                          tree.label);
                break;
            }
            env1 = env1.next;
        }

        attribStat(tree.body, env.dup(tree));
        result = null;
    }

    public void visitSwitch(JCSwitch tree) {
        Type seltype = attribExpr(tree.selector, env);

        Env<AttrContext> switchEnv =
            env.dup(tree, env.info.dup(env.info.scope.dup()));

        boolean enumSwitch =
            allowEnums &&
            (seltype.tsym.flags() & Flags.ENUM) != 0;
        if (!enumSwitch)
            seltype = chk.checkType(tree.selector.pos(), seltype, syms.intType);

        // Attribute all cases and
        // check that there are no duplicate case labels or default clauses.
        Set<Object> labels = new HashSet<Object>(); // The set of case labels.
        boolean hasDefault = false;      // Is there a default label?
        for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
            JCCase c = l.head;
            Env<AttrContext> caseEnv =
                switchEnv.dup(c, env.info.dup(switchEnv.info.scope.dup()));
            if (c.pat != null) {
                if (enumSwitch) {
                    Symbol sym = enumConstant(c.pat, seltype);
                    if (sym == null) {
                        log.error(c.pat.pos(), "enum.const.req");
                    } else if (!labels.add(sym)) {
                        log.error(c.pos(), "duplicate.case.label");
                    }
                } else {
                    Type pattype = attribExpr(c.pat, switchEnv, seltype);
                    if (pattype.tag != ERROR) {
                        if (pattype.constValue() == null) {
                            log.error(c.pat.pos(), "const.expr.req");
                        } else if (labels.contains(pattype.constValue())) {
                            log.error(c.pos(), "duplicate.case.label");
                        } else {
                            labels.add(pattype.constValue());
                        }
                    }
                }
            } else if (hasDefault) {
                log.error(c.pos(), "duplicate.default.label");
            } else {
                hasDefault = true;
            }
            attribStats(c.stats, caseEnv);
            caseEnv.info.scope.leave();
            addVars(c.stats, switchEnv.info.scope);
        }

        switchEnv.info.scope.leave();
        result = null;
    }
    // where
        /** Add any variables defined in stats to the switch scope. */
        private static void addVars(List<JCStatement> stats, Scope switchScope) {
            for (;stats.nonEmpty(); stats = stats.tail) {
                JCTree stat = stats.head;
                if (stat.getTag() == JCTree.VARDEF)
                    switchScope.enter(((JCVariableDecl) stat).sym);
            }
        }
    // where
    /** Return the selected enumeration constant symbol, or null. */
    private Symbol enumConstant(JCTree tree, Type enumType) {
        if (tree.getTag() != JCTree.IDENT) {
            log.error(tree.pos(), "enum.label.must.be.unqualified.enum");
            return syms.errSymbol;
        }
        JCIdent ident = (JCIdent)tree;
        Name name = ident.name;
        for (Scope.Entry e = enumType.tsym.members().lookup(name);
             e.scope != null; e = e.next()) {
            if (e.sym.kind == VAR) {
                Symbol s = ident.sym = e.sym;
                ((VarSymbol)s).getConstValue(); // ensure initializer is evaluated
                ident.type = s.type;
                return ((s.flags_field & Flags.ENUM) == 0)
                    ? null : s;
            }
        }
        return null;
    }

    public void visitSynchronized(JCSynchronized tree) {
        chk.checkRefType(tree.pos(), attribExpr(tree.lock, env));
        attribStat(tree.body, env);
        result = null;
    }

    public void visitTry(JCTry tree) {
        // Attribute body
        attribStat(tree.body, env.dup(tree, env.info.dup()));

        // Attribute catch clauses
        for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
            JCCatch c = l.head;
            Env<AttrContext> catchEnv =
                env.dup(c, env.info.dup(env.info.scope.dup()));
            Type ctype = attribStat(c.param, catchEnv);
            if (c.param.type.tsym.kind == Kinds.VAR) {
                c.param.sym.setData(ElementKind.EXCEPTION_PARAMETER);
            }
            chk.checkType(c.param.vartype.pos(),
                          chk.checkClassType(c.param.vartype.pos(), ctype),
                          syms.throwableType);
            attribStat(c.body, catchEnv);
            catchEnv.info.scope.leave();
        }

        // Attribute finalizer
        if (tree.finalizer != null) attribStat(tree.finalizer, env);
        result = null;
    }

    public void visitConditional(JCConditional tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        attribExpr(tree.truepart, env);
        attribExpr(tree.falsepart, env);
        result = check(tree,
                       capture(condType(tree.pos(), tree.cond.type,
                                        tree.truepart.type, tree.falsepart.type)),
                       VAL, pkind, pt);
    }
    //where
        /** Compute the type of a conditional expression, after
         *  checking that it exists. See Spec 15.25.
         *
         *  @param pos      The source position to be used for
         *                  error diagnostics.
         *  @param condtype The type of the expression's condition.
         *  @param thentype The type of the expression's then-part.
         *  @param elsetype The type of the expression's else-part.
         */
        private Type condType(DiagnosticPosition pos,
                              Type condtype,
                              Type thentype,
                              Type elsetype) {
            Type ctype = condType1(pos, condtype, thentype, elsetype);

            // If condition and both arms are numeric constants,
            // evaluate at compile-time.
            return ((condtype.constValue() != null) &&
                    (thentype.constValue() != null) &&
                    (elsetype.constValue() != null))
                ? cfolder.coerce(condtype.isTrue()?thentype:elsetype, ctype)
                : ctype;
        }
        /** Compute the type of a conditional expression, after
         *  checking that it exists.  Does not take into
         *  account the special case where condition and both arms
         *  are constants.
         *
         *  @param pos      The source position to be used for error
         *                  diagnostics.
         *  @param condtype The type of the expression's condition.
         *  @param thentype The type of the expression's then-part.
         *  @param elsetype The type of the expression's else-part.
         */
        private Type condType1(DiagnosticPosition pos, Type condtype,
                               Type thentype, Type elsetype) {
            // If same type, that is the result
            if (types.isSameType(thentype, elsetype))
                return thentype.baseType();

            Type thenUnboxed = (!allowBoxing || thentype.isPrimitive())
                ? thentype : types.unboxedType(thentype);
            Type elseUnboxed = (!allowBoxing || elsetype.isPrimitive())
                ? elsetype : types.unboxedType(elsetype);

            // Otherwise, if both arms can be converted to a numeric
            // type, return the least numeric type that fits both arms
            // (i.e. return larger of the two, or return int if one
            // arm is short, the other is char).
            if (thenUnboxed.isPrimitive() && elseUnboxed.isPrimitive()) {
                // If one arm has an integer subrange type (i.e., byte,
                // short, or char), and the other is an integer constant
                // that fits into the subrange, return the subrange type.
                if (thenUnboxed.tag < INT && elseUnboxed.tag == INT &&
                    types.isAssignable(elseUnboxed, thenUnboxed))
                    return thenUnboxed.baseType();
                if (elseUnboxed.tag < INT && thenUnboxed.tag == INT &&
                    types.isAssignable(thenUnboxed, elseUnboxed))
                    return elseUnboxed.baseType();

                for (int i = BYTE; i < VOID; i++) {
                    Type candidate = syms.typeOfTag[i];
                    if (types.isSubtype(thenUnboxed, candidate) &&
                        types.isSubtype(elseUnboxed, candidate))
                        return candidate;
                }
            }

            // Those were all the cases that could result in a primitive
            if (allowBoxing) {
                if (thentype.isPrimitive())
                    thentype = types.boxedClass(thentype).type;
                if (elsetype.isPrimitive())
                    elsetype = types.boxedClass(elsetype).type;
            }

            if (types.isSubtype(thentype, elsetype))
                return elsetype.baseType();
            if (types.isSubtype(elsetype, thentype))
                return thentype.baseType();

            if (!allowBoxing || thentype.tag == VOID || elsetype.tag == VOID) {
                log.error(pos, "neither.conditional.subtype",
                          thentype, elsetype);
                return thentype.baseType();
            }

            // both are known to be reference types.  The result is
            // lub(thentype,elsetype). This cannot fail, as it will
            // always be possible to infer "Object" if nothing better.
            return types.lub(thentype.baseType(), elsetype.baseType());
        }

    public void visitIf(JCIf tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        attribStat(tree.thenpart, env);
        if (tree.elsepart != null)
            attribStat(tree.elsepart, env);
        chk.checkEmptyIf(tree);
        result = null;
    }

    public void visitExec(JCExpressionStatement tree) {
        attribExpr(tree.expr, env);
        result = null;
    }

    public void visitBreak(JCBreak tree) {
        tree.target = findJumpTarget(tree.pos(), tree.getTag(), tree.label, env);
        result = null;
    }

    public void visitContinue(JCContinue tree) {
        tree.target = findJumpTarget(tree.pos(), tree.getTag(), tree.label, env);
        result = null;
    }
    //where
        /** Return the target of a break or continue statement, if it exists,
         *  report an error if not.
         *  Note: The target of a labelled break or continue is the
         *  (non-labelled) statement tree referred to by the label,
         *  not the tree representing the labelled statement itself.
         *
         *  @param pos     The position to be used for error diagnostics
         *  @param tag     The tag of the jump statement. This is either
         *                 Tree.BREAK or Tree.CONTINUE.
         *  @param label   The label of the jump statement, or null if no
         *                 label is given.
         *  @param env     The environment current at the jump statement.
         */
        private JCTree findJumpTarget(DiagnosticPosition pos,
                                    int tag,
                                    Name label,
                                    Env<AttrContext> env) {
            // Search environments outwards from the point of jump.
            Env<AttrContext> env1 = env;
            LOOP:
            while (env1 != null) {
                switch (env1.tree.getTag()) {
                case JCTree.LABELLED:
                    JCLabeledStatement labelled = (JCLabeledStatement)env1.tree;
                    if (label == labelled.label) {
                        // If jump is a continue, check that target is a loop.
                        if (tag == JCTree.CONTINUE) {
                            if (labelled.body.getTag() != JCTree.DOLOOP &&
                                labelled.body.getTag() != JCTree.WHILELOOP &&
                                labelled.body.getTag() != JCTree.FORLOOP &&
                                labelled.body.getTag() != JCTree.FOREACHLOOP)
                                log.error(pos, "not.loop.label", label);
                            // Found labelled statement target, now go inwards
                            // to next non-labelled tree.
                            return TreeInfo.referencedStatement(labelled);
                        } else {
                            return labelled;
                        }
                    }
                    break;
                case JCTree.DOLOOP:
                case JCTree.WHILELOOP:
                case JCTree.FORLOOP:
                case JCTree.FOREACHLOOP:
                    if (label == null) return env1.tree;
                    break;
                case JCTree.SWITCH:
                    if (label == null && tag == JCTree.BREAK) return env1.tree;
                    break;
                case JCTree.METHODDEF:
                case JCTree.CLASSDEF:
                    break LOOP;
                default:
                }
                env1 = env1.next;
            }
            if (label != null)
                log.error(pos, "undef.label", label);
            else if (tag == JCTree.CONTINUE)
                log.error(pos, "cont.outside.loop");
            else
                log.error(pos, "break.outside.switch.loop");
            return null;
        }

    public void visitReturn(JCReturn tree) {
        // Check that there is an enclosing method which is
        // nested within than the enclosing class.
        if (env.enclMethod == null ||
            env.enclMethod.sym.owner != env.enclClass.sym) {
            log.error(tree.pos(), "ret.outside.meth");

        } else {
            // Attribute return expression, if it exists, and check that
            // it conforms to result type of enclosing method.
            Symbol m = env.enclMethod.sym;
            if (m.type.getReturnType().tag == VOID) {
                if (tree.expr != null)
                    log.error(tree.expr.pos(),
                              "cant.ret.val.from.meth.decl.void");
            } else if (tree.expr == null) {
                log.error(tree.pos(), "missing.ret.val");
            } else {
                attribExpr(tree.expr, env, m.type.getReturnType());
            }
        }
        result = null;
    }

    public void visitThrow(JCThrow tree) {
        attribExpr(tree.expr, env, syms.throwableType);
        result = null;
    }

    public void visitAssert(JCAssert tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        if (tree.detail != null) {
            chk.checkNonVoid(tree.detail.pos(), attribExpr(tree.detail, env));
        }
        result = null;
    }

     /** Visitor method for method invocations.
     *  NOTE: The method part of an application will have in its type field
     *        the return type of the method, not the method's type itself!
     */
    public void visitApply(JCMethodInvocation tree) {
	
	// The local environment of a method application is
        // a new environment nested in the current one.
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup());

	// Store the actual args for method symbol resolution
	localEnv.info.actualArgs = tree.getArguments();
	
        // The types of the actual method arguments.
        List<Type> argtypes;

        // The types of the actual method type arguments.
        List<Type> typeargtypes = null;
        // The actual method region args
        List<RPL> regionargs = null;
        // The actual method effect args
        List<Effects> effectargs = null;

        Name methName = TreeInfo.name(tree.meth);

        boolean isConstructorCall =
            methName == names._this || methName == names._super;

        if (isConstructorCall) {
            // We are seeing a ...this(...) or ...super(...) call.
            // Check that this is the first statement in a constructor.
            if (checkFirstConstructorStat(tree, env)) {

                // Record the fact
                // that this is a constructor call (using isSelfCall).
                localEnv.info.isSelfCall = true;

                // Attribute arguments, yielding list of argument types.
                argtypes = attribArgs(tree.args, localEnv);
                typeargtypes = attribTypes(tree.typeargs, localEnv);
                regionargs = attribRPLs(tree.regionArgs);
                effectargs = attribEffects(tree.effectargs);
                
                // Variable `site' points to the class in which the called
                // constructor is defined.
                Type site = env.enclClass.sym.type;
                if (methName == names._super) {
                    if (site == syms.objectType) {
                        log.error(tree.meth.pos(), "no.superclass", site);
                        site = syms.errType;
                    } else {
                        site = types.supertype(site);
                    }
                }

                if (site.tag == CLASS) {
                    if (site.getEnclosingType().tag == CLASS) {
                        // we are calling a nested class

                        if (tree.meth.getTag() == JCTree.SELECT) {
                            JCTree qualifier = ((JCFieldAccess) tree.meth).selected;

                            // We are seeing a prefixed call, of the form
                            //     <expr>.super(...).
                            // Check that the prefix expression conforms
                            // to the outer instance type of the class.
                            chk.checkRefType(qualifier.pos(),
                                             attribExpr(qualifier, localEnv,
                                                        site.getEnclosingType()));
                        } else if (methName == names._super) {
                            // qualifier omitted; check for existence
                            // of an appropriate implicit qualifier.
                            rs.resolveImplicitThis(tree.meth.pos(),
                                                   localEnv, site);
                        }
                    } else if (tree.meth.getTag() == JCTree.SELECT) {
                        log.error(tree.meth.pos(), "illegal.qual.not.icls",
                                  site.tsym);
                    }

                    // if we're calling a java.lang.Enum constructor,
                    // prefix the implicit String and int parameters
                    if (site.tsym == syms.enumSym && allowEnums)
                        argtypes = argtypes.prepend(syms.intType).prepend(syms.stringType);

                    // Resolve the called constructor under the assumption
                    // that we are referring to a superclass instance of the
                    // current instance (JLS ???).
                    boolean selectSuperPrev = localEnv.info.selectSuper;
                    localEnv.info.selectSuper = true;
                    localEnv.info.varArgs = false;
                    Symbol sym = rs.resolveConstructor(
                        tree.meth.pos(), localEnv, site, argtypes, 
                        typeargtypes, regionargs, effectargs);
                    localEnv.info.selectSuper = selectSuperPrev;

                    // Set method symbol to resolved constructor...
                    TreeInfo.setSymbol(tree.meth, sym);

                    // ...and check that it is legal in the current context.
                    // (this will also set the tree's type)
                    Type mpt = newMethTemplate(argtypes, typeargtypes, 
                	    regionargs, effectargs);
                    checkId(tree.meth, site, sym, localEnv, MTH,
                            mpt, tree.varargsElement != null);
                }
                // Otherwise, `site' is an error type and we do nothing
            }
            result = tree.type = syms.voidType;
        } else {
            
            // Otherwise, we are seeing a regular method call.
            // Attribute the arguments, yielding list of argument types, ...
            argtypes = attribArgs(tree.args, localEnv);
            typeargtypes = attribTypes(tree.typeargs, localEnv);
            regionargs = attribRPLs(tree.regionArgs);
            effectargs = attribEffects(tree.effectargs);
            
            // ... and attribute the method using as a prototype a methodtype
            // whose formal argument types is exactly the list of actual
            // arguments (this will also set the method symbol).
            Type mpt = newMethTemplate(argtypes, typeargtypes, 
        	    regionargs, effectargs);
            localEnv.info.varArgs = false;
            Type mtype = attribExpr(tree.meth, localEnv, mpt);
            if (mtype instanceof MethodType)
        	tree.mtype = (MethodType) mtype;
            if (localEnv.info.varArgs)
                assert mtype.isErroneous() || tree.varargsElement != null;

            // Compute the result type.
            Type restype = mtype.getReturnType();

            if (restype.tag == WILDCARD) {
                restype = types.upperBound(restype); 
            }

            // as a special case, array.clone() has a result that is
            // the same as static type of the array being cloned
            if (tree.meth.getTag() == JCTree.SELECT &&
                allowCovariantReturns &&
                methName == names.clone &&
                types.isArray(((JCFieldAccess) tree.meth).selected.type))
                restype = ((JCFieldAccess) tree.meth).selected.type;

            // as a special case, x.getClass() has type Class<? extends |X|>
            if (allowGenerics &&
                methName == names.getClass && tree.args.isEmpty()) {
                Type qualifier = (tree.meth.getTag() == JCTree.SELECT)
                    ? ((JCFieldAccess) tree.meth).selected.type
                    : env.enclClass.sym.type;
                restype = new
                    ClassType(restype.getEnclosingType(),
                              List.<Type>of(new WildcardType(types.erasure(qualifier),
                                                               BoundKind.EXTENDS,
                                                               syms.boundClass)),
                              List.<RPL>nil(),
                              List.<Effects>nil(),
                              restype.tsym, null);
            }

            // Substitutions required by DPJ type system

            // Substitute actual arguments for argument variables
            MethodSymbol methSym = tree.getMethodSymbol();
            if (methSym != null && methSym.params != null) {
                RPL selectedRPL = exprToRPL(rs.selectedExp(tree.meth, env));
                restype = types.substRPLForVar(restype, methSym.enclThis(), selectedRPL);
        	restype = types.substIndices(restype, methSym.params, 
        		tree.getArguments());
            }
            
            // Check that constraints on rpl args and effect args are satisfied
            if (mtype instanceof MethodType && methSym.constraints != null) {
        	regionargs = ((MethodType) mtype).regionActuals;
        	if (!rpls.disjointnessConstraintsAreSatisfied(methSym.constraints.disjointRPLs,
        		methSym.rgnParams, 
        		regionargs, env.info.constraints.disjointRPLs)) {
        	    log.warning(tree, "rpl.constraints");        	    
        	}
        	if (!rpls.atomicConstraintsAreSatisfied(methSym.rgnParams,
        		regionargs)) {
        	    enter.log.error(tree, "atomic.constraints");
        	}
    	    	if (!Effects.nonintConstraintsAreSatisfied(methSym.constraints.noninterferingEffects,
    	    		tree, methSym.rgnParams, regionargs,
    	    		methSym.effectparams, effectargs, types, this, env)) {
    	    	    enter.log.warning(tree, "effect.constraints");
    	    	}
            }
            
            // Check that value of resulting type is admissible in the
            // current context.  Also, capture the return type
            result = check(tree, capture(restype), VAL, pkind, pt);
        }
        chk.validate(tree.typeargs);
    }
    //where
        /** Check that given application node appears as first statement
         *  in a constructor call.
         *  @param tree   The application node
         *  @param env    The environment current at the application.
         */
        boolean checkFirstConstructorStat(JCMethodInvocation tree, Env<AttrContext> env) {
            JCMethodDecl enclMethod = env.enclMethod;
            if (enclMethod != null && enclMethod.name == names.init) {
                JCBlock body = enclMethod.body;
                if (body.stats.head.getTag() == JCTree.EXEC &&
                    ((JCExpressionStatement) body.stats.head).expr == tree)
                    return true;
            }
            log.error(tree.pos(),"call.must.be.first.stmt.in.ctor",
                      TreeInfo.name(tree.meth));
            return false;
        }

        /** Obtain a method type with given argument types.
         */
        Type newMethTemplate(List<Type> argtypes, List<Type> typeargtypes, 
        	List<RPL> regionargs, List<Effects> effectargs) {
            MethodType mt = new MethodType(argtypes, null, null, syms.methodClass);
            return ((typeargtypes == null) && (regionargs == null) &&
        	    (effectargs == null)) ? 
        	    mt : (Type)new ForAll(typeargtypes, regionargs, effectargs, mt);
        }

    public void visitNewClass(JCNewClass tree) {
	
	Type owntype = syms.errType;

        // The local environment of a class creation is
        // a new environment nested in the current one.
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup());

        // The anonymous inner class definition of the new expression,
        // if one is defined by it.
        JCClassDecl cdef = tree.def;

        // If enclosing class is given, attribute it, and
        // complete class name to be fully qualified
        JCExpression clazz = tree.clazz; // Class field following new
        JCExpression clazzid =          // Identifier in class field
            (clazz.getTag() == JCTree.TYPEAPPLY)
            ? ((JCTypeApply) clazz).functor
            : clazz;
            
        JCExpression clazzid1 = clazzid; // The same in fully qualified form

        if (tree.encl != null) {
            // We are seeing a qualified new, of the form
            //    <expr>.new C <...> (...) ...
            // In this case, we let clazz stand for the name of the
            // allocated class C prefixed with the type of the qualifier
            // expression, so that we can
            // resolve it with standard techniques later. I.e., if
            // <expr> has type T, then <expr>.new C <...> (...)
            // yields a clazz T.C.
            Type encltype = chk.checkRefType(tree.encl.pos(),
                                             attribExpr(tree.encl, env));
            clazzid1 = make.at(clazz.pos).Select(make.Type(encltype),
                                                 ((JCIdent) clazzid).name);
            if (clazz.getTag() == JCTree.TYPEAPPLY)
                clazz = make.at(tree.pos).
                    TypeApply(clazzid1,
                              ((JCTypeApply) clazz).typeArgs, 
                              ((JCTypeApply) clazz).rplArgs,
                              ((JCTypeApply) clazz).effectArgs);
            else
                clazz = clazzid1;
//          System.out.println(clazz + " generated.");//DEBUG
        }

        // Attribute clazz expression and store
        // symbol + type back into the attributed tree.
        Type clazztype = chk.checkClassType(
            tree.clazz.pos(), attribType(clazz, env), true);
        chk.validate(clazz);
        if (tree.encl != null) {
            // We have to work in this case to store
            // symbol + type back into the attributed tree.
            tree.clazz.type = clazztype;
            TreeInfo.setSymbol(clazzid, TreeInfo.symbol(clazzid1));
            clazzid.type = ((JCIdent) clazzid).sym.type;
            if (!clazztype.isErroneous()) {
                if (cdef != null && clazztype.tsym.isInterface()) {
                    log.error(tree.encl.pos(), "anon.class.impl.intf.no.qual.for.new");
                } else if (clazztype.tsym.isStatic()) {
                    log.error(tree.encl.pos(), "qualified.new.of.static.class", clazztype.tsym);
                }
            }
        } else if (!clazztype.tsym.isInterface() &&
                   clazztype.getEnclosingType().tag == CLASS) {
            // Check for the existence of an apropos outer instance
            rs.resolveImplicitThis(tree.pos(), env, clazztype);
        }

        // Attribute constructor arguments.
        List<Type> argtypes = attribArgs(tree.args, localEnv);
        List<Type> typeargtypes = attribTypes(tree.typeargs, localEnv);
        List<RPL> regionargs = attribRPLs(tree.regionArgs);
        List<Effects> effectargs = attribEffects(tree.effectargs);

        // If we have made no mistakes in the class type...
        if (clazztype.tag == CLASS) {
            // Enums may not be instantiated except implicitly
            if (allowEnums &&
                (clazztype.tsym.flags_field&Flags.ENUM) != 0 &&
                (env.tree.getTag() != JCTree.VARDEF ||
                 (((JCVariableDecl) env.tree).mods.flags&Flags.ENUM) == 0 ||
                 ((JCVariableDecl) env.tree).init != tree))
                log.error(tree.pos(), "enum.cant.be.instantiated");
            // Check that class is not abstract
            if (cdef == null &&
                (clazztype.tsym.flags() & (ABSTRACT | INTERFACE)) != 0) {
                log.error(tree.pos(), "abstract.cant.be.instantiated",
                          clazztype.tsym);
            } else if (cdef != null && clazztype.tsym.isInterface()) {
                // Check that no constructor arguments are given to
                // anonymous classes implementing an interface
                if (!argtypes.isEmpty())
                    log.error(tree.args.head.pos(), "anon.class.impl.intf.no.args");

                if (!typeargtypes.isEmpty())
                    log.error(tree.typeargs.head.pos(), "anon.class.impl.intf.no.typeargs");

                // Error recovery: pretend no arguments were supplied.
                argtypes = List.nil();
                typeargtypes = List.nil();
                regionargs = List.nil();
            }

            // Resolve the called constructor under the assumption
            // that we are referring to a superclass instance of the
            // current instance (JLS ???).
            else {
                localEnv.info.selectSuper = cdef != null;
                localEnv.info.varArgs = false;
                //System.err.println("Calling resolveConstructor with clazztype="+clazztype+
                //	",argtypes="+argtypes);
                tree.constructor = rs.resolveConstructor(
                    tree.pos(), localEnv, clazztype, argtypes, typeargtypes, 
                    regionargs, effectargs);
                Type ctorType = checkMethod(clazztype,
                                            tree.constructor,
                                            localEnv,
                                            tree.args,
                                            argtypes,
                                            typeargtypes,
                                            regionargs,
                                            effectargs,
                                            localEnv.info.varArgs);
                if (localEnv.info.varArgs)
                    assert ctorType.isErroneous() || tree.varargsElement != null;
            }

            if (cdef != null) {
                // We are seeing an anonymous class instance creation.
                // In this case, the class instance creation
                // expression
                //
                //    E.new <typeargs1>C<typargs2>(args) { ... }
                //
                // is represented internally as
                //
                //    E . new <typeargs1>C<typargs2>(args) ( class <empty-name> { ... } )  .
                //
                // This expression is then *transformed* as follows:
                //
                // (1) add a STATIC flag to the class definition
                //     if the current environment is static
                // (2) add an extends or implements clause
                // (3) add a constructor.
                //
                // For instance, if C is a class, and ET is the type of E,
                // the expression
                //
                //    E.new <typeargs1>C<typargs2>(args) { ... }
                //
                // is translated to (where X is a fresh name and typarams is the
                // parameter list of the super constructor):
                //
                //   new <typeargs1>X(<*nullchk*>E, args) where
                //     X extends C<typargs2> {
                //       <typarams> X(ET e, args) {
                //         e.<typeargs1>super(args)
                //       }
                //       ...
                //     }
                if (Resolve.isStatic(env)) cdef.mods.flags |= STATIC;

                if (clazztype.tsym.isInterface()) {
                    cdef.implementing = List.of(clazz);
                } else {
                    cdef.extending = clazz;
                }

                attribStat(cdef, localEnv);

                // If an outer instance is given,
                // prefix it to the constructor arguments
                // and delete it from the new expression
                if (tree.encl != null && !clazztype.tsym.isInterface()) {
                    tree.args = tree.args.prepend(makeNullCheck(tree.encl));
                    argtypes = argtypes.prepend(tree.encl.type);
                    tree.encl = null;
                }

                // Reassign clazztype and recompute constructor.
                clazztype = cdef.sym.type;
                Symbol sym = rs.resolveConstructor(
                    tree.pos(), localEnv, clazztype, argtypes,
                    typeargtypes, regionargs, effectargs,
                    true, tree.varargsElement != null);
                assert sym.kind < AMBIGUOUS || tree.constructor.type.isErroneous();
                tree.constructor = sym;
            }

            if (tree.constructor != null && tree.constructor.kind == MTH)
                owntype = clazztype;
        }
        result = check(tree, owntype, VAL, pkind, pt);
        chk.validate(tree.typeargs);
    }

    /** Make an attributed null check tree.
     */
    public JCExpression makeNullCheck(JCExpression arg) {
        // optimization: X.this is never null; skip null check
        Name name = TreeInfo.name(arg);
        if (name == names._this || name == names._super) return arg;

        int optag = JCTree.NULLCHK;
        JCUnary tree = make.at(arg.pos).Unary(optag, arg);
        tree.operator = syms.nullcheck;
        tree.type = arg.type;
        return tree;
    }

    public void visitNewArray(JCNewArray tree) {
	Env<AttrContext> oldEnv = env;
	Type owntype = syms.errType;
        Type elemtype;
        if (tree.elemtype != null) {
            List<DPJRegionPathList> rpls = tree.rpls;
            List<JCIdent> indexVars = tree.indexVars;
            ListBuffer<RPL> rplBuf = ListBuffer.lb();
            ListBuffer<VarSymbol> indexBuf = ListBuffer.lb();
	    Env<AttrContext> localEnv = env.dup(tree, env.info.dup(env.info.scope.dup()));
	    // If we're in an initializer, we need the actual scope 
	    // so we can add params to it
	    Scope scope = enter.enterScope(localEnv).getActualScope();
	    while (!rpls.isEmpty()) {
        	RPL rpl = null;
        	VarSymbol indexVar = null;
        	if (indexVars.head != null) {
        	    indexVar =
        		new VarSymbol(0, indexVars.head.name,
        			syms.intType, scope.owner);
        	    indexVars.head.sym = indexVar;
        	    scope.enter(indexVar);
        	}
        	if (rpls.head != null) {
        	    attribTree(rpls.head, localEnv, NIL, Type.noType);
        	    rpl = rpls.head.rpl;
        	}
        	rplBuf.append(rpl);
        	indexBuf.append(indexVar);
        	rpls = rpls.tail;
        	indexVars = indexVars.tail;
            }
            elemtype = attribType(tree.elemtype, localEnv);
            chk.validate(tree.elemtype);
            owntype = elemtype;
            List<RPL> rplSyms = rplBuf.toList().reverse();
            List<VarSymbol> indexSyms = indexBuf.toList().reverse();
            for (List<JCExpression> l = tree.dims; l.nonEmpty(); l = l.tail) {
                attribExpr(l.head, env, syms.intType);
                owntype = new ArrayType(owntype, rplSyms.head, 
                	indexSyms.head, syms.arrayClass);
                rplSyms = rplSyms.tail;
                indexSyms = indexSyms.tail;
            }
	    localEnv.info.scope.leave();
        } else {
            // we are seeing an untyped aggregate { ... }
            // this is allowed only if the prototype is an array
            if (pt.tag == ARRAY) {
                elemtype = types.elemtype(pt);
            } else {
                if (pt.tag != ERROR) {
                    log.error(tree.pos(), "illegal.initializer.for.type",
                              pt);
                }
                elemtype = syms.errType;
            }
        }
        if (tree.elems != null) {
            attribExprs(tree.elems, env, elemtype);
            owntype = new ArrayType(elemtype, null,null,syms.arrayClass);
        }
        if (!types.isReifiable(elemtype))
            log.error(tree.pos(), "generic.array.creation");
        result = check(tree, owntype, VAL, pkind, pt);
	env = oldEnv;
    }

    public void visitParens(JCParens tree) {
        Type owntype = attribTree(tree.expr, env, pkind, pt);
        result = check(tree, owntype, pkind, pkind, pt);
        Symbol sym = TreeInfo.symbol(tree);
        if (sym != null && (sym.kind&(TYP|PCK)) != 0)
            log.error(tree.pos(), "illegal.start.of.type");
    }

    public void visitAssign(JCAssign tree) {
        Type owntype = attribTree(tree.lhs, env.dup(tree), VAR, Type.noType);
        Type capturedType = capture(owntype);
        attribExpr(tree.rhs, env, owntype);
        result = check(tree, capturedType, VAL, pkind, pt);
    }

    public void visitAssignop(JCAssignOp tree) {
        // Attribute arguments.
        Type owntype = attribTree(tree.lhs, env, VAR, Type.noType);
        Type operand = attribExpr(tree.rhs, env);
        // Find operator.
        Symbol operator = tree.operator = rs.resolveBinaryOperator(
            tree.pos(), tree.getTag() - JCTree.ASGOffset, env,
            owntype, operand);

        if (operator.kind == MTH) {
            chk.checkOperator(tree.pos(),
                              (OperatorSymbol)operator,
                              tree.getTag() - JCTree.ASGOffset,
                              owntype,
                              operand);
            if (types.isSameType(operator.type.getReturnType(), syms.stringType)) {
                // String assignment; make sure the lhs is a string
                chk.checkType(tree.lhs.pos(),
                              owntype,
                              syms.stringType);
            } else {
                chk.checkDivZero(tree.rhs.pos(), operator, operand);
                chk.checkCastable(tree.rhs.pos(),
                                  operator.type.getReturnType(),
                                  owntype);
            }
        }
        result = check(tree, owntype, VAL, pkind, pt);
    }

    public void visitUnary(JCUnary tree) {
        // Attribute arguments.
        Type argtype = (JCTree.PREINC <= tree.getTag() && tree.getTag() <= JCTree.POSTDEC)
            ? attribTree(tree.arg, env, VAR, Type.noType)
            : chk.checkNonVoid(tree.arg.pos(), attribExpr(tree.arg, env));

        // Find operator.
        Symbol operator = tree.operator =
            rs.resolveUnaryOperator(tree.pos(), tree.getTag(), env, argtype);

        Type owntype = syms.errType;
        if (operator.kind == MTH) {
            if (tree.getTag() == JCTree.NOT && 
        	    !types.isAssignable(tree.arg.type, syms.booleanType)) {
        	// Destructive access
        	owntype = tree.arg.type;
     		if (tree.arg instanceof JCFieldAccess ||
     			tree.arg instanceof JCArrayAccess) {
     		    tree.isDestructiveAccess = true;
     		    Symbol sym = tree.arg.getSymbol();
     		    if (sym != null && ((sym.flags() & FINAL) != 0))
     			log.error(tree.pos(), "cant.assign.val.to.final.var", sym);
     		}
     		else {
     		    log.error(tree.arg.pos(), "expected.access");
     		}
            } else {
        	owntype = (JCTree.PREINC <= tree.getTag() && tree.getTag() <= JCTree.POSTDEC)
        		? tree.arg.type : operator.type.getReturnType();
            }
            int opc = ((OperatorSymbol)operator).opcode;

            // If the argument is constant, fold it.
            if (argtype.constValue() != null) {
                Type ctype = cfolder.fold1(opc, argtype);
                if (ctype != null) {
                    owntype = cfolder.coerce(ctype, owntype);

                    // Remove constant types from arguments to
                    // conserve space. The parser will fold concatenations
                    // of string literals; the code here also
                    // gets rid of intermediate results when some of the
                    // operands are constant identifiers.
                    if (tree.arg.type.tsym == syms.stringType.tsym) {
                        tree.arg.type = syms.stringType;
                    }
                }
            }
        }
        result = check(tree, owntype, VAL, pkind, pt);
    }

    public void visitBinary(JCBinary tree) {
        // Attribute arguments.
        Type left = chk.checkNonVoid(tree.lhs.pos(), attribExpr(tree.lhs, env));
        Type right = chk.checkNonVoid(tree.lhs.pos(), attribExpr(tree.rhs, env));

        // Find operator.
        Symbol operator = tree.operator =
            rs.resolveBinaryOperator(tree.pos(), tree.getTag(), env, left, right);

        Type owntype = syms.errType;
        if (operator.kind == MTH) {
            owntype = operator.type.getReturnType();
            int opc = chk.checkOperator(tree.lhs.pos(),
                                        (OperatorSymbol)operator,
					tree.getTag(),
					left,
                                        right);

            // If both arguments are constants, fold them.
            if (left.constValue() != null && right.constValue() != null) {
                Type ctype = cfolder.fold2(opc, left, right);
                if (ctype != null) {
                    owntype = cfolder.coerce(ctype, owntype);

                    // Remove constant types from arguments to
                    // conserve space. The parser will fold concatenations
                    // of string literals; the code here also
                    // gets rid of intermediate results when some of the
                    // operands are constant identifiers.
                    if (tree.lhs.type.tsym == syms.stringType.tsym) {
                        tree.lhs.type = syms.stringType;
                    }
                    if (tree.rhs.type.tsym == syms.stringType.tsym) {
                        tree.rhs.type = syms.stringType;
                    }
                }
            }

            // Check that argument types of a reference ==, != are
            // castable to each other, (JLS???).
            if ((opc == ByteCodes.if_acmpeq || opc == ByteCodes.if_acmpne)) {
                if (!types.isCastable(left, right, new Warner(tree.pos()))) {
                    log.error(tree.pos(), "incomparable.types", left, right);
                }
            }

            chk.checkDivZero(tree.rhs.pos(), operator, right);
        }
        result = check(tree, owntype, VAL, pkind, pt);
    }

    public void visitTypeCast(JCTypeCast tree) {
        Type clazztype = attribType(tree.clazz, env);
        Type exprtype = attribExpr(tree.expr, env, Infer.anyPoly);
        Type owntype = chk.checkCastable(tree.expr.pos(), exprtype, clazztype);
        if (!types.isSubtype(exprtype, clazztype) &&
        	!types.isSubtype(clazztype, exprtype)) {
            // HACK ALERT:  We want to warn about unsound casts, but we need to suppress
            // all the warnings caused by the DPJ runtime, or those warnings swamp the
            // real ones and make the real ones useless.  Once we have separate compilation,
            // this won't be an issue, because we won't need to recompile the DPJ runtime
            // whenever we compile the user program.
            String tstring = env.enclClass.sym.type.toString();
            if (tstring.length() < 10 || !tstring.substring(0, 10).equals("DPJRuntime")) {
        	// Turn this off for now because it causes warnings in the bootstrap
        	// build, causing the build to fail!!1
        	//log.warning(tree.pos(), "unsound.cast");
            }
        }
        if (exprtype.constValue() != null)
            owntype = cfolder.coerce(exprtype, owntype);
        result = check(tree, capture(owntype), VAL, pkind, pt);
    }

    public void visitTypeTest(JCInstanceOf tree) {
        Type exprtype = chk.checkNullOrRefType(
            tree.expr.pos(), attribExpr(tree.expr, env));
        Type clazztype = chk.checkReifiableReferenceType(
            tree.clazz.pos(), attribType(tree.clazz, env));
        chk.checkCastable(tree.expr.pos(), exprtype, clazztype);
        result = check(tree, syms.booleanType, VAL, pkind, pt);
    }

    public void visitIndexed(JCArrayAccess tree) {
        Type owntype = syms.errType;
        Type atype = attribExpr(tree.indexed, env);
        attribExpr(tree.index, env, syms.intType);
        if (types.isArray(atype)) {
            ArrayType at = (ArrayType) atype;
            owntype = types.elemtype(atype);
            if (at.indexVar != null) {
        	owntype = types.substIndices(owntype, List.<VarSymbol>of(at.indexVar),
        		List.<JCExpression>of(tree.index));
            }
        }
        else if (types.isArrayClass(atype)) {
            ClassType ct = (ClassType) atype;
            Type site = capture(ct);
	    Symbol cellSym = rs.findIdentInType(env, site, names.fromString("cell"), VAR);
	    Symbol indexSym = rs.findIdentInType(env,  site, names.fromString("index"), VAR);
	    owntype = types.memberType(site, cellSym);
	    if (indexSym instanceof VarSymbol) {
		owntype = types.substIndices(owntype, List.<VarSymbol>of((VarSymbol) indexSym),
			List.<JCExpression>of(tree.index));
	    }
	    result = check(tree, owntype, VAR, pkind, pt);
            return;
        }
        else if (atype.tag != ERROR)
            log.error(tree.pos(), "array.req.but.found", atype);
        if ((pkind & VAR) == 0) owntype = capture(owntype);
        result = check(tree, owntype, VAR, pkind, pt);
    }

    public void visitIdent(JCIdent tree) {
	
	Symbol sym;
        boolean varArgs = false;

        // Find symbol
        if (pt.tag == METHOD || pt.tag == FORALL) {
            // If we are looking for a method, the prototype `pt' will be a
            // method type with the type of the call's arguments as parameters.
            env.info.varArgs = false;
            sym = rs.resolveMethod(tree.pos(), env, tree.name, pt.getParameterTypes(), 
        	    		   pt.getTypeArguments(), pt.getRPLArguments(),
        	    		   pt.getEffectArguments());
            varArgs = env.info.varArgs;
        } else if (tree.sym != null && tree.sym.kind != VAR) {
            sym = tree.sym;
        } else {
            sym = rs.resolveIdent(tree.pos(), env, tree.name, pkind);
        }
        tree.sym = sym;
        
        // (1) Also find the environment current for the class where
        //     sym is defined (`symEnv').
        // Only for pre-tiger versions (1.4 and earlier):
        // (2) Also determine whether we access symbol out of an anonymous
        //     class in a this or super call.  This is illegal for instance
        //     members since such classes don't carry a this$n link.
        //     (`noOuterThisPath').
        Env<AttrContext> symEnv = env;
        boolean noOuterThisPath = false;
        if (env.enclClass.sym.owner.kind != PCK && // we are in an inner class
            (sym.kind & (VAR | MTH | TYP)) != 0 &&
            sym.owner.kind == TYP &&
            tree.name != names._this && tree.name != names._super) {

            // Find environment in which identifier is defined.
            while (symEnv.outer != null &&
                   !sym.isMemberOf(symEnv.enclClass.sym, types)) {
                if ((symEnv.enclClass.sym.flags() & NOOUTERTHIS) != 0)
                    noOuterThisPath = !allowAnonOuterThis;
                symEnv = symEnv.outer;
            }
        }

        // If symbol is a variable, ...
        if (sym.kind == VAR) {
            VarSymbol v = (VarSymbol)sym;

            // ..., evaluate its initializer, if it has one, and check for
            // illegal forward reference.
            checkInit(tree, env, v, false);

            // If symbol is a local variable accessed from an embedded
            // inner class check that it is final.
            if (v.owner.kind == MTH &&
                v.owner != env.info.scope.owner &&
                (v.flags_field & FINAL) == 0) {
                log.error(tree.pos(),
                          "local.var.accessed.from.icls.needs.final",
                          v);
            }

            // If we are expecting a variable (as opposed to a value), check
            // that the variable is assignable in the current environment.
            if (pkind == VAR)
                checkAssignable(tree.pos(), v, null, env);
        }

        // In a constructor body,
        // if symbol is a field or instance method, check that it is
        // not accessed before the supertype constructor is called.
        if ((symEnv.info.isSelfCall || noOuterThisPath) &&
            (sym.kind & (VAR | MTH)) != 0 &&
            sym.owner.kind == TYP &&
            (sym.flags() & STATIC) == 0) {
            chk.earlyRefError(tree.pos(), sym.kind == VAR ? sym : thisSym(tree.pos(), env));
        }
	Env<AttrContext> env1 = env;
	if (sym.kind != ERR && sym.owner != null && sym.owner != env1.enclClass.sym) {
	    // If the found symbol is inaccessible, then it is
	    // accessed through an enclosing instance.  Locate this
	    // enclosing instance:
	    while (env1.outer != null && !rs.isAccessible(env, env1.enclClass.sym.type, sym))
		env1 = env1.outer;
	}
	env.info.siteVar = null;
	env.info.siteExp = null;
        result = checkId(tree, env1.enclClass.sym.type, sym, env, pkind, pt, varArgs);
        computeCellType(env1, tree.sym, result);
    }

    /**                                                                                                                     
     * Compute the cell type for an array class                                                                             
     * @param env   The current environment                                                                                 
     * @param sym   The type or class symbol whose cell type we are computing                                               
     * @param type  The instantiated type of the class                                                                      
     */
    private void computeCellType(Env<AttrContext> env, Symbol sym, Type type) {
        if ((sym.kind == TYP || sym.kind == CLASS) &&
		types.isArrayClass(type)) {
            Symbol cellSym = rs.findIdentInType(env, type,
	            names.fromString("cell"), VAR);
            if (cellSym != rs.varNotFound) {
                Type cellType = types.memberType(type, cellSym);
                type.setCellType(cellType);
                sym.type.setCellType(cellType);
            }
        }
    }

    public void visitSelect(JCFieldAccess tree) {
        // Determine the expected kind of the qualifier expression.
        int skind = 0;
        if (tree.name == names._this || tree.name == names._super ||
            tree.name == names._class)
        {
            skind = TYP;
        } else {
            if ((pkind & PCK) != 0) skind = skind | PCK;
            if ((pkind & TYP) != 0) skind = skind | TYP | PCK;
            if ((pkind & (VAL | MTH)) != 0) skind = skind | VAL | TYP;
        }

        // Attribute the qualifier expression, and determine its symbol (if any).
        Type site = attribTree(tree.selected, env, skind, Infer.anyPoly);
        if ((pkind & (PCK | TYP)) == 0)
            site = capture(site); // Capture field access

        Symbol selectedSym = tree.selected.getSymbol();
        env.info.siteVar = (selectedSym instanceof VarSymbol) ?
        	(VarSymbol) selectedSym : null;
        env.info.siteExp = tree.selected;

        
        // don't allow T.class T[].class, etc
        if (skind == TYP) {
            Type elt = site;
            while (elt.tag == ARRAY)
                elt = ((ArrayType)elt).elemtype;
            if (elt.tag == TYPEVAR) {
                log.error(tree.pos(), "type.var.cant.be.deref");
                result = syms.errType;
                return;
            }
        }

        // If qualifier symbol is a type or `super', assert `selectSuper'
        // for the selection. This is relevant for determining whether
        // protected symbols are accessible.
        Symbol sitesym = TreeInfo.symbol(tree.selected);
        boolean selectSuperPrev = env.info.selectSuper;
        env.info.selectSuper =
            sitesym != null &&
            sitesym.name == names._super;

        // If selected expression is polymorphic, strip
        // type parameters and remember in env.info.tvars, so that
        // they can be added later (in Attr.checkId and Infer.instantiateMethod).
        if (tree.selected.type.tag == FORALL) {
            ForAll pstype = (ForAll)tree.selected.type;
            env.info.tvars = pstype.tvars;
            env.info.rvars = pstype.rvars;
            site = tree.selected.type = pstype.qtype;
        }

        // Determine the symbol represented by the selection.
        env.info.varArgs = false;
        Symbol sym = selectSym(tree, site, env, pt, pkind);
        if (sym.exists() && !isType(sym) && (pkind & (PCK | TYP)) != 0) {
            site = capture(site);
            sym = selectSym(tree, site, env, pt, pkind);
        }
        boolean varArgs = env.info.varArgs;
        tree.sym = sym;
        
        if (site.tag == TYPEVAR && !isType(sym) && sym.kind != ERR)
            site = capture(site.getUpperBound());

        // If that symbol is a variable, ...
        if (sym.kind == VAR) {
            VarSymbol v = (VarSymbol)sym;

            // ..., evaluate its initializer, if it has one, and check for
            // illegal forward reference.
            checkInit(tree, env, v, true);

            // If we are expecting a variable (as opposed to a value), check
            // that the variable is assignable in the current environment.
            if (pkind == VAR)
                checkAssignable(tree.pos(), v, tree.selected, env);
        }

        // Disallow selecting a type from an expression
        if (isType(sym) && (sitesym==null || (sitesym.kind&(TYP|PCK)) == 0)) {
            tree.type = check(tree.selected, pt,
                              sitesym == null ? VAL : sitesym.kind, TYP|PCK, pt);
        }

        if (isType(sitesym)) {
            if (sym.name == names._this) {
                // If `C' is the currently compiled class, check that
                // C.this' does not appear in a call to a super(...)
                if (env.info.isSelfCall &&
                    site.tsym == env.enclClass.sym) {
                    chk.earlyRefError(tree.pos(), sym);
                }
            } else {
                // Check if type-qualified fields or methods are static (JLS)
                if ((sym.flags() & STATIC) == 0 &&
                    sym.name != names._super &&
                    (sym.kind == VAR || sym.kind == MTH)) {
                    rs.access(rs.new StaticError(sym),
                              tree.pos(), site, sym.name, true);
                }
            }
        }

        // If we are selecting an instance member via a `super', ...
        if (env.info.selectSuper && (sym.flags() & STATIC) == 0) {

            // Check that super-qualified symbols are not abstract (JLS)
            rs.checkNonAbstract(tree.pos(), sym);

            if (site.isRaw()) {
                // Determine argument types for site.
                Type site1 = types.asSuper(env.enclClass.sym.type, site.tsym);
                if (site1 != null) site = site1;
            }
        }

        env.info.selectSuper = selectSuperPrev;
        result = checkId(tree, site, sym, env, pkind, pt, varArgs);
        env.info.tvars = List.nil();
        env.info.rvars = List.nil();
        computeCellType(env, tree.sym, result);
        
    }
    //where
        /** Determine symbol referenced by a Select expression,
         *
         *  @param tree   The select tree.
         *  @param site   The type of the selected expression,
         *  @param env    The current environment.
         *  @param pt     The current prototype.
         *  @param pkind  The expected kind(s) of the Select expression.
         */
        protected Symbol selectSym(JCFieldAccess tree, // DPJ: Changed private to protected
                                 Type site,
                                 Env<AttrContext> env,
                                 Type pt,
                                 int pkind) {
            DiagnosticPosition pos = tree.pos();
            Name name = tree.name;

            switch (site.tag) {
            case PACKAGE:
                return rs.access(
                    rs.findIdentInPackage(env, site.tsym, name, pkind),
                    pos, site, name, true);
            case ARRAY:
            case CLASS:
                if (pt.tag == METHOD || pt.tag == FORALL) {
                    return rs.resolveQualifiedMethod(
                        pos, env, site, name, pt.getParameterTypes(), pt.getTypeArguments(),
                        pt.getRPLArguments(), pt.getEffectArguments());
                } else if (name == names._this || name == names._super) {
                    return rs.resolveSelf(pos, env, site.tsym, name);
                } else if (name == names._class) {
                    // In this case, we have already made sure in
                    // visitSelect that qualifier expression is a type.
                    Type t = syms.classType;
                    List<Type> typeargs = allowGenerics
                        ? List.of(types.erasure(site))
                        : List.<Type>nil();
                    t = new ClassType(t.getEnclosingType(), typeargs, 
                	    List.<RPL>nil(), 
                	    List.<Effects>nil(), t.tsym, null);
                    return new VarSymbol(
                        STATIC | PUBLIC | FINAL, names._class, t, site.tsym);
                } else {
                    // We are seeing a plain identifier as selector.
                    Symbol sym = rs.findIdentInType(env, site, name, pkind);
                    if ((pkind & ERRONEOUS) == 0)
                        sym = rs.access(sym, pos, site, name, true);
                    return sym;
                }
            case WILDCARD:
                throw new AssertionError(tree);
            case TYPEVAR:
                // Normally, site.getUpperBound() shouldn't be null.
                // It should only happen during memberEnter/attribBase
                // when determining the super type which *must* be
                // done before attributing the type variables.  In
                // other words, we are seeing this illegal program:
                // class B<T> extends A<T.foo> {}
                Symbol sym = (site.getUpperBound() != null)
                    ? selectSym(tree, capture(site.getUpperBound()), env, pt, pkind)
                    : null;
                if (sym == null || isType(sym)) {
                    log.error(pos, "type.var.cant.be.deref");
                    return syms.errSymbol;
                } else {
                    return sym;
                }
            case ERROR:
                // preserve identifier names through errors
                return new ErrorType(name, site.tsym).tsym;
            default:
                // The qualifier expression is of a primitive type -- only
                // .class is allowed for these.
                if (name == names._class) {
                    // In this case, we have already made sure in Select that
                    // qualifier expression is a type.
                    Type t = syms.classType;
                    Type arg = types.boxedClass(site).type;
                    t = new ClassType(t.getEnclosingType(), List.of(arg), 
                	    List.<RPL>nil(), 
                	    List.<Effects>nil(), t.tsym, null);
                    return new VarSymbol(
                        STATIC | PUBLIC | FINAL, names._class, t, site.tsym);
                } else {
                    log.error(pos, "cant.deref", site);
                    return syms.errSymbol;
                }
            }
        }

        /** Determine type of identifier or select expression and check that
         *  (1) the referenced symbol is not deprecated
         *  (2) the symbol's type is safe (@see checkSafe)
         *  (3) if symbol is a variable, check that its type and kind are
         *      compatible with the prototype and protokind.
         *  (4) if symbol is an instance field of a raw type,
         *      which is being assigned to, issue an unchecked warning if its
         *      type changes under erasure.
         *  (5) if symbol is an instance method of a raw type, issue an
         *      unchecked warning if its argument types change under erasure.
         *  If checks succeed:
         *    If symbol is a constant, return its constant type
         *    else if symbol is a method, return its result type
         *    otherwise return its type.
         *  Otherwise return errType.
         *
         *  @param tree       The syntax tree representing the identifier
         *  @param site       If this is a select, the type of the selected
         *                    expression, otherwise the type of the current class.
         *  @param sym        The symbol representing the identifier.
         *  @param env        The current environment.
         *  @param pkind      The set of expected kinds.
         *  @param pt         The expected type.
         */
        Type checkId(JCTree tree,
                     Type site,
                     Symbol sym,
                     Env<AttrContext> env,
                     int pkind,
                     Type pt,
                     boolean useVarargs) {
            if (pt.isErroneous()) return syms.errType;
            Type owntype; // The computed type of this identifier occurrence.
            switch (sym.kind) {
            case TYP:
                // For types, the computed type equals the symbol's type,
                // except for two situations:
                owntype = sym.type;
                if (owntype.tag == CLASS) {
                    int numParams = owntype.tsym.type.getRPLArguments().size();
                    Type ownOuter = owntype.getEnclosingType();
                    
                    // (a) If the symbol's type is parameterized, erase it
                    // because no type parameters were given.
                    // We recover generic outer type later in visitTypeApply.
                    if (owntype.tsym.type.getTypeArguments().nonEmpty() ||
                	    owntype.tsym.type.getRPLArguments().nonEmpty()) {
                	List<RPL> regionParams = 
                	    owntype.tsym.type.getRPLArguments();
                        owntype = types.erasure(owntype);
                        // Don't erase the region params!
                        ((ClassType) owntype).rplparams_field = regionParams;
                        // Put the default region of ROOT in for each param position.
                        // If this type is subject to a region apply, the ROOT's will
                        // get replaced.  Otherwise, we have a type instantiated
                        // with ROOT's.  This is the DPJ equivalent of a Java "raw type."
                        ListBuffer<RPL> buf = ListBuffer.lb();
                        for (int i = 0; i < numParams; ++i) {
                            buf.append(RPLs.ROOT);
                        }
                        ((ClassType) owntype).rplparams_field = buf.toList();
                    }

                    // (b) If the symbol's type is an inner class, then
                    // we have to interpret its outer type as a superclass
                    // of the site type. Example:
                    //
                    // class Tree<A> { class Visitor { ... } }
                    // class PointTree extends Tree<Point> { ... }
                    // ...PointTree.Visitor...
                    //
                    // Then the type of the last expression above is
                    // Tree<Point>.Visitor.
                    else if (ownOuter.tag == CLASS && site != ownOuter) {
                        Type normOuter = site;
                        if (normOuter.tag == CLASS)
                            normOuter = types.asEnclosingSuper(site, ownOuter.tsym);
                        if (normOuter == null) // perhaps from an import
                            normOuter = types.erasure(ownOuter);
                        if (normOuter != ownOuter)
                            owntype = new ClassType(
                                normOuter, List.<Type>nil(), 
                                List.<RPL>nil(), 
                                List.<Effects>nil(), owntype.tsym, null);
                    }
                }
                break;
            case VAR:
                VarSymbol v = (VarSymbol)sym;
                // Test (4): if symbol is an instance field of a raw type,
                // which is being assigned to, issue an unchecked warning if
                // its type changes under erasure.
                if (allowGenerics &&
                    pkind == VAR &&
                    v.owner.kind == TYP &&
                    (v.flags() & STATIC) == 0 &&
                    (site.tag == CLASS || site.tag == TYPEVAR)) {
                    Type s = types.asOuterSuper(site, v.owner);
                    if (s != null &&
                        s.isRaw() &&
                        !types.isSameType(v.type, v.erasure(types))) {
                        chk.warnUnchecked(tree.pos(),
                                          "unchecked.assign.to.var",
                                          v, s);
                    }
                }
                // The computed type of a variable is the type of the
                // variable symbol, taken as a member of the site type.
                owntype = (sym.owner.kind == TYP &&
                           sym.name != names._this && sym.name != names._super)
                    ? types.memberType(site, sym)
                    : sym.type;

                if (env.info.tvars.nonEmpty() || env.info.rvars.nonEmpty()) {
                    Type owntype1 = new ForAll(env.info.tvars, env.info.rvars, 
                	    List.<Effects>nil(), owntype);
                    for (List<Type> l = env.info.tvars; l.nonEmpty(); l = l.tail)
                        if (!owntype.contains(l.head)) {
                            log.error(tree.pos(), "undetermined.type", owntype1);
                            owntype1 = syms.errType;
                        }
                    owntype = owntype1;
                }

                // Substitutions required by DPJ type system
                RPL selectedRPL = exprToRPL(rs.selectedExp(tree, env));
                owntype = types.substRPLForVar(owntype, rs.findThis(env), selectedRPL);
                
                // If the variable is a constant, record constant value in
                // computed type.
                if (v.getConstValue() != null && isStaticReference(tree))
                    owntype = owntype.constType(v.getConstValue());

                if (pkind == VAL) {
                    owntype = capture(owntype); // capture "names as expressions"
                }
                break;
            case MTH: {
                JCMethodInvocation app = (JCMethodInvocation)env.tree;
                owntype = checkMethod(site, sym, env, app.args,
                                      pt.getParameterTypes(), pt.getTypeArguments(),
                                      pt.getRPLArguments(), pt.getEffectArguments(),
                                      env.info.varArgs);
                break;
            }
            case PCK: case ERR:
            case RPL_ELT: case EFFECT:
                owntype = sym.type;
                break;
            default:
                throw new AssertionError("unexpected kind: " + sym.kind +
                                         " in tree " + tree);
            }

            // Test (1): emit a `deprecation' warning if symbol is deprecated.
            // (for constructors, the error was given when the constructor was
            // resolved)
            if (sym.name != names.init &&
                (sym.flags() & DEPRECATED) != 0 &&
                (env.info.scope.owner.flags() & DEPRECATED) == 0 &&
                sym.outermostClass() != env.info.scope.owner.outermostClass())
                chk.warnDeprecated(tree.pos(), sym);

            if ((sym.flags() & PROPRIETARY) != 0)
                log.strictWarning(tree.pos(), "sun.proprietary", sym);

            // Test (3): if symbol is a variable, check that its type and
            // kind are compatible with the prototype and protokind.
            return check(tree, owntype, sym.kind, pkind, pt);
        }

        /** Check that variable is initialized and evaluate the variable's
         *  initializer, if not yet done. Also check that variable is not
         *  referenced before it is defined.
         *  @param tree    The tree making up the variable reference.
         *  @param env     The current environment.
         *  @param v       The variable's symbol.
         */
        private void checkInit(JCTree tree,
                               Env<AttrContext> env,
                               VarSymbol v,
                               boolean onlyWarning) {
//          System.err.println(v + " " + ((v.flags() & STATIC) != 0) + " " +
//                             tree.pos + " " + v.pos + " " +
//                             Resolve.isStatic(env));//DEBUG

            // A forward reference is diagnosed if the declaration position
            // of the variable is greater than the current tree position
            // and the tree and variable definition occur in the same class
            // definition.  Note that writes don't count as references.
            // This check applies only to class and instance
            // variables.  Local variables follow different scope rules,
            // and are subject to definite assignment checking.
            if (v.pos > tree.pos &&
                v.owner.kind == TYP &&
                canOwnInitializer(env.info.scope.owner) &&
                v.owner == env.info.scope.owner.enclClass() &&
                ((v.flags() & STATIC) != 0) == Resolve.isStatic(env) &&
                (env.tree.getTag() != JCTree.ASSIGN ||
                 TreeInfo.skipParens(((JCAssign) env.tree).lhs) != tree)) {

                if (!onlyWarning || isNonStaticEnumField(v)) {
                    log.error(tree.pos(), "illegal.forward.ref");
                } else if (useBeforeDeclarationWarning) {
                    log.warning(tree.pos(), "forward.ref", v);
                }
            }

            v.getConstValue(); // ensure initializer is evaluated

            checkEnumInitializer(tree, env, v);
        }

        /**
         * Check for illegal references to static members of enum.  In
         * an enum type, constructors and initializers may not
         * reference its static members unless they are constant.
         *
         * @param tree    The tree making up the variable reference.
         * @param env     The current environment.
         * @param v       The variable's symbol.
         * @see JLS 3rd Ed. (8.9 Enums)
         */
        private void checkEnumInitializer(JCTree tree, Env<AttrContext> env, VarSymbol v) {
            // JLS 3rd Ed.:
            //
            // "It is a compile-time error to reference a static field
            // of an enum type that is not a compile-time constant
            // (15.28) from constructors, instance initializer blocks,
            // or instance variable initializer expressions of that
            // type. It is a compile-time error for the constructors,
            // instance initializer blocks, or instance variable
            // initializer expressions of an enum constant e to refer
            // to itself or to an enum constant of the same type that
            // is declared to the right of e."
            if (isNonStaticEnumField(v)) {
                ClassSymbol enclClass = env.info.scope.owner.enclClass();

                if (enclClass == null || enclClass.owner == null)
                    return;

                // See if the enclosing class is the enum (or a
                // subclass thereof) declaring v.  If not, this
                // reference is OK.
                if (v.owner != enclClass && !types.isSubtype(enclClass.type, v.owner.type))
                    return;

                // If the reference isn't from an initializer, then
                // the reference is OK.
                if (!Resolve.isInitializer(env))
                    return;

                log.error(tree.pos(), "illegal.enum.static.ref");
            }
        }

        private boolean isNonStaticEnumField(VarSymbol v) {
            return Flags.isEnum(v.owner) && Flags.isStatic(v) && !Flags.isConstant(v);
        }

        /** Can the given symbol be the owner of code which forms part
         *  if class initialization? This is the case if the symbol is
         *  a type or field, or if the symbol is the synthetic method.
         *  owning a block.
         */
        private boolean canOwnInitializer(Symbol sym) {
            return
                (sym.kind & (VAR | TYP)) != 0 ||
                (sym.kind == MTH && (sym.flags() & BLOCK) != 0);
        }

    Warner noteWarner = new Warner();

    /**
     * Check that method arguments conform to its instantiation.
     **/
    public Type checkMethod(Type site,
                            Symbol sym,
                            Env<AttrContext> env,
                            final List<JCExpression> argtrees,
                            List<Type> argtypes,
                            List<Type> typeargtypes,
                            List<RPL> regionargs,
                            List<Effects> effectargs,
                            boolean useVarargs) {
        // Test (5): if symbol is an instance method of a raw type, issue
        // an unchecked warning if its argument types change under erasure.
        if (allowGenerics &&
            (sym.flags() & STATIC) == 0 &&
            (site.tag == CLASS || site.tag == TYPEVAR)) {
            Type s = types.asOuterSuper(site, sym.owner);
            if (s != null && s.isRaw() &&
                !types.isSameTypes(sym.type.getParameterTypes(),
                                   sym.erasure(types).getParameterTypes())) {
                chk.warnUnchecked(env.tree.pos(),
                                  "unchecked.call.mbr.of.raw.type",
                                  sym, s);
            }
        }

        // Compute the identifier's instantiated type.
        // For methods, we need to compute the instance type by
        // Resolve.instantiate from the symbol's type as well as
        // any region args, type arguments, and value arguments.
        noteWarner.warned = false;
        Type owntype = rs.instantiate(env,
                                      site,
                                      sym,
                                      argtypes,
                                      typeargtypes,
                                      regionargs,
                                      effectargs,
                                      true,
                                      useVarargs,
                                      noteWarner);
        boolean warned = noteWarner.warned;
        
        // If this fails, something went wrong; we should not have
        // found the identifier in the first place.
        if (owntype == null) {
            if (!pt.isErroneous())
                log.error(env.tree.pos(),
                          "internal.error.cant.instantiate",
                          sym, site,
                          RPL.toString(pt.getRPLArguments()) + Type.toString(pt.getParameterTypes()));
            owntype = syms.errType;
        } else {
            // System.out.println("call   : " + env.tree);
            // System.out.println("method : " + owntype);
            // System.out.println("actuals: " + argtypes);
            List<Type> formals = owntype.getParameterTypes();
            Type last = useVarargs ? formals.last() : null;
            if (sym.name==names.init &&
                sym.owner == syms.enumSym)
                formals = formals.tail.tail;
            List<JCExpression> args = argtrees;
            while (formals.head != last) {
                JCTree arg = args.head;
                Warner warn = chk.convertWarner(arg.pos(), arg.type, formals.head);
                assertConvertible(arg, arg.type, formals.head, warn);
                warned |= warn.warned;
                args = args.tail;
                formals = formals.tail;
            }
            if (useVarargs) {
                Type varArg = types.elemtype(last);
                while (args.tail != null) {
                    JCTree arg = args.head;
                    Warner warn = chk.convertWarner(arg.pos(), arg.type, varArg);
                    assertConvertible(arg, arg.type, varArg, warn);
                    warned |= warn.warned;
                    args = args.tail;
                }
            } else if ((sym.flags() & VARARGS) != 0 && allowVarargs) {
                // non-varargs call to varargs method
                Type varParam = owntype.getParameterTypes().last();
                Type lastArg = argtypes.last();
                if (types.isSubtypeUnchecked(lastArg, types.elemtype(varParam)) &&
                    !types.isSameType(types.erasure(varParam), types.erasure(lastArg)))
                    log.warning(argtrees.last().pos(), "inexact.non-varargs.call",
                                types.elemtype(varParam),
                                varParam);
            }

            if (warned && sym.type.tag == FORALL) {
                String typeargs = "";
                if (typeargtypes != null && typeargtypes.nonEmpty()) {
                    typeargs = "<" + Type.toString(typeargtypes) + ">";
                }
                chk.warnUnchecked(env.tree.pos(),
                                  "unchecked.meth.invocation.applied",
                                  sym,
                                  sym.location(),
                                  typeargs,
                                  Type.toString(argtypes));
                owntype = new MethodType(owntype.getParameterTypes(),
                                         types.erasure(owntype.getReturnType()),
                                         owntype.getThrownTypes(),
                                         syms.methodClass);
            }
            if (useVarargs) {
                JCTree tree = env.tree;
                Type argtype = owntype.getParameterTypes().last();
                if (!types.isReifiable(argtype))
                    chk.warnUnchecked(env.tree.pos(),
                                      "unchecked.generic.array.creation",
                                      argtype);
                Type elemtype = types.elemtype(argtype);
                switch (tree.getTag()) {
                case JCTree.APPLY:
                    ((JCMethodInvocation) tree).varargsElement = elemtype;
                    break;
                case JCTree.NEWCLASS:
                    ((JCNewClass) tree).varargsElement = elemtype;
                    break;
                default:
                    throw new AssertionError(""+tree);
                }
            }
        }
        return owntype;
    }

    private void assertConvertible(JCTree tree, Type actual, Type formal, Warner warn) {
        if (types.isConvertible(actual, formal, warn))
            return;

        if (formal.isCompound()
            && types.isSubtype(actual, types.supertype(formal))
            && types.isSubtypeUnchecked(actual, types.interfaces(formal), warn))
            return;

        if (false) {
            // TODO: make assertConvertible work
            chk.typeError(tree.pos(), JCDiagnostic.fragment("incompatible.types"), actual, formal);
            throw new AssertionError("Tree: " + tree
                                     + " actual:" + actual
                                     + " formal: " + formal);
        }
    }

    public void visitLiteral(JCLiteral tree) {
        result = check(
            tree, litType(tree.typetag).constType(tree.value), VAL, pkind, pt);
    }
    //where
    /** Return the type of a literal with given type tag.
     */
    Type litType(int tag) {
        return (tag == TypeTags.CLASS) ? syms.stringType : syms.typeOfTag[tag];
    }

    public void visitTypeIdent(JCPrimitiveTypeTree tree) {
        result = check(tree, syms.typeOfTag[tree.typetag], TYP, pkind, pt);
    }

    @Override public void visitTypeArray(JCArrayTypeTree tree) {
	VarSymbol indexVar = null;
	Env<AttrContext> localEnv =
	    env.dup(tree, env.info.dup(env.info.scope.dup()));
	// If we're in an initializer, we need the actual scope 
	// so we can add params to it
	Scope scope = enter.enterScope(localEnv).getActualScope();
	if (tree.indexParam != null) {
	    indexVar =
		new VarSymbol(STATIC, tree.indexParam.name, 
			syms.intType, scope.owner);
	    tree.indexParam.sym = indexVar;
	    scope.enter(indexVar);
	}
	if (tree.rpl != null) {
	    attribTree(tree.rpl, localEnv, NIL, Type.noType);
	}
	Type etype = attribType(tree.elemtype, localEnv);
	Type type = new ArrayType(etype, null, indexVar, syms.arrayClass);
	result = check(tree, type, TYP, pkind, pt);
	localEnv.info.scope.leave();
	if (tree.rpl != null)
	    ((ArrayType) tree.type).rpl = tree.rpl.rpl;
    }
    
    /** Visitor method for parameterized types.
     *  Bound checking is left until later, since types are attributed
     *  before supertype structure is completely known
     */
    public void visitTypeApply(JCTypeApply tree) {
	
	Type owntype = syms.errType;

        // Attribute functor part of application
        Type functortype = attribType(tree.functor, env); 
        
        // If functor is a type var, handle it
        if (functortype.tag == TYPEVAR) {
            
            TypeVar tvartype = (TypeVar) functortype;
            
            // Collect all the RPL args, some of which may have been parsed as
            // types
            List<DPJRegionPathList> rplArgs = 
        	(tree.rplArgs == null) ? List.<DPJRegionPathList>nil() : tree.rplArgs;
            tree.rplArgs = asRPLs(tree.typeArgs);
            tree.typeArgs = List.nil();
            tree.rplArgs = rplArgs.appendList(tree.rplArgs);
            
            // Attribute the RPL args
            List<RPL> rplargs = attribRPLs(tree.rplArgs);
            
            // Check # of args
            if (rplargs.size() != tvartype.rplparams.size()) {
        	log.error(tree.pos(), "wrong.number.type.args",
			Integer.toString(tvartype.rplparams.size()));
            } else {
        	TypeVar newtvar = null;
        	owntype = newtvar = 
        	    new TypeVar(tvartype.tsym, tvartype.getUpperBound(), tvartype.lower);
        	newtvar.rplparams = newtvar.rplparams;
        	newtvar.rplargs = rplargs;
        	newtvar.prototype = tvartype;
            }
            
            result = check(tree, owntype, TYP, pkind, pt);
            return;
        }

        // Otherwise make sure it's a class...
        chk.checkClassType(tree.functor.pos(), functortype);
        // ...and handle it
        if (functortype.tag == CLASS) {

            // Get the formal type parameters
            List<Type> typeFormals = 
        	functortype.tsym.type.getTypeArguments();

            // Get the formal region params
            List<RPL> rplFormals =
        	functortype.tsym.type.getRPLArguments();

            // Get the formal effect params
            List<Effects> effectFormals =
        	functortype.tsym.type.getEffectArguments();
            
            // Handle error case of unexpected args
            if (typeFormals.isEmpty() && 
        	    rplFormals.isEmpty() &&
        	    	effectFormals.isEmpty() &&
        	    tree.typeArgs.nonEmpty()) {
                log.error(tree.pos(), "type.doesnt.take.params", functortype.tsym);
                result = check(tree, owntype, TYP, pkind, pt);
                return;
            }    
            
            // Use the # of type params to separate the type args from the RPL args
            int counter = 0;
            int size = typeFormals.size();
            List<JCExpression> listptr = tree.typeArgs;
            List<JCExpression> rplArgs = List.nil();
            if (size > tree.typeArgs.size() && tree.typeArgs.nonEmpty()) {
        	log.error(tree.pos(), "wrong.number.type.args",
        		Integer.toString(size));
            } else {
        	do {
        	    if (counter >= size) {
        		if (counter == 0) {
        		    // No type arguments expected!
        		    rplArgs = tree.typeArgs;
        		    tree.typeArgs = List.nil();
        		} else {
        		    // Rest of list from listptr.tail is rpl args
        		    rplArgs = listptr.tail;
        		    listptr.tail = List.nil();
        		}
        	    } else {
        		if (listptr == null || listptr.head == null) {
        		    log.error(tree.pos(), "wrong.number.type.args",
        			    Integer.toString(typeFormals.length()));
        	    		break;
        		} else {
        		    if (counter > 0)
        			listptr = listptr.tail;
        		}
        	    }
        	} while (counter++ < size);
            }

            if (tree.rplArgs == null) {
        	// Construct the list of rpl arguments and store it in the AST
        	tree.rplArgs = asRPLs(rplArgs);
            } else {
        	// We already have rplargs in the tree, so there shouldn't be any
        	// typeargs left over as rplargs
        	if (rplArgs.nonEmpty()) {
        	    log.error(tree.pos(), "wrong.number.type.args",
			Integer.toString(typeFormals.length()));        	    
        	}
            }

            // Attribute type args
            List<Type> actuals = attribTypes(tree.typeArgs, env);            

            List<Type> a = actuals;
            List<Type> f = typeFormals;
            while (a.nonEmpty()) {
        	a.head = a.head.withTypeVar(f.head);
        	a = a.tail;
        	f = f.tail;
            }

            ClassType ct = (ClassType) functortype;
            List<RPL> rplActuals = null;
            // Attribute RPL args.
            rplActuals = attribRPLs(tree.rplArgs);
            // Check actual vs. expected # of RPL args
            counter = rplFormals.size() - rplActuals.size();
            if (counter < 0) {
        	// Too many rpl args ==> error
        	log.error(tree.pos(), "too.many.rpl.args",
        		Integer.toString(rplFormals.size()));
            } else {
        	// Not enough rpl args ==> fill the rest in with Root
        	while (counter-- > 0) {
        	    rplActuals = rplActuals.append(RPLs.ROOT);
        	}
            }

            // Attribute effect args
            // TODO: Why does the null reference occur?
            if (tree.effectArgs == null) tree.effectArgs = List.nil();
            List<Effects> effectActuals = attribEffects(tree.effectArgs);
            if (effectActuals.length() != effectFormals.length()) {
        	log.error(tree.pos(), "wrong.number.effect.args",
        		Integer.toString(effectFormals.length())); 
            }
            
            // Compute the proper generic outer
            Type clazzOuter = functortype.getEnclosingType();
            if (clazzOuter.tag == CLASS) {
        	Type site;
        	if (tree.functor.getTag() == JCTree.IDENT) {
        	    site = env.enclClass.sym.type;
        	} else if (tree.functor.getTag() == JCTree.SELECT) {
        	    site = ((JCFieldAccess) tree.functor).selected.type;
        	} else throw new AssertionError(""+tree);
        	if (clazzOuter.tag == CLASS && site != clazzOuter) {
        	    if (site.tag == CLASS)
        		site = types.asOuterSuper(site, clazzOuter.tsym);
        	    if (site == null)
        		site = types.erasure(clazzOuter);
        	    clazzOuter = site;
        	}
            }
            
            // Construct the instantiated type with the type, RPL, and effect args
            owntype = new ClassType(clazzOuter, actuals, 
        	    rplActuals, effectActuals,
        	    functortype.tsym, null);
        }
        result = check(tree, owntype, TYP, pkind, pt);
        computeCellType(env, tree.functor.getSymbol(), result);
    }

    /** Helper function:  Make a list of expressions, some of which may have
     *  been parsed as types, into a list of RPLs.
     */
    private List<DPJRegionPathList> asRPLs(List<JCExpression> trees) {
	if (trees == null) return List.nil();
        ListBuffer<DPJRegionPathList> buf = ListBuffer.lb();
        for (JCExpression expr : trees) {
            DPJRegionPathList rpl = null;
            if (expr instanceof DPJRegionPathList) {
		// If we parsed the argument as an RPL, put it in the list.
        	rpl = (DPJRegionPathList) expr;
            } else {
		// If we parsed the argument as a type, then make it into an RPL now.
        	DPJRegionPathListElt elt = 
        	    make.at(expr.pos).RegionPathListElt(expr, DPJRegionPathListElt.NAME);
        	rpl = make.at(expr.pos()).RegionPathList(List.of(elt));
            }
            buf.append(rpl);
        }
        return buf.toList();
    }
    
    public void visitTypeParameter(JCTypeParameter tree) {
        TypeVar a = (TypeVar)tree.type;
        Set<Type> boundSet = new HashSet<Type>();
        if (a.getUpperBound().isErroneous())
            return;
        List<Type> bs = types.getBounds(a);
        if (tree.bounds.nonEmpty()) {
            // accept class or interface or typevar as first bound.
            Type b = checkBase(bs.head, tree.bounds.head, env, false, false, false);
            boundSet.add(types.erasure(b));
            if (b.tag == TYPEVAR) {
                // if first bound was a typevar, do not accept further bounds.
                if (tree.bounds.tail.nonEmpty()) {
                    log.error(tree.bounds.tail.head.pos(),
                              "type.var.may.not.be.followed.by.other.bounds");
                    tree.bounds = List.of(tree.bounds.head);
                }
            } else {
                // if first bound was a class or interface, accept only interfaces
                // as further bounds.
                for (JCExpression bound : tree.bounds.tail) {
                    bs = bs.tail;
                    Type i = checkBase(bs.head, bound, env, false, true, false);
                    if (i.tag == CLASS)
                        chk.checkNotRepeated(bound.pos(), types.erasure(i), boundSet);
                }
            }
        }
        bs = types.getBounds(a);

        // in case of multiple bounds ...
        if (bs.length() > 1) {
            // ... the variable's bound is a class type flagged COMPOUND
            // (see comment for TypeVar.bound).
            // In this case, generate a class tree that represents the
            // bound class, ...
            JCTree extending;
            List<JCExpression> implementing;
            if ((bs.head.tsym.flags() & INTERFACE) == 0) {
                extending = tree.bounds.head;
                implementing = tree.bounds.tail;
            } else {
                extending = null;
                implementing = tree.bounds;
            }
            JCClassDecl cd = make.at(tree.pos).ClassDef(
                make.Modifiers(PUBLIC | ABSTRACT),
                tree.name, null,
                List.<JCTypeParameter>nil(),
                extending, implementing, List.<JCTree>nil());

            ClassSymbol c = (ClassSymbol)a.getUpperBound().tsym;
            assert (c.flags() & COMPOUND) != 0;
            cd.sym = c;
            c.sourcefile = env.toplevel.sourcefile;

            // ... and attribute the bound class
            c.flags_field |= UNATTRIBUTED;
            Env<AttrContext> cenv = enter.classEnv(cd, env);
            enter.typeEnvs.put(c, cenv);
        }
    }


    public void visitWildcard(JCWildcard tree) {
        //- System.err.println("visitWildcard("+tree+");");//DEBUG
        Type type = (tree.kind.kind == BoundKind.UNBOUND)
            ? syms.objectType
            : attribType(tree.inner, env);
        result = check(tree, new WildcardType(chk.checkRefType(tree.pos(), type),
                                              tree.kind.kind,
                                              syms.boundClass),
                       TYP, pkind, pt);
    }

    public void visitAnnotation(JCAnnotation tree) {
        log.error(tree.pos(), "annotation.not.valid.for.type", pt);
        result = tree.type = syms.errType;
    }

    public void visitErroneous(JCErroneous tree) {
        if (tree.errs != null)
            for (JCTree err : tree.errs)
                attribTree(err, env, ERR, pt);
        result = tree.type = syms.errType;
    }

    /** Default visitor method for all other trees.
     */
    public void visitTree(JCTree tree) {
        throw new AssertionError();
    }

    /** Main method: attribute class definition associated with given class symbol.
     *  reporting completion failures at the given position.
     *  @param pos The source position at which completion errors are to be
     *             reported.
     *  @param c   The class symbol whose definition will be attributed.
     */
    public void attribClass(DiagnosticPosition pos, ClassSymbol c) {
        try {
            annotate.flush();
            attribClass(c);
        } catch (CompletionFailure ex) {
            chk.completionError(pos, ex);
        }
    }

    /** Attribute class definition associated with given class symbol.
     *  @param c   The class symbol whose definition will be attributed.
     */
    void attribClass(ClassSymbol c) throws CompletionFailure {
        if (c.type.tag == ERROR) return;

        // Check for cycles in the inheritance graph, which can arise from
        // ill-formed class files.
        chk.checkNonCyclic(null, c.type);

        Type st = types.supertype(c.type);
        if ((c.flags_field & Flags.COMPOUND) == 0) {
            // First, attribute superclass.
            if (st.tag == CLASS)
                attribClass((ClassSymbol)st.tsym);

            // Next attribute owner, if it is a class.
            if (c.owner.kind == TYP && c.owner.type.tag == CLASS)
                attribClass((ClassSymbol)c.owner);
        }

        // The previous operations might have attributed the current class
        // if there was a cycle. So we test first whether the class is still
        // UNATTRIBUTED.
        if ((c.flags_field & UNATTRIBUTED) != 0) {
            c.flags_field &= ~UNATTRIBUTED;

            // Get environment current at the point of class definition.
            Env<AttrContext> env = enter.typeEnvs.get(c);

            // The info.lint field in the envs stored in enter.typeEnvs is deliberately uninitialized,
            // because the annotations were not available at the time the env was created. Therefore,
            // we look up the environment chain for the first enclosing environment for which the
            // lint value is set. Typically, this is the parent env, but might be further if there
            // are any envs created as a result of TypeParameter nodes.
            Env<AttrContext> lintEnv = env;
            while (lintEnv.info.lint == null)
                lintEnv = lintEnv.next;

            // Having found the enclosing lint value, we can initialize the lint value for this class
            env.info.lint = lintEnv.info.lint.augment(c.attributes_field, c.flags());

            Lint prevLint = chk.setLint(env.info.lint);
            JavaFileObject prev = log.useSource(c.sourcefile);

            try {
                // java.lang.Enum may not be subclassed by a non-enum
                if (st.tsym == syms.enumSym &&
                    ((c.flags_field & (Flags.ENUM|Flags.COMPOUND)) == 0))
                    log.error(env.tree.pos(), "enum.no.subclassing");

                // Enums may not be extended by source-level classes
                if (st.tsym != null &&
                    ((st.tsym.flags_field & Flags.ENUM) != 0) &&
                    ((c.flags_field & Flags.ENUM) == 0) &&
                    !target.compilerBootstrap(c)) {
                    log.error(env.tree.pos(), "enum.types.not.extensible");
                }
                attribClassBody(env, c);

                chk.checkDeprecatedAnnotation(env.tree.pos(), c);
            } finally {
                log.useSource(prev);
                chk.setLint(prevLint);
            }
        }
    }

    public void visitImport(JCImport tree) {
        // nothing to do
    }

    /** Finish the attribution of a class. */
    private void attribClassBody(Env<AttrContext> env, ClassSymbol c) {
        JCClassDecl tree = (JCClassDecl)env.tree;
        assert c == tree.sym;

        // Validate annotations
        chk.validateAnnotations(tree.mods.annotations, c);

        // Validate type parameters, supertype and interfaces.
        attribBounds(tree.typarams);
        chk.validateTypeParams(tree.typarams);
        chk.validate(tree.extending);
        chk.validate(tree.implementing);

        // If this is a non-abstract class, check that it has no abstract
        // methods or unimplemented methods of an implemented interface.
        if ((c.flags() & (ABSTRACT | INTERFACE)) == 0) {
            if (!relax)
                chk.checkAllDefined(tree.pos(), c);
        }

        if ((c.flags() & ANNOTATION) != 0) {
            if (tree.implementing.nonEmpty())
                log.error(tree.implementing.head.pos(),
                          "cant.extend.intf.annotation");
            if (tree.typarams.nonEmpty())
                log.error(tree.typarams.head.pos(),
                          "intf.annotation.cant.have.type.params");
        } else {
            // Check that all extended classes and interfaces
            // are compatible (i.e. no two define methods with same arguments
            // yet different return types).  (JLS 8.4.6.3)
            chk.checkCompatibleSupertypes(tree.pos(), c.type);
        }

        // Check that class does not import the same parameterized interface
        // with two different argument lists.
        chk.checkClassBounds(tree.pos(), c.type);

        tree.type = c.type;

        boolean assertsEnabled = false;
        assert assertsEnabled = true;
        if (assertsEnabled) {
            for (List<JCTypeParameter> l = tree.typarams;
                 l.nonEmpty(); l = l.tail)
                assert env.info.scope.lookup(l.head.name).scope != null;
        }

        // Check that a generic class doesn't extend Throwable
        if (!c.type.alltyparams().isEmpty() && types.isSubtype(c.type, syms.throwableType))
            log.error(tree.extending.pos(), "generic.throwable");

        // Check that all methods which implement some
        // method conform to the method they implement.
        chk.checkImplementations(tree);

        for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
            // Attribute declaration
            attribStat(l.head, env);
            // Check that declarations in inner classes are not static (JLS 8.1.2)
            // Make an exception for static constants.
            if (c.owner.kind != PCK &&
                ((c.flags() & STATIC) == 0 || c.name == names.empty) &&
                (TreeInfo.flags(l.head) & (STATIC | INTERFACE)) != 0) {
                Symbol sym = null;
                if (l.head.getTag() == JCTree.VARDEF) sym = ((JCVariableDecl) l.head).sym;
                if (sym == null ||
                    sym.kind != VAR ||
                    ((VarSymbol) sym).getConstValue() == null)
                    log.error(l.head.pos(), "icls.cant.have.static.decl");
            }
        }

        // Check for cycles among non-initial constructors.
        chk.checkCyclicConstructors(tree);

        // Check for cycles among annotation elements.
        chk.checkNonCyclicElements(tree);

        // Check for proper use of serialVersionUID
        if (env.info.lint.isEnabled(Lint.LintCategory.SERIAL) &&
            isSerializable(c) &&
            (c.flags() & Flags.ENUM) == 0 &&
            (c.flags() & ABSTRACT) == 0) {
            checkSerialVersionUID(tree, c);
        }
    }
        // where
        /** check if a class is a subtype of Serializable, if that is available. */
        private boolean isSerializable(ClassSymbol c) {
            try {
                syms.serializableType.complete();
            }
            catch (CompletionFailure e) {
                return false;
            }
            return types.isSubtype(c.type, syms.serializableType);
        }

        /** Check that an appropriate serialVersionUID member is defined. */
        private void checkSerialVersionUID(JCClassDecl tree, ClassSymbol c) {

            // check for presence of serialVersionUID
            Scope.Entry e = c.members().lookup(names.serialVersionUID);
            while (e.scope != null && e.sym.kind != VAR) e = e.next();
            if (e.scope == null) {
                log.warning(tree.pos(), "missing.SVUID", c);
                return;
            }

            // check that it is static final
            VarSymbol svuid = (VarSymbol)e.sym;
            if ((svuid.flags() & (STATIC | FINAL)) !=
                (STATIC | FINAL))
                log.warning(TreeInfo.diagnosticPositionFor(svuid, tree), "improper.SVUID", c);

            // check that it is long
            else if (svuid.type.tag != TypeTags.LONG)
                log.warning(TreeInfo.diagnosticPositionFor(svuid, tree), "long.SVUID", c);

            // check constant
            else if (svuid.getConstValue() == null)
                log.warning(TreeInfo.diagnosticPositionFor(svuid, tree), "constant.SVUID", c);
        }

    private Type capture(Type type) {
        return types.capture(type);
    }
    
    public VarSymbol getVarSymbolFor(JCExpression tree,
            Env<AttrContext> env) {
        Symbol sym = tree.getSymbol();
        VarSymbol varSym = null;
        if (sym instanceof VarSymbol) {
            varSym = (VarSymbol) sym;
        }
        else if (tree instanceof JCArrayAccess) {
            JCArrayAccess aa = (JCArrayAccess) tree;
            Type atype = aa.indexed.type;
            if (types.isArrayClass(atype)) {
                ClassType ct = (ClassType) atype;
                Type site = capture(ct);
                varSym = (VarSymbol)
                        rs.findIdentInType(env, site, names.fromString("cell"), VAR);
            }
        }
        return varSym;
    }
    
    /**
     * Get the RPL associated with an expression
     */
    public RPL getRPLFor(JCExpression tree,
	    Env<AttrContext> env) {
	if (tree instanceof JCArrayAccess) {
	    JCArrayAccess aa = (JCArrayAccess) tree;
            Type atype = aa.indexed.type;
            if (types.isArray(atype)) {
        	ArrayType at = (ArrayType) atype;
        	return at.rpl;
            }
	}
	VarSymbol vsym = getVarSymbolFor(tree, env);
	if (vsym != null) {
	    return vsym.rpl;
	}
	return null;
    }
        
    /** Perform 'as member of' implied by a selection */
    public <T extends AsMemberOf<T>> T asMemberOf(T elt,
            JCExpression tree, Env<AttrContext> env) {
	if (elt == null) return null;
	if (tree instanceof JCFieldAccess) {
            JCFieldAccess fa = (JCFieldAccess) tree;
            return elt.asMemberOf(fa.selected.type, types);
	}
        if (tree instanceof JCArrayAccess) {
            JCArrayAccess aa = (JCArrayAccess) tree;
            return elt.asMemberOf(aa.indexed.type, types);
        }
    	if (tree instanceof JCIdent) {
            return elt.asMemberOf(env.enclClass.sym.type, types);
        }
        return elt;
    }
    

    /** Perform index substitution implied by a selection */
    public <T extends SubstIndex<T>> T substIndex(T elt, 
	    JCExpression tree, Env<AttrContext> env) {
	if (elt == null) return null;
	T result = elt;
	if (tree instanceof JCArrayAccess) {
	    JCArrayAccess aa = (JCArrayAccess) tree;
            Type atype = aa.indexed.type;
            if (types.isArray(atype)) {
                ArrayType at = (ArrayType) atype;
            	result = result.substIndex(at.indexVar, aa.index);
            }    
            else if (types.isArrayClass(atype)) {
                ClassType ct = (ClassType) atype;
                Type site = types.capture(ct);
        	Symbol sym = getVarSymbolFor(tree,env);
        	if (sym instanceof VarSymbol) {
        	    Symbol indexSym = rs.findIdentInType(env,  
        		    site, names.fromString("index"), 
        		    VAR);
        	    if (indexSym instanceof VarSymbol) {
        		result = result.substIndex((VarSymbol) indexSym, aa.index);
        	    }
        	}
            }            
	}
	return result;
    }

    /** Perform RPL-for-var substitution implied by a selection */
    public <T extends SubstRPLForVar<T>> T substOwnerRPL(T elt,
	    JCExpression tree, Env<AttrContext> env) {
	if (elt == null) return null;
	T result = elt;
	if (tree instanceof JCFieldAccess) {
	    JCFieldAccess fa = (JCFieldAccess) tree;
	    RPL rpl = exprToRPL(fa.selected);
	    if (rpl != null) {	
		result = result.substRPLForVar(tree.enclThis(), rpl);
	    }
	}
	if (tree instanceof JCArrayAccess) {
	    JCArrayAccess aa = (JCArrayAccess) tree;
	    JCExpression selectedExp = 
		    rs.selectedExp(aa.indexed, env);                	
	    RPL selectedRPL = exprToRPL(selectedExp);
	    Symbol sym = aa.indexed.getSymbol();
	    if (selectedRPL != null && sym != null) {
		VarSymbol thisSym = sym.enclThis();
		result = result.substRPLForVar(thisSym, selectedRPL);
	    }
	}
	return result;
    }
    
    /** Default effect = writes Root : * */
    public Effects defaultEffects() {
	return new Effects(new Effect.WriteEffect(rpls, 
		    	new RPL(List.<RPLElement>of(RPLElement.ROOT_ELEMENT, 
		    		RPLElement.STAR)), false, true));
    }
        
    // DPJ constructs (to end)
    
    public void visitRegionDecl(DPJRegionDecl tree) { // DPJ -- based on visitVarDef
        // Local variables have not been entered yet, so we need to do it now:
        if (env.info.scope.owner.kind == MTH) {
            if (tree.sym != null) {
                // parameters have already been entered
                env.info.scope.enter(tree.sym);
            } else {
                memberEnter.memberEnter(tree, env);
                annotate.flush();
            }
        }
    }
    
    public void visitUniqueRegionDecl(DPJUniqueRegionDecl tree) {
	if (tree.param.sym != null) {
	    env.info.scope.enter(tree.param.sym);
	}
	else {
	    RegionParameterSymbol sym = new RegionParameterSymbol(
		    STATIC,     // Treat regions as static class members
		    tree.param.name,
		    env.info.scope.owner,
		    tree.param.isAtomic,
		    tree.param.isUnique);
	    if (chk.checkUnique(tree.pos(), sym, env.info.scope)) {
		env.info.scope.enter(sym);
		tree.param.sym = sym;
	    }
	}
    }

    public void visitFinish(DPJFinish tree) {
        attribStat(tree.body, env);
        result = null;
    }

    public void visitCobegin(DPJCobegin tree) {
	attribStat(tree.body, env);
	result = null;
    }
    
    public void visitAtomic(DPJAtomic tree) {
	attribStat(tree.body, env);
	result = null;
    }

    public void visitNonint(DPJNonint tree) {
	attribStat(tree.body, env);
	result = null;
    }
    
    public void visitDPJForLoop(DPJForLoop tree) {
	Env<AttrContext> loopEnv =
	    env.dup(env.tree, env.info.dup(env.info.scope.dup()));
	tree.var.mods.flags |= Flags.FINAL;
	attribStat(tree.var, loopEnv);
	attribExpr(tree.start, loopEnv);
	if (tree.length != null)
	    attribExpr(tree.length, loopEnv);
	if (tree.stride != null)
	    attribExpr(tree.stride, loopEnv);
	loopEnv.tree = tree; // before, we were not in loop!
	attribStat(tree.body, loopEnv);
	loopEnv.info.scope.leave();
	result = null;	
    }
    
    /** Visitor method for spawn.
     */
    public void visitSpawn(DPJRenames tree) {
        attribStat(tree.body, env);
        result = null;
    }
    
    /** Visitor method for region parameter declarations, including disjointness constraints.
     */
    //public void visitParamInfo(DPJParamInfo tree) {
    //}
    
    /** Visitor method for region parameters.
     */
    public void visitRegionParameter(DPJRegionParameter tree) {
	attribTree(tree.bound, env, RPL_ELT, Type.noType);
    }

    /** Visitor method for RPLs in various contexts.
     */
    public void visitRPL(DPJRegionPathList tree) { // based on #attribExprs
	tree.rpl = new RPL();
	boolean firstPos = true;
	ListBuffer<RPLElement> buf = ListBuffer.lb();
	for (DPJRegionPathListElt elt : tree.elts) {
	    attribTree(elt, env, RPL_ELT, Type.noType);
	    if (elt.rplElt != null) {
		if (firstPos) {
		    // In the first position ...
		    if (elt.rplElt != RPLElement.ROOT_ELEMENT &&
			    !(elt.rplElt instanceof RPLParameterElement) &&
			    !(elt.rplElt instanceof VarRPLElement) &&
			    !(elt.rplElt.isLocalName())) {
			// If RPL doesn't start with 'Root', a parameter, a variable,
			// or a local name, then prepend the implicit 'Root :'
			buf.append(RPLElement.ROOT_ELEMENT);
		    }
		    /*
		    if (elt.rplElt.isLocalName()) {
			// If RPL starts with a local region name, then it
			// implicitly starts with 'Root : Local :'
			buf.append(RPLElement.LOCAL_ELEMENT);
		    }
		    */
		} else {
		    // Otherwise ...
		    if (elt.rplElt == RPLElement.ROOT_ELEMENT)
			// 'Root' is not allowed
			log.error(elt.pos(), "root.must.start.rpl");
		    else if (elt.rplElt instanceof RPLParameterElement)
			// Region params are not allowed
			log.error(elt.pos(), "region.param.must.start.rpl");
		    else if (elt.rplElt instanceof VarRPLElement) {
			// Variables are not allowed
			log.error(elt.pos(), "var.region.must.start.rpl");
		    }
		}
		buf.append(elt.rplElt);
	    }
	    firstPos = false;
	}
	tree.rpl.elts = buf.toList();
    }
    
    /** Visitor method for RPL elements.
     */
    public void visitRPLElt(DPJRegionPathListElt elt) {
	switch (elt.type) {
	case DPJRegionPathListElt.NAME: 
            if (elt.exp instanceof JCIdent) {
        	JCIdent id = (JCIdent) elt.exp;
        	if (id.name == names.root) {
        	    elt.rplElt = RPLElement.ROOT_ELEMENT;
        	} else if (id.name == names.local) {
        	    if (env.enclMethod == null) {
        		log.error(elt.pos(), "local.region.not.in.scope");
        	    }
        	    elt.rplElt = RPLElement.LOCAL_ELEMENT;
        	}
        	else {
        	    attribTree(elt.exp, env, RPL_ELT, Type.noType);
        	    Symbol sym = elt.exp.getSymbol();
        	    if (sym instanceof RegionNameSymbol)
        		elt.rplElt = new NameRPLElement((RegionNameSymbol)sym);
        	    else if (sym instanceof RegionParameterSymbol)
        		elt.rplElt = new RPLParameterElement((RegionParameterSymbol)sym);
        	    else if (sym instanceof VarSymbol) {
        		VarSymbol vsym = (VarSymbol) sym;
        		elt.rplElt = new RPLElement.VarRPLElement(vsym);
        		if ((vsym.flags() & Flags.FINAL) == 0 ||
        			(vsym.name != names._this &&
        				vsym.owner.kind != Kinds.MTH))
        		    log.error(elt.pos, "region.must.be.final.local.variable");
        	    }
        	}
            } else if (elt.exp instanceof JCFieldAccess) {
        	JCFieldAccess fieldAccess = (JCFieldAccess) elt.exp;
        	Type selectedType = attribTree(fieldAccess.selected, env, TYP, Type.noType);
        	// from Attr#selectSym
        	Symbol sym = rs.findIdentInType(env, selectedType, fieldAccess.name, RPL_ELT);
        	if ((pkind & ERRONEOUS) == 0)
        	    sym = rs.access(sym, fieldAccess.pos(), selectedType, fieldAccess.name, true);
        	fieldAccess.sym = sym;
        	if (fieldAccess.sym instanceof RegionNameSymbol)
        	    elt.rplElt = new NameRPLElement((RegionNameSymbol) fieldAccess.sym);
        	else if (fieldAccess.sym instanceof VarSymbol)
        	    elt.rplElt = new RPLElement.VarRPLElement((VarSymbol) fieldAccess.sym);
            } else {
        	log.error(elt.pos(), "bad.field.region");
            }
            break;
	case DPJRegionPathListElt.STAR:
	    elt.rplElt = RPLElement.STAR;
	    break;
	case DPJRegionPathListElt.ARRAY_INDEX:
	    attribExpr(elt.exp, env);
	    elt.rplElt = new RPLElement.ArrayIndexRPLElement(elt.exp);
	    break;
	case DPJRegionPathListElt.ARRAY_UNKNOWN:
	    elt.rplElt = new RPLElement.ArrayIndexRPLElement(null);
	    break;
	default:
	    log.error(elt.pos(), "unknown.rpl.element");
	}
    }
    
    public void visitEffect(DPJEffect tree) {
	attribRPLs(tree.readEffects);
	attribRPLs(tree.writeEffects);
	for (JCIdent param : tree.variableEffects) {
	    attribTree(param, env, Kinds.EFFECT, Type.noType);
	}
	tree.effects = new Effects();
	if (tree.isPure) {
	    // Nothing to do
	} else if (tree.readEffects.nonEmpty() ||
		tree.writeEffects.nonEmpty() ||
		tree.variableEffects.nonEmpty()) {
	    for (DPJRegionPathList treeRPL : tree.readEffects) {
		tree.effects.add(new Effect.ReadEffect(rpls, treeRPL.rpl, 
			treeRPL.isAtomic, treeRPL.isNonint));
	    }
	    for (DPJRegionPathList treeRPL : tree.writeEffects) {
		    tree.effects.add(new Effect.WriteEffect(rpls, treeRPL.rpl, 
			treeRPL.isAtomic, treeRPL.isNonint));
	    }
	    for (JCIdent treeEffectParam : tree.variableEffects) {
		if (treeEffectParam.sym instanceof EffectParameterSymbol) {
		    tree.effects.add(new Effect.VariableEffect((EffectParameterSymbol) 
			treeEffectParam.sym));
		}
	    }
	} else {
	    tree.effects = defaultEffects(); 
	}            
    }

}
