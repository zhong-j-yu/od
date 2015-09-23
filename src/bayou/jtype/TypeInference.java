package bayou.jtype;

import java.util.*;

class TypeInference
{
    // vars and their constraints. typically there are only a few, so we don't need a hash map.
    VarConstraint[] varConstraints;

    // we have constraints (C1 and C2 and ...)
    // reduce them, Ci becomes (Ci1 and Ci2 and ...), eventually all are reduced to var constraints.
    // if Cj becomes (Cj1 or Cj2 or ...), abandon this inference, create orBranches=(K1 or K2 or ...)
    // where Kx = (C1 and .. Cjx .. ). solve K1,K2..., pick the first one with a solution.
    LinkedList<Constraint> constraints = new LinkedList<Constraint>();
    ArrayList<TypeInference> orBranches = null;
    String branchName;

    // (T,F): definitely one unique, precise solution
    // (T,T): found a solution, but it may not be unique
    // (F,T): we can't find a solution, but don't know if there can be one.
    // (F,F): definitely no solution
    boolean oneSolutionIsFound = false, mayHaveOtherSolutions = false;

    List<ReferenceType<?>> solutions;
    String errorMsg=null;

    Counter counter; // we may have dead loops; abort if taking too long.

    TypeInference(List<TypeVar<?>>vars, boolean addBoundsAsConstraints)
    {
        counter = new Counter();
        branchName = "";

        varConstraints = new VarConstraint[vars.size()];
        for(int i=0; i<vars.size(); i++)
            varConstraints[i] = new VarConstraint(vars.get(i));

        if(addBoundsAsConstraints)
            for(TypeVar<?> var : vars)
            {
                addConstraint(null, var, var.getUpperBound(), -1);
                addConstraint(null, var, var.getLowerBound(), +1);
            }
    }

    private TypeInference() // for or branch
    {
    }

    VarConstraint varConstraint(JavaType<?> type)
    {
        if(!(type instanceof TypeVar))
            return null;
        TypeVar<?> var = (TypeVar<?>)type;
        for(VarConstraint vc : varConstraints)
        {
            TypeVar<?> v = vc.var;
            if( v==var || v.hashCode()==var.hashCode() && v.equals(var) )
                return vc;
        }
        return null;
    }

    static boolean testTrace = false;

    void addConstraint(Constraint parent, ReferenceType<?> lhs, ReferenceType<?> rhs, int op)
    {
        Constraint constraint =  new Constraint(parent, lhs, rhs, op);
        constraints.add(constraint);

        if(testTrace)
            System.out.println(branchName+" +constraint: "+constraint.toString(false));
    }

    void addOrBranch(Constraint parent, ReferenceType<?> lhs, ReferenceType<?> rhs, int op)
    {
        TypeInference branch = new TypeInference();
        if(orBranches==null)
            orBranches = new ArrayList<TypeInference>();
        orBranches.add(branch);

        branch.branchName=branchName+"."+orBranches.size();
        branch.counter = counter;
        // branch.testTrace = testTrace;

        int N = varConstraints.length;
        branch.varConstraints = new VarConstraint[N];
        for(int i=0; i<N; i++)
            branch.varConstraints[i] = varConstraints[i].klone();
        branch.constraints.addAll(constraints);

        branch.addConstraint(parent, lhs, rhs, op);
    }

    static class Constraint
    {
        final Constraint parent;
        final ReferenceType<?> lhs;
        final ReferenceType<?> rhs;
        final int op; // <: -1, = 0, :> +1;
        Constraint(Constraint parent, ReferenceType<?> lhs, ReferenceType<?> rhs, int op)
        {
            this.parent = parent;
            this.lhs = lhs;
            this.rhs = rhs;
            this.op = op;
        }
        Constraint reverse()
        {
            return new Constraint(parent, rhs, lhs, -op);
        }
        public String toString()
        {
            return toString(true);
        }
        public String toString(boolean full)
        {
            String opc = (op<0)? "<:" : (op==0)? "=" : ":>";
            String s = lhs.ss() +' '+opc+' '+ rhs.ss();
            if(full && parent!=null)
                s = s + "\n    from    " + parent.toString(full);
            return s;
        }
    }

    // constraints about a var, where lhs is a the var.
    // rhs is not null, not intersection
    // rhs can contain var - vars can depend on each other
    static class VarConstraint
    {
        TypeVar<?> var;
        VarConstraint(TypeVar<?> var){ this.var = var; }

        ArrayList<Constraint> uppers = new ArrayList<Constraint>();
        ArrayList<Constraint> equals = new ArrayList<Constraint>();
        ArrayList<Constraint> lowers = new ArrayList<Constraint>();

        // (v op x), x cannot be intersection type
        void add(Constraint c) throws ConstraintException
        {
            assert c.lhs.equals(this.var);

            assert !(c.rhs instanceof IntersectionType);

            if(c.rhs instanceof NullType) // won't be added
            {
                if(c.op>0) // v > null, ignore
                    return;
                else       // v <= null; we do not accept null as solution
                    throw new ConstraintException(c);
            }
            // our algo won't lead to a var=Null solution

            if     (c.op<0)
                uppers.add(c);
            else if(c.op==0)
                equals.add(c);
            else //(c.op>0)
                lowers.add(c);
        }

        // solved-ness of the var:
        // 1-solved : `equals` contains a var-less type `S`
        // 2-solved : `S` is assigned to `solution`
        // 3-solved : constraints `var op X` are substituted as new constraints `S op X`,
        //            and scheduled to be checked. done=true.

        ReferenceType<?> solution = null;
        boolean done = false;

        VarConstraint klone()
        {
            VarConstraint klone = new VarConstraint(this.var);
            klone.uppers.addAll(this.uppers);
            klone.equals.addAll(this.equals);
            klone.lowers.addAll(this.lowers);
            klone.solution = this.solution;
            klone.done = this.done;
            return klone;
        }
    }

    static class ConstraintException extends Exception
    {
        Constraint constraint;
        ConstraintException(Constraint constraint)
        {
            this.constraint = constraint;
        }
    }
    static class Counter
    {
        int count=0;
        void inc()
        {
            if( count++ > 1000*10)
                throw new RuntimeException("inference is taking too long, abort");
        }
    }

    public void solve()
    {
        long t0 = System.nanoTime();
        try
        {
            solve0();
        }
        catch(ConstraintException e)
        {
            errorMsg = "unsatisfiable: "+e.constraint.toString(true);
            if(testTrace)
                System.out.println(branchName+" ERR: "+errorMsg);
            return;
        }
        catch(Throwable t)
        {
            t.printStackTrace(); //todo log
            mayHaveOtherSolutions = true;
            errorMsg = t.toString();
            return;
        }

        if(orBranches!=null)
        {
            solveOrBranches();
            return;
        }

        ArrayList<String> unsolved = new ArrayList<String>();
        solutions = new ArrayList<ReferenceType<?>>(varConstraints.length);
        for (VarConstraint vc : varConstraints)
        {
            solutions.add(vc.solution);
            if (vc.solution == null)
                unsolved.add(vc.var.ss());
        }

        if(unsolved.size()==0)
            oneSolutionIsFound = true;
        else
            errorMsg = "unsolved vars: "+unsolved.toString();

        // the mayHaveOtherSolutions flag could have been set in doBoundedChoices()
    }

    void solveOrBranches()
    {
        for(TypeInference branch : orBranches)
        {
            branch.solve();
            if(branch.oneSolutionIsFound)
            {
                if(! this.oneSolutionIsFound )
                {
                    this.oneSolutionIsFound = true;
                    this.solutions = branch.solutions;
                }
                else // a previous branch already found a solution
                {
                    this.mayHaveOtherSolutions = true;
                }
            }
            if(branch.mayHaveOtherSolutions)
                this.mayHaveOtherSolutions = true;

            if(testTrace)
                continue; // go thru all branches

            if(this.oneSolutionIsFound && this.mayHaveOtherSolutions)
                break; // all we need to know; no need to do remaining branches
        }

        if(!this.oneSolutionIsFound)
            errorMsg = "no solution is found"; // this message is not very useful
    }

    void solve0() throws ConstraintException
    {
        boolean didBoundedChoices = false;

        while(true)
        {
            // reduce constraints
            while(constraints.size()>0)
            {
                counter.inc();
                Constraint constraint = constraints.removeFirst();
                reduceConstraint(constraint); // may add "and constraints" or new "or branches"
                if(orBranches!=null)
                {
                    constraints.addFirst(constraint); // add back, for diagnosis
                    return; // abandon this inference; solve branches instead.
                }
            }

            // may have 1-solved

            while(checkSolved()>0) // 1-solved becomes 2-solved. return # of 2-solved
            {
                doSubstitutions(); // 2-solved becomes 3-solved.
                // may lead to more 1-solved, do checkSolved() again
                // may have new constraints
            }

            // no more 1-solved

            if(constraints.size()>0)
                continue;   // reduce new constraints.

            // no more constraints. no new solutions
            // do bounded choice

            if(doBoundedChoices()>0) // have new 2-solved.
            {
                didBoundedChoices = true;
                continue; // should goto checkSolved(); ok to goto START. constraints is empty anyway.
            }

            // if we are here, we can't make any more progress.
            break;
        }

        if(didBoundedChoices)
            mayHaveOtherSolutions = true;
    }

    int checkSolved()
    {
        int solved = 0; // number of 2-solved
        for(VarConstraint vc : varConstraints)
        {
            if(vc.done)
                continue;

            if(vc.solution==null)
            {
                // find a var-less equality constraint
                for(int i=0; i<vc.equals.size(); i++)
                {
                    ReferenceType<?> typeU = vc.equals.get(i).rhs;
                    if(!containsVar(typeU)) // 1-solved
                    {
                        vc.solution = typeU; // 2-solved
                        vc.equals.remove(i); // remove from an array list. ok. list is likely to be small.
                        if(testTrace)
                            System.out.printf("%s solved %s == %s %n", branchName, vc.var.ss(), typeU.ss());

                        break;
                    }
                }
            }

            if(vc.solution!=null) // 2-solved
                solved++;
        }
        return solved;
    }

    // substitute all 2/3-solved variables
    void doSubstitutions()
    {
        HashMap<TypeVar, ReferenceType<?>> substitutions = new HashMap<TypeVar, ReferenceType<?>>();
        for(VarConstraint varConstraint : varConstraints)
        {
            if(varConstraint.solution!=null) // 2/3-solved
                substitutions.put(varConstraint.var, varConstraint.solution);
        }

        for(VarConstraint vc : varConstraints)
        {
            if(vc.done) // 3-solved. no more constraints about this var.
                continue;

            // for any (Ti op V), if V contains solved variables,
            // do substitution, V->V'. the constraint becomes (Ti op V')
            doSubstitutions(vc.uppers, substitutions);
            doSubstitutions(vc.equals, substitutions);
            doSubstitutions(vc.lowers, substitutions);
            // if op is = and V' is variable-less, this may lead to new solutions.
            // so after this method call, checkSolved() should be called again

            if(vc.solution!=null) // 2-solved
            {
                // Ti is solved to be Ui
                // every var constraint (Ti op V) became new constraint (Ui op V)
                // if V is variable-less, this is to verify the constraint
                // if V contains Tj, the new constraint will eventually be reduced to be about Tj
                constraints.addAll(vc.uppers);
                constraints.addAll(vc.equals);
                constraints.addAll(vc.lowers);
                // after this method call, check for new constraints

                vc.done=true; // 3-solved
                // no longer needed.
                vc.uppers = null;
                vc.equals = null;
                vc.lowers = null;
            }
        }
    }

    static void doSubstitutions(ArrayList<Constraint> vcs, Map<TypeVar, ReferenceType<?>> substitutions)
    {
        for(int i=0; i<vcs.size(); i++)
        {
            Constraint ci = vcs.get(i);
            ReferenceType<?> lhs = TypeMath.doSubstitutions( ci.lhs, substitutions );
            ReferenceType<?> rhs = TypeMath.doSubstitutions( ci.rhs, substitutions );
            vcs.set(i, new Constraint(ci.parent, lhs, rhs, ci.op));
        }
    }

    boolean containsVar(JavaType<?> type)
    {
        if( varConstraint(type)!=null )
            return true ; // type *is* a var

        // intersection
        if(type instanceof IntersectionType)
        {
            for(ReferenceType<?> t2 : ((IntersectionType<?>)type).superTypes)
                if(containsVar(t2))
                    return true;
            return false;
        }

        if(type instanceof ArrayType)
            return containsVar( ((ArrayType<?>)type).componentType );

        if(type instanceof ClassType)
        {
            for(TypeArg arg : ((ClassType<?>)type).args)
                if(containsVar2(arg))
                    return true;
            return false;
        }

        // could be a TypeVar that is not for solving.
        return false;
    }
    boolean containsVar2(TypeArg arg)
    {
        if(arg instanceof ReferenceType)
            return containsVar((ReferenceType<?>)arg);
        Wildcard wc = (Wildcard)arg;
        return containsVar(wc.upperBound) || containsVar(wc.lowerBound);
    }

    // our algo is not very smart. hopefully, it works for most cases.
    // when a programmer expects an inference to succeed, he actually has to reason about it too,
    // so it's unlikely that the case is more complicated than what we can handle here.
    // if a reasonable use case is presented to us which defeats our algo, we then see where to improve.
    int doBoundedChoices() throws ConstraintException
    {
        int num = 0;
        for(VarConstraint vc : varConstraints)
            if(doBoundedChoice(vc))
                num++;
        return num;
    }
    boolean doBoundedChoice(VarConstraint vc) throws ConstraintException
    {
        if(vc.done)
            return false;

        if(vc.lowers.isEmpty() && vc.uppers.isEmpty())
            return false; // no bounds for us to choose within
        // actually if we have var bounds as init constraints
        // each var has at least one bound, it's Object if not explicitly set.

        // can't do if bounds have variables.
        // vars can appear in each other 's bounds. if a var doesn't depend on other vars, it's chosen first.
        // if there are circular dependencies in vars (or even a var on itself), we are screwed.
        for(Constraint upper : vc.uppers)
            if(containsVar(upper.rhs))
                return false;
        for(Constraint lower : vc.lowers)
            if(containsVar(lower.rhs))
                return false;

        // now, we have lower and/or upper bounds, all variable-less.
        // we want to choose one of them as the solution, if it's within all other bounds.

        // definitely no solution if not lower<:upper
        for(Constraint lower : vc.lowers)
            for(Constraint upper : vc.uppers)
                if(!TypeMath.isSubType( lower.rhs, upper.rhs ))
                    throw new ConstraintException(
                        new Constraint(lower, lower.rhs, upper.rhs, -1)
                    );

        // choose from lower bounds first, then choose from upper bounds.
        ReferenceType<?> chosen = null;
        if(chosen==null)
            for(Constraint lower : vc.lowers)
                if(withinBounds(lower.rhs, vc))
                {
                    chosen = lower.rhs;
                    break;
                }
        if(chosen==null)
            for(Constraint upper : vc.uppers)
                if(withinBounds(upper.rhs, vc))
                {
                    chosen = upper.rhs;
                    break;
                }
        if(chosen==null)
            return false;

        if(testTrace)
            System.out.printf("%s choose %s == %s %n", branchName, vc.var.ss(), chosen.ss());

        // now we have a choice within bounds.
        // 2-solved. goto checkSolved()
        vc.solution = chosen;

        // uppers lowers are satisfied
        vc.uppers.clear();
        vc.lowers.clear();
        // equals are yet to be checked

        return true;
    }

    boolean withinBounds(ReferenceType type, VarConstraint vc)
    {
        for(Constraint upper : vc.uppers)
            if( ! TypeMath.isSubType(type, upper.rhs) )
                return false;

        for(Constraint lower : vc.lowers)
            if( ! TypeMath.isSubType(lower.rhs, type) )
                return false;

        return true;
    }

    // ============================================================================
    // now comes the hard part - reduce constraints

    // may add new constraints
    void reduceConstraint(Constraint constraint) throws ConstraintException
    {
        ReferenceType<?> lhs = constraint.lhs;
        ReferenceType<?> rhs = constraint.rhs;
        int op = constraint.op;

        // check if either side is a var
        // and the other side is not an intersection (which requires further reducing)
        VarConstraint lVC = varConstraint(lhs);
        if(lVC!=null && !(rhs instanceof IntersectionType))
        {
            // rhs can contain vars. vars can have each other in their bounds.
            lVC.add(constraint);
            return;
        }
        VarConstraint rVC = varConstraint(rhs);
        if(rVC!=null && !(lhs instanceof IntersectionType))
        {
            constraint = constraint.reverse();
            // rhs can contain vars. vars can have each other in their bounds.
            rVC.add(constraint);
            return;
        }

        // note either side can still be a TypeVar(not for solving)
        // either created from wildcard capture during this inference,
        // or even an outside var prior to this inference(as a constant type)

        if     (op<0)
            reduceLT(lhs, rhs, constraint);
        else if(op>0)
            reduceLT(rhs, lhs, constraint); // reverse sides
        else //(op==0)
            reduceEQ(lhs, rhs, constraint);
    }

    // in general, we must do lhs<rhs && rhs<lhs
    // there may be some shortcuts we can quickly check
    void reduceEQ(ReferenceType<?> lhs, ReferenceType<?> rhs, Constraint constraint) throws ConstraintException
    {
        if(lhs instanceof ClassType && rhs instanceof ClassType)
            if( reduceEQ((ClassType<?>)lhs, (ClassType<?>)rhs, constraint) ) // can throw
                return; // handled
        // don't care too much about arrays

        // lhs<rhs && rhs<lhs
        // we cannot simply do {reduceLT(lhs, rhs); reduceLT(rhs, lhs)} due to possible branching
        addConstraint(constraint, lhs, rhs, -1);
        addConstraint(constraint, lhs, rhs, +1);
    }

    boolean reduceEQ(ClassType<?> lhs, ClassType<?> rhs, Constraint constraint) throws ConstraintException
    {
        // some quick fail check
        if(lhs.clazz!=rhs.clazz)
            throw new ConstraintException(constraint);
        if(lhs.args.size()!=rhs.args.size())
            throw new ConstraintException(constraint);

        // if either has wildcard, no shortcut, must go through both lhs<rhs && rhs<lhs
        if(lhs.hasWildcard() || rhs.hasWildcard())
            return false;

        // neither has wildcard, simply lhs.arg[i]=rhs.arg[i]. no need for reverse relation.
        for(int i=0; i<lhs.args.size(); i++)
        {

            ReferenceType<?> argL = (ReferenceType<?>) lhs.args.get(i);
            ReferenceType<?> argR = (ReferenceType<?>) rhs.args.get(i);
            addConstraint(constraint, argL, argR, 0);
        }
        return true;
    }

    // closely mirroring isSubType()
    void reduceLT(ReferenceType<?> typeA, ReferenceType<?> typeB, Constraint constraint) throws ConstraintException
    {
        //[0]
        if(typeA instanceof ClassType && typeB instanceof ClassType)
        {
            reduceLT2((ClassType<?>) typeA, (ClassType<?>) typeB, constraint);
            return;
        }

        //[1]
        if(typeA instanceof NullType)
            return;

        if(typeB instanceof IntersectionType)
        {
            //[2] A<B1 and..and A<Bm. (m=0 => true; B~Object)
            for(ReferenceType<?> tb : ((IntersectionType<?>)typeB).getSuperTypes())
                addConstraint(constraint, typeA, tb, -1);
            return;
        }
        TypeVar<?> varB = (typeB instanceof TypeVar) ? (TypeVar<?>)typeB : null;
        if(typeA instanceof IntersectionType)
        {
            IntersectionType<?> intersectA = (IntersectionType<?>)typeA;
            if(intersectA.getSuperTypes().size()==0) // degenerate, not necessarily false. A~Object
            {
                addConstraint(constraint, ClassType.OBJECT, typeB, -1);
                return;
            }

            //[3] A1<B or..or An<B. (n>=1)
            for(ReferenceType<?> ta : ((IntersectionType<?>)typeA).getSuperTypes())
                addOrBranch(constraint, ta, typeB, -1);

            // or [6]
            if(varB!=null)
                addOrBranch(constraint, typeA, varB.getLowerBound(), -1);
            return;
        }
        if(typeA instanceof TypeVar)
        {
            TypeVar<?> varA = (TypeVar<?>)typeA;
            if(varB==null) //[5]
            {
                addConstraint(constraint, varA.getUpperBound(), typeB, -1);
                return;
            }
            else // typeB instanceof TypeVar
            {
                // [4]
                if(varA.equals(varB))
                    return;
                // A=B: no (or[5] or[6]) - they must fail anyway, a var can't be a proper super type of itself

                // or [5]
                addOrBranch(constraint, varA.getUpperBound(), typeB, -1);
                // or [6]
                addOrBranch(constraint, typeA, varB.getLowerBound(), -1);
                return;
            }
        }
        // [6]
        if(varB!=null) // typeB instanceof TypeVar
        {
            addConstraint(constraint, typeA, varB.getLowerBound(), -1);
            return;
        }
        if(typeA instanceof ArrayType)
        {
            if(typeB instanceof ArrayType) //[7]
            {
                reduceLT2((ArrayType<?>)typeA, (ArrayType<?>)typeB, constraint);
                return;
            }
            if(typeB instanceof ClassType) //[8]
            {
                if(TypeMath.isSuperClassOfArrays(((ClassType<?>) typeB).clazz))
                    return;
                else
                    throw new ConstraintException(constraint);
            }
            // [-]
        }

        // [-]
        throw new ConstraintException(constraint);
    }

    @SuppressWarnings("unchecked")
    void reduceLT2(ClassType<?> typeA, ClassType<?> typeB, Constraint constraint) throws ConstraintException
    {
        if(!typeB.clazz.isAssignableFrom(typeA.clazz))
            throw new ConstraintException(constraint);

        if( typeB.args.size()==0 ) // B is raw or non-generic
            return;                // trivially true

        // B is generic and non-raw
        if( typeA.isRawType() ) // raw types' super types are raw too
            throw new ConstraintException(constraint);
        // note: that's harsh. e.g. class A<T> extends B<Int>, A(raw) is not subtype of B<Int>

        // neither raw.
        // capture conversion is required, even if A and B are of the same class.
        ClassType<?> typeA2 = TypeMath.doCaptureConversion(typeA);
        ClassType<?> typeA3 = TypeMath.getSuperType(typeA2, typeB.clazz);  // no wildcard arg
        for(int i=0; i<typeA3.args.size(); i++)
        {
            ReferenceType<?> argA = (ReferenceType<?>)typeA3.args.get(i);
            TypeArg argB = typeB.args.get(i);
            if(argB instanceof ReferenceType)
            {
                addConstraint(constraint, argA, ((ReferenceType<?>)argB), 0);
            }
            else // argB is wildcard
            {
                Wildcard wildB = (Wildcard)argB;
                addConstraint(constraint, wildB.lowerBound, argA, -1);
                addConstraint(constraint, argA, wildB.upperBound, -1);
            }
        }
    }
    void reduceLT2(ArrayType<?> typeA, ArrayType<?> typeB, Constraint constraint) throws ConstraintException
    {
        JavaType<?> compoA = typeA.componentType;
        JavaType<?> compoB = typeB.componentType;
        if(compoA instanceof ReferenceType && compoB instanceof ReferenceType)
        {
            addConstraint(constraint, (ReferenceType<?>)compoA, (ReferenceType<?>)compoB, -1);
            return;
        }
        // one of them is primitive
        if(compoA.equals(compoB)) // both are same primitive
            return;

        throw new ConstraintException(constraint);
    }

    // solve Y = new G<>(), return X=G<t1..tn>, X<:Y
    // throw Error if inference fails
    public static ClassType<?> diamondInfer(ClassType<?> targetType, Class<?> genericClass) throws RuntimeException
    {
        // X0 = G<T1..Tn>
        ClassType<?> X0 = ClassType.withTypeVars(genericClass);
        TypeInference infer = new TypeInference(X0.getTypeVars(), true);
        infer.addConstraint(null, X0, targetType, -1);
        infer.solve();
        if(!infer.oneSolutionIsFound)
            throw new RuntimeException("inference failed: "+infer.errorMsg);
        List<TypeArg> args = TypeMath.cast(infer.solutions);
        return new ClassType.Impl<Object>(false, genericClass, args);
    }
}
