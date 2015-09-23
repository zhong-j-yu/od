package bayou.od;

import bayou.jtype.*;

import java.util.Collections;
import java.util.Set;
import bayou.od.OD.Predicate;
import bayou.od.OD.Supplier;

import static bayou.od.ConstructorSupplier.newInstance;

/*
    bind type T to supplier class P
    T's class is A. A has n type params.
    class P<V1..Vm> extends Supplier<z<V1..Vm>>  (maybe indirectly)
        P(A1..Ak) throws IllegalExceptionArgument // the type-arg constructor

    in general, this is a multiple mapping:
    A' -> new P<v1..vm>(a1..ak), where A' and v1..vm satisfy
        1. A'.class = A
        2. P<v1..vm> <: Supplier<? extends A'>
        3. if T is not raw, T<:A'

    by query time inference:
    given a query type Q, require Q.class=T.class; if T not raw, require T<:Q
    solve v1..vm by
        Supplier<? extends Q>  :>  P<V1..Vm>
    get type-arg constructor parameters a1..ak from v1..vm.
    p = new P<v1..vm>(a1..ak). it can throw IllegalArgumentException to reject v1..vm.
    if everything works, 'p' is mapped to type Q.

    not all cases need query time inference. if we can decide that it's a single mapping,
    (actually can be multiple through wildcards - but we usually only mention the min applicable one)
    we'll optimize it at binding time. for example
        if T is not raw, it's a single mapping T->p
            resolve v1..vm by Supplier<? extends T> :> P<V1..Vm>
        if m=0, z contains no vars. if we can find minimum A', so that A' :> z, (not always possible to do this)
            it's a single mapping A'->new P()

    if the mapping is obviously vacuous, treat it as a programming error.

 */
class SupplierClassBinding
{
    // about static type matching of method arg `supplierClass`
    // the type of `supplierClass` isn't specific enough, we lose some compile time checking.
    // ideally the type should be Class<? extends Supplier<? extends T>>, however it doesn't work if
    // supplierClass is generic, due to erasure of its super types. e.g. this doesn't compile:
    //     ListSupplier<E> implements Supplier<List<E>>
    //     bind(List.class).toSupplier(ListSupplier.class)
    // we'll check at runtime, which isn't bad - if the call is tested ok during dev, it won't fail on production.

    public static<T> OD.Binding of(ClassType<T> typeT, Predicate<Object[]> tagMatcher, Class<? extends Supplier> supplierClassP)
    {
        // P<V1..Vm>
        ClassType<?> declP = ClassType.withTypeVars(supplierClassP);
        assert Supplier.class.isAssignableFrom(supplierClassP); // ensured by static checking
        // P<V1..Vm> extends Supplier<z>  (maybe indirectly)
        ClassType<?> pz = TypeMath.getSuperType(declP, Supplier.class);
        if(pz.isRawType())
            throw new IllegalArgumentException(String.format("%s extends raw %s", supplierClassP, Supplier.class));
        // z can't be wildcard; could be array, class/interface, type var.
        ReferenceType<?> z = (ReferenceType<?>)pz.getTypeArgs().get(0);
        TypeArgConstructor tac = TypeArgConstructor.of(supplierClassP); // throws

        if( ! typeT.isRawType() )
            return bindSingle(typeT, tagMatcher, supplierClassP, declP, z, tac);

        // T is raw A. A is generic.
        Class<?> classA = typeT.getTheClass();

        // if m=0, z contains no var. unfortunately we can't in all cases determine min A' so that A':>z

        if(z instanceof ClassType) // common case
        {
            ClassType<?> cz = (ClassType<?>)z;
            Class<?> classZ = cz.getTheClass();
            if(!classA.isAssignableFrom(classZ))
                throw new IllegalArgumentException(String.format("%s cannot provide %s", supplierClassP, classA.getName()));
            if(!cz.hasWildcard()) // common case
            {
                // z extends A'
                ClassType<?> Ap = TypeMath.getSuperType(cz, classA); // can't do this if z has wildcard
                if(Ap.isRawType()) // z is of raw A, bind single T=A=A'
                    return bindSingle(Ap, tagMatcher, supplierClassP, declP, z, tac);
                if(declP.getTypeVars().isEmpty()) // m=0, A' contains no var, bind to single A'
                    return bindSingle(Ap, tagMatcher, supplierClassP, declP, z, tac);
            }
            else // z has wildcard; in general we cannot get A', except...
            {
                if(classZ==classA) // z=A'
                {
                    ClassType<?> Ap = cz; // A' contains wildcard
                    if(declP.getTypeVars().isEmpty()) // m=0, A' contains no var, bind to single A'
                        return bindSingle(Ap, tagMatcher, supplierClassP, declP, z, tac);
                }
                /*
                // we could do the following branch; but it's unnecessary handling of rare case.
                else // Z is a subclass of A. we want to find out if it extends raw A. any Z<..> would do.
                {
                    ClassType<?> z2 = ClassType.of(classZ, true);
                    ClassType<?> Ap = TypeMath.getSuperType(z2, classA); // not super type of z
                    if(Ap.isRaw()) // z is of raw A, bind single T=A=A'
                        return bindSingle(Ap, tags, supplierClassP, declP, z, tac);
                }
                */
            }
        }

        // for any other cases, do query time inference
        return new ViaInference(classA, supplierClassP, tagMatcher, tac);
    }

    // P<V1..Vm> extends Supplier<z>
    // given a query type Q, check Q.class=A.
    // infer V1..Vm from Supplier<? extends Q> :> P<V1..Vm>
    // get a1..ak; return supplier new P(a1..ak)
    static class ViaInference implements OD.Binding
    {
        final Class classA;
        final Class classP;
        final Predicate<Object[]> tagMatcher;
        final TypeArgConstructor tac;

        ViaInference(Class classA, Class classP, Predicate<Object[]> tagMatcher, TypeArgConstructor tac)
        {
            this.classA = classA;
            this.classP = classP;
            this.tagMatcher = tagMatcher;
            this.tac = tac;
        }

        public String toString()
        {
            return String.format("SupplierClassBinding(supplierClass=%s, type=%s<>, tags=%s)",
                classP.getName(), classA.getName(), tagMatcher);
        }

        public <T> Supplier<? extends T> map(ClassType<T> typeQ, Object... tags)
        {
            if(typeQ.getTheClass()!=classA)
                return null;
            if(!tagMatcher.test(tags))
                return null;

            ClassType<?> P_v1_vm;
            try
            {   P_v1_vm = TypeMath.diamondInfer(_Pwe(typeQ), classP); }
            catch (Exception error) // inference fails
            {   return null; }

            Object[] a1_ak = tac.getConstructorArgs(P_v1_vm);
            if(a1_ak==null) // mismatch Ai and vj
                return null;

            Object supplier;
            try
            {
                supplier = newInstance(tac.constructor, a1_ak);
            }
            catch (IllegalArgumentException e)
            {
                // not an error; protocol to reject the inferred v1..vm
                return null;
            }
            // other throwable: unexpected; don't be silent, pop up.

            return OD.cast( supplier );
        }

        public Set<? extends Class> getApplicableClasses()
        {
            return Collections.singleton(classA);
        }
    }

    // no query time inference; map T to new P(a1..ak);
    // infer V1..Vm by Supplier<? extends T> :> P<V1..Vm> (that means z<:T); get a1..ak from v1..vm
    private static <T> OD.Binding
    bindSingle(ClassType<T> typeT, Predicate<Object[]> tagMatcher, Class<? extends Supplier> supplierClassP,
               ClassType<?> declP, ReferenceType<?> z, TypeArgConstructor tac)
    {
        // m=0, shortcut; no need to infer, just check z<:T. bind to new P()
        if(declP.getTypeVars().isEmpty())
        {
            if(!TypeMath.isSubType(z, typeT))
                throw new IllegalArgumentException(String.format("%s cannot provide %s", supplierClassP, typeT));

            Supplier<? extends T> supplier = OD.cast(newInstance(tac.constructor)); // new P (). IllegalArgEx?
            return new SupplierBinding<T>(typeT, tagMatcher, supplier);
        }

        // m>0, infer V1..Vm, so that Supplier<? extends T> :> P<V1..Vm>
        ClassType<?> P_v1_vm = TypeMath.diamondInfer(_Pwe(typeT), supplierClassP); // throws. wrap message?
        Object[] a1_ak = tac.getConstructorArgs(P_v1_vm);
        if(a1_ak==null) // mismatch Ai and vj
            throw new IllegalArgumentException(String.format("the type-arg constructor %s doesn't match type %s",tac.constructor,P_v1_vm));
        Supplier<? extends T> supplier = OD.cast(newInstance(tac.constructor, a1_ak)); // throws IllegalArgEx
        return new SupplierBinding<T>(typeT, tagMatcher, supplier);

        // note: if supplier constructor throws IllegalArgumentException to reject inferred v1..vm
        //       it propagates up to caller, who probably made a programming error.
    }


    // return Supplier<? extends X>
    static <T> ClassType<?> _Pwe(ClassType<T> typeX)
    {
        return ClassType.<Supplier,Supplier>of    // intellij inference failure
            (Supplier.class, Wildcard.extends_(typeX));
    }
}
