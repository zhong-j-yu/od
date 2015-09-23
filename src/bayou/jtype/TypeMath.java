package bayou.jtype;

import _bayou._tmp._Array2ReadOnlyList;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Containing some algorithms of Java types.
 */

// ref: Daniel Smith 2007, Completing the Java Type System
@SuppressWarnings("unchecked")
public class TypeMath
{
    private TypeMath(){}

    // ============================================================================================== convert jlr Type

    // wildcard is not really a java type; don't pass a java.lang.reflect.WildcardType to this method.
    static <T> JavaType<T> convertType(Type jlrType)
    {
        if(jlrType instanceof Class) // cast it to Class<T>
            return convertType( TypeMath.< Class<T> >cast(jlrType) );

        if(jlrType instanceof ParameterizedType)
            return convertType((ParameterizedType) jlrType);

        if(jlrType instanceof GenericArrayType)
            return cast( convertType((GenericArrayType) jlrType) );

        if(jlrType instanceof TypeVariable)
            return convertType((TypeVariable) jlrType);

        throw new AssertionError("unknown type "+jlrType.getClass()+" "+jlrType);
    }

    static <T> JavaType<T> convertType(Class<T> clazz)
    {
        if(clazz.isPrimitive())
            return PrimitiveType.of(clazz);
        if(clazz.isArray())
            return convertArrayType(clazz);
        // really a class/interface
        return new ClassType.Impl<T>(false, clazz);
    }
    static <T> ArrayType<T> convertType(GenericArrayType arrayType)
    {
        JavaType<?> compoType = convertType(arrayType.getGenericComponentType());
        return new ArrayType<T>(compoType);
    }
    static <T> ClassType<T> convertType(ParameterizedType paramType)
    {
        ArrayList<Type> jlrArgs = collectAllArgs(paramType, new ArrayList<Type>());

        ArrayList<TypeArg> typeArgs = new ArrayList<TypeArg>(jlrArgs.size());
        for (Type jlrArg : jlrArgs)
            typeArgs.add(convertTypeArg(jlrArg));

        boolean validate=false; // we can trust jlrType
        return new ClassType.Impl<T>(validate, (Class)paramType.getRawType(), typeArgs);
    }
    static <T> TypeVar<T> convertType(TypeVariable jlrVar)
    {
        return new TypeVar_Declared<T>(jlrVar);
    }

    static TypeArg convertTypeArg(Type jlrType)
    {
        if(jlrType instanceof WildcardType)
            return convertWildcard((WildcardType) jlrType);
        return convertReferenceType(jlrType);
    }

    static Wildcard convertWildcard(WildcardType jlrWildcard)
    {
        ReferenceType upper=null, lower=null;
        // java reflect API:
        // there is always exactly one upper bound. it's Object if there is no explicit upper bound.
        // there is zero or one lower bound. if there is a lower bound, upper bound is Object.
        Type[] low = jlrWildcard.getLowerBounds();
        if(low.length>0)
            lower = convertReferenceType( low[0] );
        else
            upper = convertReferenceType( jlrWildcard.getUpperBounds()[0] );

        return new Wildcard( upper, lower );
    }

    static <T> ReferenceType<T> convertReferenceType(Type jlrType)
    {
        JavaType<T> type = convertType(jlrType);
        assert type instanceof ReferenceType;
        return cast(type);
    }
    static <T> JavaType<T> convertArrayType(Class<?> arrayClass)
    {
        @SuppressWarnings("unchecked")
        Class<?> compoClass = arrayClass.getComponentType();
        if(compoClass==null)
            throw new IllegalArgumentException("arrayClass is not an array class: "+arrayClass);
        JavaType<?> compoType = convertType( compoClass );
        ArrayType<?> arrayType = new ArrayType<Object>( compoType );
        return cast(arrayType);
    }
    static JavaType[] convertTypes(Type[] jlrTypes)
    {
        JavaType[] result = new JavaType[jlrTypes.length];
        for(int i=0; i<jlrTypes.length; i++)
            result[i] = convertType(jlrTypes[i]);
        return result;
    }

    // ================================================================================================== substitution

    // e.g. O<T>.I<S>, get( O<x>.I<y> ) == { T=x, S=y }
    static Map<TypeVar, TypeArg> getTypeArgs(ClassType<?> type)
    {
        HashMap<TypeVar, TypeArg> map = new HashMap<TypeVar, TypeArg>();

        if(type.args.size()==0) // non-generic or raw
            return map;

        List<TypeVar<?>> vars = type.getTypeVars();
        assert vars.size()==type.args.size();
        for(int i=0; i<vars.size(); i++)
            map.put( vars.get(i), type.args.get(i) );
        return map;
    }

    static TypeArg doSubstitutions(TypeArg arg, Map<TypeVar, ReferenceType<?>> substitution)
    {
        if(arg instanceof Wildcard)
            return doSubstitutions( (Wildcard)arg, substitution );
        // reference type
        return doSubstitutions( (ReferenceType<?>)arg, substitution );
    }
    static Wildcard doSubstitutions(Wildcard wildcard, Map<TypeVar, ReferenceType<?>> substitution)
    {
        ReferenceType<?> upper1 = wildcard.getUpperBound();
        ReferenceType<?> upper2 = doSubstitutions(upper1, substitution);

        ReferenceType<?> lower1 = wildcard.getLowerBound();
        ReferenceType<?> lower2 = doSubstitutions(lower1, substitution);

        if(upper2==upper1 && lower2==lower1)
            return wildcard; // unchanged

        return new Wildcard(upper2, lower2);
    }
    static ReferenceType<?> doSubstitutions(ReferenceType<?> type, Map<TypeVar,ReferenceType<?>> substitution)
    {
        if(type instanceof NullType)
            return type;
        if(type instanceof ClassType)
            return doSubstitutions( (ClassType<?>)type, substitution );
        if(type instanceof TypeVar)
            return doSubstitutions((TypeVar<?>) type, substitution);
        if(type instanceof ArrayType)
            return doSubstitutions( (ArrayType<?>)type, substitution ) ;
        if(type instanceof IntersectionType)
            return doSubstitutions( (IntersectionType<?>)type, substitution );
        throw new AssertionError();
    }
    static ClassType<?> doSubstitutions(ClassType<?> type, Map<TypeVar, ReferenceType<?>> substitution)
    {
        List<TypeArg> oldArgs = type.args;
        ArrayList<TypeArg> newArgs = new ArrayList<TypeArg>(oldArgs.size());
        int argsChanged=0;
        for(TypeArg oldArg : oldArgs)
        {
            TypeArg newArg = doSubstitutions(oldArg, substitution);
            newArgs.add(newArg);
            if( newArg != oldArg )
                argsChanged++;
        }
        if(argsChanged==0)
            return cast( type ); // unchanged
        return new ClassType.Impl<Object>( false, type.clazz, newArgs );
    }
    static ArrayType<?> doSubstitutions(ArrayType<?> arrayType, Map<TypeVar, ReferenceType<?>> substitution)
    {
        JavaType<?> compoType = arrayType.getComponentType();
        if(!(compoType instanceof ReferenceType)) // primitive array
            return cast( arrayType );
        ReferenceType<?> ct1 = (ReferenceType<?>)compoType;
        ReferenceType<?> ct2 = doSubstitutions(ct1, substitution);
        if(ct2==ct1)
            return cast( arrayType ); // unchanged
        return new ArrayType<Object>(ct2);
    }
    static ReferenceType<?> doSubstitutions(TypeVar<?> var, Map<TypeVar, ReferenceType<?>> substitution)
    {
        ReferenceType<?> arg = substitution.get(var);
        if(arg==null)
            return var; // unchanged. no substitution for var
        return arg;
    }
    static ReferenceType<?> doSubstitutions(IntersectionType<?> intersection,Map<TypeVar,ReferenceType<?>> substitution)
    {
        ArrayList<ReferenceType<?>> types = new ArrayList<ReferenceType<?>>();
        int changed=0;
        for(ReferenceType<?> t1 : intersection.getSuperTypes())
        {
            ReferenceType<?> t2 = doSubstitutions(t1, substitution);
            if(t2!=t1) changed++;
            types.add(t2);
        }
        if(changed==0)
            return intersection;
        return new IntersectionType<Object>(types);
    }

    // =============================================================================================== wildcard capture

    /**
     * Perform capture conversion.
     */
    // 5.1.10. symbols: generic declaration G<A1..An>, type=G<T1..Tn>, convert it to G<S1..Sn>
    static public <T1,T2> ClassType<T2> doCaptureConversion(ClassType<T1> type)
    {
        // usually, no wildcard args, no conversion is needed.
        // do a quick check, before the more expensive algorithm.
        if(!type.hasWildcard())
            return cast (type);

        // S1..Sn
        ArrayList<ReferenceType<?>> arrayS = new ArrayList<ReferenceType<?>>();
        // substitution [ Ai := Si ]
        HashMap<TypeVar,ReferenceType<?>> A2S = new HashMap<TypeVar,ReferenceType<?>>();
        // create fresh type vars for wildcard args
        List<TypeVar<?>> vars = type.getTypeVars();
        for(int i=0; i<vars.size(); i++)
        {
            TypeVar Ai = vars.get(i);
            TypeArg Ti = type.args.get(i);
            ReferenceType<?> Si;
            if(Ti instanceof Wildcard)
                Si = new TypeVar_Captured<Object>( Ai, (Wildcard)Ti );
            else
                Si = (ReferenceType<?>) Ti;

            arrayS.add(Si);
            A2S.put(Ai,Si);

            // for new var. we can't define the bounds here, which may refer to self or other new vars.
        }

        // set bounds for the fresh type vars
        for(ReferenceType<?> Si : arrayS)
        {
            if(!(Si instanceof TypeVar_Captured))
                continue;
            TypeVar_Captured<?> Zi = (TypeVar_Captured<?>)Si;
            // Zi.lower = Wi.lower | ( Ai.lower[Aj:=Sj] )
            // however, Ai is declared, Ai.lower=null, no need for union.
            Zi.lowerBound = Zi.wildcard.lowerBound;
            // Zi.upper = Wi.upper & ( Ai.upper[Aj:=Sj] )
            ReferenceType<?> AiUpper = Zi.var0.getUpperBound();
            ReferenceType<?> AiUpperSubs = doSubstitutions(AiUpper, A2S);
            // courtesy simplification for two common cases
            if(AiUpperSubs.isObject())                  // type param without explicit upper bound
                Zi.upperBound = Zi.wildcard.upperBound;
            else if(Zi.wildcard.upperBound.isObject())  // wildcard without explicit upper bound
                Zi.upperBound = AiUpperSubs;
            else // general case; intersection, no simplification
                Zi.upperBound = new IntersectionType( Zi.wildcard.upperBound, AiUpperSubs );
            // note: if the bound is an intersection, and if we want to simplify it, we can't do it here.
            // intersection simplification depends on subtyping, which may depend on bounds of other new vars,
            // which may not have been set yet. so simplification can't be done in this loop.
        }

        List<TypeArg> newArgs = cast( arrayS );
        return new ClassType.Impl<T2>(false, type.clazz, newArgs);
        // the well-formed-ness of new vars should be checked by caller
    }

    // ===================================================================================================== subtyping
    // we don't care about primitive subtyping (4.10.1)

    /**
     * Test whether `typeA` is a subtype of `typeB`.
     */
    static public boolean isSubType(ReferenceType<?> typeA, ReferenceType<?> typeB)
    {
        /* rules. left choices = A, top choices = B

                        null  class  array  var  intersect
                 null    1      1      1     1     1
                class    -      0      -     6     2
                array    -      8      7     6     2
                  var    5      5      5   456     2
            intersect    3      3      3    36     2

         */

        //[1]
        if(typeA instanceof NullType)
            return true;

        //[0]
        if(typeA instanceof ClassType && typeB instanceof ClassType)
            return isSubType((ClassType<?>) typeA, (ClassType<?>) typeB);

        if(typeB instanceof IntersectionType)
        {
            //[2] A<B1 and..and A<Bm. (m=0 => true; B~Object)
            for(ReferenceType<?> tb : ((IntersectionType<?>)typeB).getSuperTypes())
                if(!isSubType(typeA, tb))
                    return false;
            return true;
        }
        TypeVar<?> varB = (typeB instanceof TypeVar) ? (TypeVar<?>)typeB : null;
        if(typeA instanceof IntersectionType)
        {
            IntersectionType<?> intersectA = (IntersectionType<?>)typeA;
            if(intersectA.getSuperTypes().size()==0) // degenerate, not necessarily false
                return isSubType(ClassType.OBJECT, typeB);

            //[3] A1<B or..or An<B. (n>=1)
            for(ReferenceType<?> ta : intersectA.getSuperTypes())
                if(isSubType(ta, typeB))
                    return true;

            // or [6]
            return varB!=null && isSubType(typeA, varB.getLowerBound());
        }
        if(typeA instanceof TypeVar)
        {
            // [4]
            if(varB!=null && typeA.equals(typeB))
                return true;
            // or [5]
            if(isSubType(((TypeVar<?>)typeA).getUpperBound(), typeB))
                return true;
            // or [6]
            return varB!=null && isSubType(typeA, varB.getLowerBound());
        }
        // [6]
        if(varB!=null) // typeB instanceof TypeVar
        {
            return isSubType(typeA, varB.getLowerBound());
        }
        if(typeA instanceof ArrayType)
        {
            if(typeB instanceof ArrayType) //[7]
                return isSubType((ArrayType<?>) typeA, (ArrayType<?>) typeB);
            if(typeB instanceof ClassType) //[8]
                return isSuperClassOfArrays(((ClassType<?>) typeB).clazz);
            // [-]
        }
        // [-]
        return false;
    }

    static boolean isSuperClassOfArrays(Class clazz) // 4.10.3
    {
        return clazz==Object.class || clazz==Cloneable.class || clazz==Serializable.class;
    }

    static boolean isSubType(ArrayType<?> typeA, ArrayType<?> typeB)
    {
        JavaType<?> compoA = typeA.componentType;
        JavaType<?> compoB = typeB.componentType;
        if(compoA.equals(compoB))
            return true;
        if(compoA instanceof ReferenceType && compoB instanceof ReferenceType)
            return isSubType((ReferenceType<?>) compoA, (ReferenceType<?>) compoB);
        // one is primitive type, and other is not the same primitive
        return false;
    }
    static boolean isSubType(ClassType<?> typeA, ClassType<?> typeB)
    {
        if(!typeB.clazz.isAssignableFrom(typeA.clazz))
            return false;

        if( typeB.args.size()==0 ) // B is raw or non-generic
            return true;           // trivially true

        // B is generic and non-raw
        if( typeA.isRawType() ) // raw types' super types are raw too
            return false;
        // note: that's harsh. e.g. class A<T> extends B<Int>, A(raw) is not subtype of B<Int>

        // neither raw.
        // capture conversion is required, even if A and B are of the same class.
        ClassType<?> typeA2 = doCaptureConversion(typeA);
        ClassType<?> typeA3 = getSuperType(typeA2, typeB.clazz);  // no wildcard arg
        if(typeA3.isRawType()) // possible, A extends raw B
            return false;
        for(int i=0; i<typeA3.args.size(); i++)
            if( ! contains(typeB.args.get(i), (ReferenceType<?>)typeA3.args.get(i)) )
                return false;
        return true;
    }

    /**
     * Get the supertype of `type` at the specified `superClass`.
     * <p>
     *     For example,
     * </p>
     * <pre>
     *     getSuperType( <i>ArrayList&lt;String&gt;</i> , Collection.class) =&gt; <i>Collection&lt;String&gt;</i>
     * </pre>
     * <p>
     *     `type` must not contain wildcard;
     *     if necessary, caller should do wildcard capture first.
     * </p>
     */
    // get the super type with the specified class.
    // e.g. getSuperType(ArrayList<String>, Collection.class) == Collection<String>
    // both type and super type contain no wildcard. caller should do capture conversion on type first.
    public static ClassType<?> getSuperType(ClassType<?> type, Class<?> superClass)
    {
        assert superClass.isAssignableFrom(type.clazz);

        Class clazz = type.clazz;
        if(clazz==superClass)
            return type;

        if(type.isRawType())
            return new ClassType.Impl(false, superClass); // super types of raw are raw

        // type = A<a1..an>
        // class A<T1..Tn> extends B< s1(T1..Tn) .. sm(T1..Tn >

        // directParent = B< s1(T1..Tn) .. sm(T1..Tn) >
        Type directParent0 = findDirectParent(clazz, superClass); // Class or ParameterizedType
        ClassType<?> directParent = (ClassType<?>)convertType(directParent0);
        if(directParent.isRawType())
            return new ClassType.Impl(false, superClass); // super types of raw are raw

        // args = [ Ti := ai ]
        Map<TypeVar, TypeArg> args0 = getTypeArgs(type);
        Map<TypeVar, ReferenceType<?>> args = cast(args0); // safe cast, `type` has no wildcard arg.

        // B< s1(a1..an) .. sm(a1..an) >
        ClassType<?> directParent2 = doSubstitutions(directParent, args);

        // recursive, until superClass is reached
        return getSuperType(directParent2, superClass);
    }
    // direct parent that is also subtype of superClass
    // both args are class/interface, and thisClass is assignable to superClass
    static Type findDirectParent(Class thisClass, Class superClass)
    {
        // search interfaces first. for example:
        //     class ArrayList<E> extends AbstractList<E> implements List<E> ...
        // we are most likely interested in `List<E>`, which is redundantly declared here for fast access.
        // no need to search interfaces if superClass is a class (not an interface)

        if(superClass.isInterface())
            for(Type directSuperInterface : thisClass.getGenericInterfaces())
                if(isAssignable(directSuperInterface, superClass))
                    return directSuperInterface;

        Type directSuperClass = thisClass.getGenericSuperclass(); // can be null
        if(directSuperClass!=null)
            if(isAssignable(directSuperClass, superClass))
                return directSuperClass;

        // if we are here, it must be that thisClass is Object/interface and superClass is Object. (4.10.2 )
        return superClass;
    }
    static boolean isAssignable(Type type, Class superClass)
    {
        Class clazz;
        if(type instanceof Class)
            clazz = (Class)type;
        else if(type instanceof ParameterizedType)
            clazz = (Class)((ParameterizedType)type).getRawType();
        else
            throw new AssertionError();
        return superClass.isAssignableFrom(clazz);
    }

    // spec (4.5.1.1) has clauses for arg2 being wildcard;
    // however in actual usage (4.10.2), arg2 is always a reference type, never a wildcard.
    static boolean contains(TypeArg arg1, ReferenceType<?> arg2) // JLS3#4.5.1.1
    {
        if(arg1 instanceof ReferenceType)
            return isEquivalent((ReferenceType<?>)arg1, arg2);

        Wildcard wild = (Wildcard)arg1;
        return isSubType(wild.lowerBound, arg2)
            && isSubType(arg2, wild.upperBound);
    }

    // A<:B && B<:A
    // it's possible that A,B are syntactically different
    static boolean isEquivalent(ReferenceType<?> typeA, ReferenceType<?> typeB)
    {
        return typeA.equals(typeB) // shortcut
            || isSubType(typeA, typeB)
            && isSubType(typeB, typeA);
    }

    // =================================================================================================== well formed

    static void assertWellFormed(ReferenceType<?> type0) throws AssertionError
    {
        if(type0 instanceof IntersectionType)
        {
            for(ReferenceType<?> ti : ((IntersectionType<?>)type0).getSuperTypes())
                assertWellFormed(ti);
        }
        else if(type0 instanceof ArrayType)
        {
            JavaType<?> ct = ((ArrayType<?>)type0).getComponentType();
            if(ct instanceof ReferenceType)
                assertWellFormed((ReferenceType<?>) ct);
            // primitive ct, trivially well formed
        }
        else if(type0 instanceof TypeVar)
        {
            assertWellFormed((TypeVar<?>)type0);
        }
        else if(type0 instanceof ClassType)
        {
            assertWellFormed((ClassType<?>) type0);
        }
        // NullType, trivially well formed
    }
    static void assertWellFormed(TypeVar<?> var)
    {
        if(!(var instanceof TypeVar_Captured))
            return; // declared var; we can trust it

        // captured var V, for wildcard W on type parameter P

        if(!isSubType(var.getLowerBound(), var.getUpperBound()))
            throw new AssertionError("invalid type var, lower bound is not subtype of upper bound: "+var);

        // if W=(? extends X), V.upper = X & P.upper[subs].
        // V.upper can be patently unsatisfiable (except by null type).
        // Daniel Smith's paper doesn't do any check; null type is an acceptable subtype of the bound.
        // the language spec isn't clear on the matter. javac 7 does some checking: (from observation)
        //      V<:A and V<:B, A,B are classes(not interface) => A<:B or B<:A
        //      V<:G<A> and V<:G<B> => A=B
        //      V<:F, F is a final class => F<:V.upper
        //      V<:A[] and V<:B[] => A<:B or B<:A
        //      V<:A[], V<:B, B is class/interface => B=Object/Cloneable/Serializable
        // currently, we don't do these; they are not critical for soundness of our algorithms.
        // if a user supplies an improper wildcard, we won't throw error promptly,
        // he'll see problems later and he'll have to trace it back to the wildcard by himself.

        // weird behavior in javac 7 if W's bound is a type var:
        //        class G<N extends Number> {}
        //        <T> void f1(G<? extends T> k){} //error. why?
        //        <T> void f2(G<? super   T> k){} //ok. why?

    }

    static void assertWellFormed(ClassType<?> type0) throws AssertionError
    {
        assertIsClassOrInterface(type0.clazz);

        if(type0.args.size()==0) // non-generic or raw
            return;

        if(type0.args.size() != type0.getTypeVars().size())
            throw new AssertionError("wrong number of type arguments");

        // formally,
        // class C<P1..Pn>. for C<W1..Wn> to be well formed
        // do capture conversion on T, getting C<T1..Tn>
        // for every Ti,
        //   (1) Ti<Pi.upper[Pj=Tj] (no lower bound for declared Pi)
        //   (2) Ti must be well-formed

        // optimized:
        // if Ti=Wi, no need to check (2).
        //    Wi is a reference type; at the time it was constructed, it was checked to be well-formed already.
        // if Ti!=Wi, no need to check (1)
        //    Ti is a fresh var created by capture; Ti.upper = Wi.upper & Pi.upper[Pj=Tj], already in bounds


        ClassType<?> type = doCaptureConversion(type0);
        Map<TypeVar, TypeArg> argMap0 = getTypeArgs(type);
        Map<TypeVar, ReferenceType<?>> argMap = cast(argMap0); // safe cast, `type` has no wildcard arg.

        for(int i=0; i<type.args.size(); i++)
        {
            TypeVar<?> Pi = type.getTypeVars().get(i);
            TypeArg Wi = type0.args.get(i);
            ReferenceType<?> Ti = (ReferenceType<?>)type.args.get(i);

            if(Ti==Wi)
            {
                ReferenceType<?> upperSubs = doSubstitutions(Pi.getUpperBound(), argMap);
                if(!isSubType(Ti, upperSubs))
                    throw new AssertionError(
                        "Type argument "+type0.args.get(i)+" is not within bounds of type variable "+Pi);
            }
            else // a new captured var. check its bounds
            {
                try
                {   assertWellFormed((TypeVar)Ti);   }
                catch(AssertionError e) // mask the msg; users don't know/care about vars from capture
                {
                    AssertionError e2 = new AssertionError(
                        "Type argument "+type0.args.get(i)+" is not within bounds of type variable "+Pi);
                    e2.initCause(e);
                    throw e2;
                }
                // example: class C<T ext String>. C<? super Number> => C<W>, Number<W<String, W is not well-formed,
                // since (W.lower<W.upper)=false. Error msg: "? super Number is not within bounds of T"
            }
        }
    }

    // ========================================================================================================= misc

    // caller makes sure that the cast is safe
    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj)
    {
        return (T)obj;
    }

    // for inner member class, also include args from owner type. see ClassType doc.
    static ArrayList<Type> collectAllArgs(ParameterizedType paramType, ArrayList<Type> args)
    {
        // args from owner
        Type ownerType = paramType.getOwnerType();
        if(ownerType instanceof ParameterizedType)
            collectAllArgs((ParameterizedType)ownerType, args);
        // and args from me
        Collections.addAll(args, paramType.getActualTypeArguments());
        return args;
    }

    // vars of class.
    // for inner member class, also include vars from declaring class. see ClassType doc.
    static ConcurrentHashMap<Class, List<TypeVar<?>>> classVars = new ConcurrentHashMap<Class, List<TypeVar<?>>>();
    // returned list is immutable (for further safe publication)
    static List<TypeVar<?>> getVars(Class clazz)
    {
        List<TypeVar<?>> vars = classVars.get(clazz);
        if(vars==null)
        {
            vars = _collectAllVars(clazz, new ArrayList<TypeVar<?>>());
            // make an immutable list
            TypeVar<?>[] arr = new TypeVar<?>[vars.size()];
            for(int i=0; i<vars.size(); i++)
                arr[i] = vars.get(i);
            vars = new _Array2ReadOnlyList<TypeVar<?>>(arr); // immutable

            classVars.putIfAbsent(clazz, vars);
        }
        return vars;
    }
    static ArrayList<TypeVar<?>> _collectAllVars(Class clazz, ArrayList<TypeVar<?>> vars)
    {
        assertIsClassOrInterface(clazz);

        // owner vars if I am inner member class
        Class ownerClass = clazz.getDeclaringClass();
        if(ownerClass!=null && !Modifier.isStatic(clazz.getModifiers()) )
            _collectAllVars( ownerClass, vars);
        // my vars
        for(TypeVariable tv : clazz.getTypeParameters())
            vars.add( convertType(tv) );
        return vars;
    }

    // a Class object may not represent a class or interface; throw error then.
    static void assertIsClassOrInterface(Class clazz) throws AssertionError
    {
        if(clazz.isPrimitive() || clazz.isArray())
            throw new AssertionError("not a class/interface: "+clazz);
    }

    // not used
    static ReferenceType<?> simplifyIntersection(IntersectionType<?> intersection)
    {
        ArrayList<ReferenceType<?>> types = new ArrayList<ReferenceType<?>>(intersection.getSuperTypes());
        int N = types.size();
        // eliminate super types
        for(int i=0; i<N; i++)
        {
            ReferenceType<?> ti = types.get(i);
            if(ti==null) continue;
            for(int j=0; j<N; j++)
            {
                if(i==j) continue;
                ReferenceType<?> tj = types.get(j);
                if(tj==null) continue;
                if(isSubType(ti, tj))
                    types.set(j, null);
            }
        }
        ArrayList<ReferenceType<?>> types2 = new ArrayList<ReferenceType<?>>(N);
        for(ReferenceType<?> ti : types)
            if(ti!=null)
                types2.add(ti);

        N = types2.size();
        if(N==0)
            return ClassType.OBJECT;
        if(N==1)
            return types2.get(0);

        // a proper intersection T1&..&Tn, n>2, no Ti <: Tj (i!=j)
        // it's not equivalent(semantically) to obj of any other class.
        // (in our type system, if neither T1 or T2 is null type, T1&T2 is not null type)
        return new IntersectionType(types2);
    }


    /**
     * Do diamond inference.
     * <p>
     *     For example
     * </p>
     * <pre>
     *     diamondInfer( <i>List&lt;? extends String&gt;</i> , ArrayList.class ) =&gt; <i>ArrayList&lt;String&gt;</i>
     *
     *     // as how javac performs diamond inference on
     *
     *     List&lt;? extends String&gt; list = new ArrayList&lt;&gt;();
     * </pre>
     * @throws java.lang.RuntimeException if inference fails
     */
    // solve Y = new G<>(), return X=G<t1..tn>, X<:Y
    public static ClassType<?> diamondInfer(ClassType<?> targetType, Class<?> genericClass) throws RuntimeException
    {
        return TypeInference.diamondInfer(targetType, genericClass);
    }

}
