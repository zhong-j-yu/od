package bayou.jtype;

import _bayou._tmp._Array2ReadOnlyList;

import java.util.Collections;
import java.util.List;

/**
 * Class or interface type.
 * ( Ideally this should be named as <code>ClassOrInterfaceType</code>, but that would be too long. )
 * <p>
 *     A ClassType may represent a parameterized types with type arguments.
 * </p>
 * <p>
 *     A ClassType can be created by one of the static factory methods, for example
 * </p>
 * <pre>
 *     ClassType&lt; <b>Map&lt;String,Integer&gt;</b> &gt; type = ClassType.of(Map.class, String.class, Integer.class);
 * </pre>
 * <p>
 *     or as "type literal" by anonymous subclass , for example
 * </p>
 * <pre>
 *     new ClassType&lt; <b>List&lt;String&gt;</b> &gt; (){}
 * </pre>
 */

/*
    class categories
        1. top level class
        2. static member class
        3. inner member class
        4. local class, declared in a block
        5. anonymous class, as an instantiation expression.
    2,3 are member classes. 2-5 are nested classes. 3,4,5 are inner classes.
    2 can only be contained by 1 or 2. 3 can be contained by any of 1-5.

    On type parameters/variables:

    1) for top level and static member classes, the only type parameters they have
    access to are the ones declared on themselves.

    most applications may want to only deal with top level and static member classes;
    inner classes are more difficult, as explained below.

    2) for an inner member class, it also has access to type parameters of enclosing classes.
    we consider all of them as type parameters of the inner member class. for example,
        class O<T> { class I<S>{} }, we represent the inner class as O.I<T,S>.
    this is diff from the typical representation, O<T>.I<S>. ours is simpler to work with.
    (we don't have "owner type" support, i.e. extract O<T> from O.I<T,S>; a use case hasn't come up)

    3) local/anon classes are more hairy. If one is declared in a method/constructor, it has
    access to the type parameters of the method/constructor. One can also be declared in
    an instance or static initializer, without enclosing method/constructor. One is either
    in or not in a static context. If it's not in a static context, it has an enclosing
    instance, and it has access to type parameters of the enclosing class. If it is in a
    static context, it has no enclosing instance, no access to type parameters of the
    enclosing class.

    this is too much. There is no intuitive way to define what type parameters from its
    environments should be included for the local/anon class.

    even JDK screws up. owner type for local/anon types are not well defined.
    various ways of trying to get a ParameterizedType for a generic local class always end up
    with GenericSignatureFormatError. And if a local/anon class has no enclosing method/constructor,
    it's in an initializer,  JDK doesn't tell us if the initializer is instance or static, so we can't
    know if it's in a static context, and whether it has access to enclosing class's type parameters.

    so our approach is to consider the type parameters only declared on the class itself. "alien" type
    parameters from the enclosing environments are overlooked. therefore
    we treat local/anon classes effectively the same as top level classes for type parameters/arguments.
    usually local/anon classes don't escape so we won't encounter them. when we do encounter
    a local/anon class, typically from an object passed to us, mostly we are interested in its super
    class/interface. if the generic super type does not contain type variables, we are fine.

*/

public abstract class ClassType<T> extends ReferenceType<T>
{
    /**
     * Create a ClassType with the class and type arguments.
     * <p>
     *     If `clazz` is generic, and `typeArgs` is empty,
     *     this method creates a raw type.
     * </p>
     */
    static public <C, T extends C> ClassType<T> of(Class<C> clazz, TypeArg... typeArgs)
    {
        typeArgs = typeArgs.clone(); // defensive
        return new ClassType.Impl<T>(true, clazz, typeArgs);
    }

    /**
     * Create a ClassType with the class and type arguments.
     * <p>
     *     This is a convenience method for
     *     {@link #of(Class, TypeArg...) of(Class, TypeArg...)}
     *     by converting each <code>Class</code> to a <code>TypeArg</code>.
     * </p>
     * <p>
     *     Example
     * </p>
     * <pre>
     *     ClassType&lt;Map&lt;String,Integer&gt;&gt; type = ClassType.of(Map.class, String.class, Integer.class);
     * </pre>
     */
    static public <C, T extends C> ClassType<T> of(Class<C> clazz, Class<?>... typeArgs)
    {
        TypeArg[] formalArgs = new TypeArg[typeArgs.length];
        for(int i=0; i<typeArgs.length; i++)
            formalArgs[i] = of(typeArgs[i]);
        return new ClassType.Impl<T>(true, clazz, formalArgs);
    }

    /**
     * Create a ClassType representing the class. For example
     * <pre>
     *     ClassType&lt;String&gt; type = ClassType.of(String.class);
     * </pre>
     * <p>
     *     If `clazz` is generic, this method creates a raw type.
     * </p>
     */
    // this method is needed to resolve the ambiguity of two methods `of(clazz,typeArgs)` when typeArgs is empty.
    static public <T> ClassType<T> of(Class<T> clazz) // if clazz is generic, return raw type
    {
        TypeMath.assertIsClassOrInterface(clazz); // all the validation we need

        List<TypeArg> args = NO_ARG_LIST;

        return new ClassType.Impl<T>(false, clazz, args);
    }

    /**
     * Create a ClassType, using type variables as type arguments.
     * <p>
     *     For example,
     *     <code>ClassType.withTypeVars(java.util.Map.class)</code>
     *     returns a
     *     <code>ClassType&lt;K,V&gt;</code>
     *     where <code>K,V</code> are type variables defined on
     *     <code>java.util.Map&lt;K,V&gt;</code>.
     * </p>
     */
    // include vars as args. class G<T>   =>   withTypeVars(G.class)==G<T>
    static public <C> ClassType<? extends C> withTypeVars(Class<C> clazz)
    {
        TypeMath.assertIsClassOrInterface(clazz); // all the validation we need

        List<TypeArg> args = TypeMath.cast(TypeMath.getVars(clazz)); // safe cast

        return new ClassType.Impl<C>(false, clazz, args);
    }

    // user can create a class type by convertXXX() methods, of() methods,
    // or by "ClassType literal", i.e. a subclass supplying the concrete T.
    //     new ClassType<X> (){}  // anonymous subclass, supplying T=X
    // the constructor ClassType() which then extracts the T from `this`.
    // the subclass cannot change the behavior of ClassType, so most methods in ClassType are final.
    // subclass can still override toString(), clone(), finalize(). that's fine, no harm to us.

    /**
     * For anonymous subclass to create type literal.
     * <p>
     *     This protected constructor is only intended to be used
     *     to create "type literal" by anonymous subclass. For example
     * </p>
     * <pre>
     *     new ClassType&lt; <b>List&lt;String&gt;</b> &gt; (){}
     * </pre>
     */
    protected ClassType()
    {
        // the constructor will reflect on `this`, and extract `T`.
        Class<?> thisClass = this.getClass();
        ClassType<?> thisType = ClassType.withTypeVars(thisClass);
        if(thisType.getTypeVars().size()>0)
            throw new IllegalArgumentException("must not contain type parameters: "+thisType.ss());
        ClassType<?> superType = TypeMath.getSuperType(thisType, ClassType.class);
        if(superType.args.size()==0) // subclass extends raw ClassType
            throw new IllegalArgumentException("this.getClass() must not extend raw ClassType: "+thisType.toString(true));
        assert superType.clazz==ClassType.class && superType.args.size()==1;
        TypeArg argT = superType.args.get(0);
        if(!(argT instanceof ClassType)) // could be array, or even type variable; not supported
            throw new IllegalArgumentException("T for ClassType<T> must be a class/interface type: T = " + argT);
        ClassType<?> literal = (ClassType<?>)argT;
        this.clazz = literal.clazz;
        this.args = literal.args;
        // no validation
    }

    final Class clazz;
    final List<TypeArg> args;
    // if clazz is an inner member class, we merge all type args, including outer ones.
    // a type O<T>.I<S> is represented by us as O.I<T,S>. that's ok for most purposes.

    ClassType(boolean validate, Class<?> clazz, List<TypeArg> args)
    {
        this.clazz = clazz;
        this.args = args;

        // constructed by user, we need to validate typeArgs.
        if(validate)
            TypeMath.assertWellFormed(this);
    }

    // use case without type args is quite common. optimize.
    static final _Array2ReadOnlyList<TypeArg> NO_ARG_LIST = new _Array2ReadOnlyList<TypeArg>(new TypeArg[0]);

    /**
     * The <code>java.lang.Object</code> type.
     */
    static public final ClassType<Object> OBJECT = of(Object.class);

    // to avoid accidentally new ClassType(), instead of on a subclass for literal,
    // ClassType is marked abstract. we need a secrete subclass for instantiation.
    static class Impl<T> extends ClassType<T>
    {
        // caller ensures that `args` won't be modified further
        Impl(boolean validate, Class<?> clazz, List<TypeArg> args)
        {
            super(validate, clazz, args);
        }
        // caller ensures that `args` won't be modified further
        Impl(boolean validate, Class<?> clazz, TypeArg... args)
        {
            super(validate, clazz, toList(args));
        }
        static List<TypeArg> toList(TypeArg... args)
        {
            return args.length==0 ? NO_ARG_LIST : new _Array2ReadOnlyList<TypeArg>(args);
        }
    }

    boolean isObject(){ return clazz==Object.class; }

    /**
     * Get the class or interface of this type.
     * <p>
     *     For example, if this ClassType represents <code>List&lt;String&gt;</code>,
     *     this method will return <code>List.class</code>.
     * </p>
     * <p>
     *     Strictly speaking, the return type of this method should be
     *     <code>Class&lt;|T|&gt;</code> where <code>|T|</code> is the erasure of <code>T</code>.
     *     However, we cannot express that constraint precisely.
     *     Therefore the return type might be for example  <code>Class&lt;List&lt;String&gt;&gt;</code>
     *     which technically does not exist.
     * </p>
     */
    // strictly speaking, the return type should be Class<|T|>, which we can't express.
    // Class<T> can be useful in many cases, but misleading in others. be careful.
    //     Class<ArrayList<String> clazz = ...; // there is really no such class
    //     ArrayList<String> x = clazz.newInstance(); // but it's useful here
    //     clazz.isInstance(x); // misleading. only checks the raw class, not type args.
    final public Class<T> getTheClass()
    {
        return TypeMath.cast(clazz);
    }

    /**
     * Get the type arguments.
     * <p>
     *     For example, if this ClassType represents
     *     <code>Map&lt;String,Integer&gt;</code>,
     *     this method returns <code>[String,Integer]</code>.
     * </p>
     * <p>
     *     Return an empty list if this is not a parameterized type,
     *     either because the class/interface is non-generic, or this is a raw type.
     * </p>
     */
    final public List<TypeArg> getTypeArgs()
    {
        return Collections.unmodifiableList(args);
    }

    List<TypeVar<?>> vars;

    /**
     * Get the type variables of the class/interface definition.
     * <p>
     *     For example, if <code>getTheClass()</code> is <code>java.util.Map.class</code>,
     *     this method returns <code>[K,V]</code> as they are declared on <code>Map</code>.
     * </p>
     * <p>
     *     Return an empty list if the class/interface is not generic.
     * </p>
     */
    final public List<TypeVar<?>> getTypeVars()
    {
        List<TypeVar<?>> v = vars;
        if(vars==null)
        {
            v = TypeMath.getVars(this.clazz);
            // the object is immutable, therefore the caching is safe
            vars = v;
        }
        return v;
    }

    /**
     * Whether this is a raw type.
     * <p>
     *     If the class/interface is generic, but there is no type arguments
     *     for this ClassType, this is a raw type.
     * </p>
     */
    final public boolean isRawType()
    {
        return getTypeVars().size()>0 && args.size()==0;
    }

    /**
     * Whether any type argument is a wildcard.
     */
    final public boolean hasWildcard()
    {
        for(TypeArg arg : args)
            if(arg instanceof Wildcard)
                return true;
        return false;
    }

    final
    public int hashCode(){ return super.hashCode(); }

    final int genHash()
    {
        return clazz.hashCode() + 31* args.hashCode();
    }

    /**
     *  Whether this type is equal to another type.
     *  <p>
     *      A `ClassType` is only equal to another `ClassType`
     *      with the same {@link #getTheClass() class}
     *      and the same {@link #getTypeArgs() type arguments}.
     *  </p>
     */
    // super util equals()->eqX() may not work here.
    // this class is not final; it's designed to be subclass-ed for type literal, and for Impl.
    final public boolean equals(Object obj)
    {
        return this==obj ||
            (obj instanceof ClassType) && this.equals((ClassType<?>)obj);
    }
    boolean equals(ClassType<?> that)
    {
        return eq(this.clazz, that.clazz) && eq(this.args, that.args);
    }
    // will not be called
    final boolean eqX(Object obj){ return this.equals((ClassType<?>)obj); }

    final public String toString(boolean full)
    {
        StringBuilder sb = new StringBuilder();
        sb.append( full? clazz.getName() : getSimplestName(clazz) );
        if(args.size()>0)
        {
            sb.append('<');
            for(int i=0; i< args.size(); i++)
                sb.append(i==0?"":", ").append(args.get(i).toString(full));
            sb.append('>');
        }
        return sb.toString();
        // if this is an inner member class O<T>.I<S>, we print it as O.I<T,S>.
        // that is ok; it is not common anyway.
    }

    static String getSimplestName(Class clazz)
    {
        String fullName = clazz.getName();
        // find last . or $
        int i;
        for(i=fullName.length()-1; i>=0; i--)
        {
            char c = fullName.charAt(i);
            if(c=='.' || c=='$')
                break;
        }
        //if not found, i==-1
        return fullName.substring(i+1);
    }




}
