package bayou.od;

import bayou.jtype.ClassType;
import bayou.jtype.TypeMath;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Set;
import bayou.od.OD.Predicate;
import bayou.od.OD.Supplier;

/*
    bind type T to impl class B
    T's class is A. A has n type params.
    class B<V1..Vm>
        B(A1..Ak) // the type-arg constructor

    in general, this is a multiple mapping:
    A' -> { new B<v1..vm>(a1..ak) }, where A' and v1..vm satisfy
        1. A'.class = A
        2. B<v1..vm> <: A'
        3. if T is not raw, T<:A'

    by query time inference:
    given a query type Q, require Q.class=T.class; if T not raw, request T<:Q
    solve v1..vm by
        Q :> B<V1..Vm>
    get type-arg constructor parameters a1..ak from v1..vm.
    return ConstructorSupplier { new B<v1..vm>(a1..ak) }

    not all cases need query time inference. if we can decide that it's a single mapping
    (actually can be multiple through wildcards - but we usually only mention the min applicable one)
    we'll optimize it at binding time. for example
        1. if T is not raw, it's a single mapping for T;
            resolve v1..vm by T :> B<V1..Vm>
        2. if B<V1..Vm> extends Ax, and Ax contains no V1..Vm, it's a single mapping for Ax.
            resolve v1..vm by Ax :> B<V1..Vm> (i.e. by Vi's own bound only)

    if the mapping is obviously vacuous, treat it as a programming error

 */
class ImplClassBinding
{
    // about static type matching of method arg `implClass`
    // if `T` has type args, and `B` is generic, bind(T).to(B.class) doesn't compile.
    // that's a good restriction in most cases, e.g. user can't
    //     bind(`List<User>`).to(ArrayList.class)
    // however the use case can be legit, user really meant "ArrayList<User>.class".
    // this can be achieved by casting
    //     bind(`List<User>`).to( (Class(ArrayList<User>) ArrayList.class )

    public static<T> OD.Binding of(ClassType<T> typeT, Predicate<Object[]> tagMatcher, Class<? extends T> implClassB)
    {
        // we could handle all cases with query time inference.
        // but we check for some simpler cases which bind a single type to constructor with fixed args.
        // this is for optimization, and for early error detection.

        // throw error if the binding is obviously vacuous, which is very likely a programming mistake.

        Class<?> classA = typeT.getTheClass();
        if(!classA.isAssignableFrom(implClassB))
            throw new IllegalArgumentException(String.format("%s is not subclass of %s", implClassB, classA));
        TypeArgConstructor tac = TypeArgConstructor.of(implClassB); // throws
        // B<V1..Vm>
        ClassType<?> declB = ClassType.withTypeVars(implClassB);

        if( ! typeT.isRawType() ) // bind to single type T
            return bindSingle(typeT, tagMatcher, implClassB, tac, declB);

        // T is raw A, n>0
        // B<V1..Vm> extends A' (maybe indirectly)
        ClassType<?> Ap = TypeMath.getSuperType(declB, classA);

        if(Ap.isRawType()) // B extends raw A. A' contains no var. bind to single type T=A=A'
            return bindSingle(Ap, tagMatcher, implClassB, tac, declB);

        // A' = A<s1..sn>, si is function of V1..Vm
        if(declB.getTypeVars().isEmpty()) // m=0, A' contains no var; bind to single A'
            return bindSingle(Ap, tagMatcher, implClassB, tac, declB);

        // m>0, n>0, do query time inference
        return new ViaInference(classA, implClassB, tagMatcher, tac);
        // note, it's still possible that A' contains no V1..Vm. we could optimize for that case,
        // and bind to single A', no query time inference. we don't do that since the case should be rare.
    }

    // B<V1..Vm> extends A<s1..sn>
    // given a query type Q, check Q.class=A. infer V1..Vm from Q :> B<V1..Vm>.
    // get a1..ak; return constructor supplier new B(a1..ak)
    static class ViaInference implements OD.Binding
    {
        final Class classA;
        final Class classB;
        final Predicate<Object[]> tagMatcher;
        final TypeArgConstructor tac;

        ViaInference(Class classA, Class classB, Predicate<Object[]> tagMatcher, TypeArgConstructor tac)
        {
            this.classA = classA;
            this.classB = classB;
            this.tagMatcher = tagMatcher;
            this.tac = tac;
        }

        public String toString()
        {
            return String.format("ImplClassBinding(implClass=%s, type=%s<>, tags=%s)",
                classB.getName(), classA.getName(), tagMatcher);
        }

        public <T> Supplier<? extends T> map(ClassType<T> typeQ, Object... tags)
        {
            if(typeQ.getTheClass()!=classA)
                return null;
            if(!tagMatcher.test(tags))
                return null;

            ClassType<?> B_v1_vm;
            try
            {   B_v1_vm = TypeMath.diamondInfer(typeQ, classB); }
            catch (Exception error) // inference fails
            {   return null; }

            Object[] a1_ak = tac.getConstructorArgs(B_v1_vm);
            if(a1_ak==null) // mismatch Ai and vj
                return null;

            Constructor<T> cons = OD.cast(tac.constructor);
            return new ConstructorSupplier<T>(cons, a1_ak);
        }

        public Set<? extends Class> getApplicableClasses()
        {
            return Collections.singleton(classA);
        }
    }

    // no query time inference; map T to new B(a1..ak);
    // infer V1..Vm by T :> B<V1..Vm>; get a1..ak from v1..vm
    static <T> OD.Binding
    bindSingle(ClassType<T> typeT, Predicate<Object[]> tagMatcher, Class implClassB, TypeArgConstructor tac, ClassType<?> declB)
    {
        // m=0, shortcut; no need to infer, just check T:>B. bind to new B()
        if(declB.getTypeVars().isEmpty())
        {
            if(!TypeMath.isSubType(declB, typeT))
                throw new IllegalArgumentException(String.format("%s is not subtype of %s", declB, typeT));

            Constructor<T> cons = OD.cast(tac.constructor);
            return new ConstructorBinding<T>(typeT, tagMatcher, cons, new Object[0]);
        }

        // m>0, infer V1..Vm by T :> B<V1..Vm>
        ClassType<?> B_v1_vm = TypeMath.diamondInfer(typeT, implClassB); // throws. wrap message?
        Object[] a1_ak = tac.getConstructorArgs(B_v1_vm);
        if(a1_ak==null) // mismatch Ai and vj
            throw new IllegalArgumentException(String.format("the type-arg constructor %s doesn't match type %s", tac.constructor, B_v1_vm));
        Constructor<T> cons = OD.cast(tac.constructor);
        return new ConstructorBinding<T>(typeT, tagMatcher, cons, a1_ak);
    }
}
