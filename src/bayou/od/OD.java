package bayou.od;

import bayou.jtype.ClassType;
import bayou.jtype.TypeMath;

import java.util.*;

/**
 * A Service Locator library.
 * <p>
 *     See <a href="package-summary.html#package.description">package doc</a>.
 * </p>
 * <p>
 *     This class (<code>OD</code>) is a namespace containing some interfaces and static methods.
 * </p>
 */
public class OD
{
    private OD(){}


    /**
     * Similar to <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/Predicate.html">Predicate</a>
     * in Java 8.
     * <p>
     *     We include the interface here because OD library requires only Java 6.
     * </p>
     */
    public interface Predicate<T>
    {
        boolean test(T t);
    }
    /**
     * Similar to <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html">Supplier</a>
     * in Java 8.
     * <p>
     *     We include the interface here because OD library requires only Java 6.
     * </p>
     */
    public interface Supplier<T>
    {
        T get();
    }


    // query ---------------------------------------------------------------------------------------

    /**
     * Binding is not found for (type,tags).
     * <p>
     *     This exception may be thrown from {@link OD#get(bayou.jtype.ClassType, Object...) OD.get(type,tags)}
     *     if no binding matches (type,tags).
     * </p>
     * <p>
     *     This is a RuntimeException;
     *     it is considered a configuration error, and it is not supposed to happen.
     * </p>
     */
    static public class NotFoundException extends RuntimeException
    {
        final ClassType<?> type;
        final Object[] tags;
        NotFoundException(ClassType<?> type, Object[] tags)
        {
            super();
            this.type = type;
            this.tags = tags;
        }
        public String getMessage()
        {
            return String.format("type=%s, tags=%s",
                type.toString(false), Arrays.toString(tags));
        }

        /**
         * The `type` in {@link OD#get(bayou.jtype.ClassType, Object...) OD.get(type,tags)}.
         */
        public ClassType<?> getType() { return type; }
        /**
         * The `tags` in {@link OD#get(bayou.jtype.ClassType, Object...) OD.get(type,tags)}.
         */
        public Object[] getTags() { return tags; }
    }


    /**
     * Lookup an object by (type,tags).
     * <p>
     *     This method is equivalent to {@link #get(ClassType, Object...)}
     *     by wrapping the `Class` as `ClassType`; see {@link ClassType#of(Class)}.
     * </p>
     */
    static public <T> T get(Class<T> type, Object... tags) throws NotFoundException
    {
        return get(ClassType.of(type), tags);
    }

    /**
     * Lookup an object by (type,tags).
     * <p>
     *     The local binding list is searched before the global binding list.
     *     If a binding is found to {@link Binding#map(ClassType, Object...) match (type,tags)},
     *     the supplier is invoked to return a `T` value.
     *     Otherwise, `NotFoundException` is thrown.
     * </p>
     * @see #get(Class, Object...)
     * @throws NotFoundException
     *         if binding is not found for (type,tags)
     */
    static public <T> T get(ClassType<T> type, Object... tags) throws NotFoundException
    {
        if(tags==null) throw new IllegalArgumentException("tags==null");

        Supplier<T> supplier = getSupplier(type, tags); // throws
        if(supplier==null)
            throw new NotFoundException(type, tags);
        else
            return supplier.get(); // throws
    }

    // static public <T> Optional<T> find(type, tags)
    //  - requires java8. we don't want to define our own Optional here.
    // leave it to user to define a helper method like that.


    static <T> Supplier<T> getSupplier(ClassType<T> type, Object... tags) //throws
    {
        TypeAndTags<T> tnt = new TypeAndTags<T>(type, tags);

        Supplier<? extends T> supplier = LocalBindings.getSupplier(tnt); //throws
        if(supplier ==null)
            supplier = GlobalBindings.getSupplier(tnt); // throws

        return cast(supplier); // Supplier<? extends T> to Supplier<T>
    }

    // multiple bindings. in their binding order, from global to local.

    static <T> List<T> getAll(ClassType<T> type, Object... tags)
    {
        List<Supplier<T>> suppliers = getAllSuppliers(type, tags);  // throws
        ArrayList<T> objects = new ArrayList<T>( suppliers.size() );
        for(Supplier<T> supplier : suppliers)
            objects.add( supplier.get() ); // can be null
        return objects;
    }

    static <T> List<Supplier<T>> getAllSuppliers(ClassType<T> type, Object... tags) // throws
    {
        Class clazz = type.getTheClass();
        ArrayList<Supplier<T>> suppliers = new ArrayList<Supplier<T>>();

        List<Binding> globalList = GlobalBindings.snapshot(clazz);
        findSuppliers(suppliers, globalList, type, tags);

        LocalBindings lb = LocalBindings.localBindings_TL.get();
        if(lb!=null)
            findSuppliers(suppliers, lb.bindingList.forClass(clazz), type, tags);

        return suppliers;
    }

    // bind -------------------------------------------------------------------------------------

    /**
     * Binding of (type,tags)-&gt;supplier.
     * <p>
     *     A Binding is just a mapping of (type,tags)-&gt;supplier.
     *     If a (type,tags) is mapped to a non-null supplier,
     *     we say this binding applies to (type,tags).
     * </p>
     * <p>
     *     A subclass implements {@link #map(ClassType, Object...)}
     *     to provide arbitrary binding strategy. For example, a Binding
     *     can choose to apply to all subtypes of `Pet`,
     *     returning a different supplier for each subtype.
     * </p>
     * <p>
     *     A Binding should be stateless and thread-safe.
     * </p>
     */
    public interface Binding
    {
        // a binding is just a mapping of [type,tags]=>supplier.
        // if a binding maps a [type,tags] to a non-null supplier,
        //     we say the binding applies to [type,tags] (we also say it applies to type and type.class)

        // convention for most bindings:
        //     if [s,tags] is applicable, t:>s, t.class=s.class, then [t,tags] is also applicable.

        // if [t,tags] is applicable, and there's not another applicable [s,tags] so that t:>s,
        //     we say [t,tags] is minimum applicable. we usually only mention min applicable ones.

        // a simple binding has one min applicable [type,tags].
        // a more complex binding may have multiple min applicable [type,tags].

        // given an ordered list of bindings, we get a merged mapping of [type,tags]=>supplier.

        // a binding should be stateless and thread safe.
        // 2 calls to map(type,tags) should return 2 equal suppliers.
        // a binding is call-site agnostic. this enables us to cache suppliers.
        // supplier itself can return different objects based on different contexts,
        // e.g. it can detect caller somehow, and return an object suitable for each caller.


        /**
         * Map (type,tags) to a supplier.
         * <p>
         *     Return `null` if this Binding is not applicable to a given (type,tags).
         * </p>
         * <p>
         *     This method may be invoked multiple times for the same (type,tags);
         *     same or equivalent suppliers should be returned for the same (type,tags).
         * </p>
         * <p>
         *     The returned Supplier will be invoked once by every matching `OD.get()` call.
         * </p>
         * <p>
         *     The returned Supplier may be cached for multiple `OD.get()` calls.
         * </p>
         */
        <T> Supplier<? extends T> map(ClassType<T> type, Object... tags); // throws
        // impl can throw, if a binding encounters an out-of-ordinary problem

        /**
         * Get all classes this Binding may apply to.
         * <p>
         *     If this binding applies to a (type,tags),
         *     {@link ClassType#getTheClass() the class of the type} must be in the returned Set.
         *     It's OK if the Set contains more classes than necessary.
         * </p>
         * <p>
         *     Return `null` to represent the set of all classes,
         *     if it's difficult or impossible to enumerate applicable classes.
         * </p>
         * <p>
         *     This information is used for internal optimization;
         *     try to be precise and return the smallest set.
         * </p>
         */
        Set<? extends Class> getApplicableClasses();


        // use wildcard in return types; easier for impl code


    }

    // Global bindings

    /**
     * To add a global binding.
     * <p>
     *     This method is equivalent to {@link #bind(ClassType)}
     *     by wrapping the `Class` as `ClassType`; see {@link ClassType#of(Class)}.
     * </p>
     * <p>
     *     Example usage:
     * </p>
     * <pre>
     *     OD.bind(Foo.class).to(foo);
     * </pre>
     */
    static public <T> BindingBuilder<T> bind(Class<T> clazz){ return bind(ClassType.of(clazz)); }

    /**
     * To add a global binding.
     * <p>
     *     This method return a builder; when the builder finishes,
     *     a binding is created and added to the global binding list.
     * </p>
     * <p>
     *     Example usage:
     * </p>
     * <pre>
     *     OD.bind( ClassType.of(Foo.class) ).to(foo);
     * </pre>
     * @see #bind(Class)
     * @see bayou.jtype.ClassType
     */
    static public <T> BindingBuilder<T> bind(ClassType<T> type){ return new BindingBuilder<T>(false, type); }

    /**
     * Add the binding to the global binding list.
     */
    static public void bind(Binding binding) { GlobalBindings.addGlobal(binding); }

    // no way to remove global bindings.
    // get all global bindings?


    /**
     * Thread-local bindings.
     * <p>
     *     This class contains static methods for managing thread-local bindings.
     * </p>
     * <p>
     *     Each thread contains a local binding list;
     *     the local bindings are always looked up before the global bindings.
     * </p>
     * <p>
     *     Examples:
     * </p>
     * <pre>
     *          OD.Local.bind(Foo.class).to(foo1);
     *          ...
     *          OD.Local.bind(Foo.class).to(foo2);
     *
     *          // later in the same thread
     *          OD.get(Foo.class)  // sees `foo2`
     * </pre>
     */
    static public class Local
    {
        private Local(){}

        /**
         * To add a local binding.
         * <p>
         *     This method is equivalent to {@link #bind(ClassType)}
         *     by wrapping the `Class` as `ClassType`; see {@link ClassType#of(Class)}.
         * </p>
         */
        static public <T> BindingBuilder<T> bind(Class<T> clazz){ return bind(ClassType.of(clazz)); }

        /**
         * To add a local binding.
         * <p>
         *     This method return a builder; when the builder finishes,
         *     a binding is created and added to the local binding list of the current thread.
         * </p>
         * <p>
         *     Example usage:
         * </p>
         * <pre>
         *     OD.Local.bind(Foo.class).to(foo);
         * </pre>
         * @see #bind(Class)
         */
        static public <T> BindingBuilder<T> bind(ClassType<T> type){ return new BindingBuilder<T>(true, type); }

        /**
         * Add the binding to the local binding list of the current thread.
         */
        static public void bind(Binding binding) { LocalBindings.addLocal(binding); }

        // bulk get/set local bindings.
        // convention: whoever added a binding is responsible to remove it.
        // this is done by first backup the list, then later restore the list.
        // we use List<Binding> instead of an opaque data structure, to allow more sophisticated manipulations.

        /**
         * Get the local binding list of the current thread.
         * <p>
         *     The returned list is immutable. Typically it will be used later in
         *     {@link #setBindings(List)}. For example
         * </p>
         * <pre>
         *     List&lt;Binding&gt; b0 = OD.Local.getBindings();  // save local bindings
         *     try
         *     {
         *        ...
         *        OD.Local.bind(Foo.class).to(foo);  // change local bindings
         *        ...
         *     }
         *     finally
         *     {
         *         OD.Local.setBindings(b0); // restore local bindings
         *     }
         * </pre>
         * <p>
         *     It is ok to get the binding list from one thread and set it to another thread;
         *     this is useful for migrating a task and its context between threads.
         * </p>
         */
        static public List<Binding> getBindings() { return LocalBindings.getSnapshot(); }

        /**
         * Set the local binding list for the current thread.
         * <p>
         *     The list is typically from a previous {@link #getBindings()} call;
         *     but it can also be any list of bindings.
         * </p>
         * <p>
         *     If `bindings` is null or empty, the local bindings of the current thread will be cleared.
         * </p>
         */
        static public void setBindings(List<Binding> bindings){ LocalBindings.setAll(bindings); }
        // user can set with any list; allowing sophisticated manipulation of local bindings.

    }

    static final Predicate<Object[]> NO_TAG = new Predicate<Object[]>()
    {
        @Override
        public boolean test(Object[] tags)
        {
            return tags.length==0;
        }

        @Override
        public String toString()
        {
            return "[]";
        }
    };


    /**
     * Builder for creating bindings.
     * <p>
     *     A BindingBuilder is started by
     *     {@link OD#bind(ClassType)} or {@link OD.Local#bind(ClassType)},
     *     finished by one of the <code>to(...)</code> methods, for example,
     * </p>
     * <pre>
     *     OD.bind(Foo.class).to(foo);
     *
     *     OD.Local.bind(Bar.class).to(BarImpl::new);
     * </pre>
     * <p>
     *     The <code>to(...)</code> method creates a Binding, adds it to
     *     the global or local binding list, depending how the builder was started.
     * </p>
     * <p>
     *     <b>Tags</b> -
     *     By default, the binding exact-matches empty tags.
     *     You can specify exact-matching of tags by {@link #tags(Object...)}
     * </p>
     * <pre>
     *     OD.bind(Foo.class).tags(tag1, tag2).to(foo);
     *     ...
     *     OD.get(Foo.class, tag1, tag2);   // sees `foo`
     * </pre>
     * <p>
     *     See also {@link #tagsMatch(OD.Predicate)} for arbitrary tag matching algorithm.
     * </p>
     *
     */
    static public final class BindingBuilder<T>
    {
        boolean local;
        ClassType<T> type;
        Predicate<Object[]> tagMatcher = NO_TAG;

        BindingBuilder(boolean local, ClassType<T> type)
        {
            this.local = local;
            this.type = type;
        }


        /**
         * Specify exact-matching tags.
         * <p>
         *     This method is equivalent to {@link #tagsMatch(OD.Predicate)}
         *     with a predicate that matches exactly the given `tags`.
         * </p>
         * <p>
         *     Exact-match of two objects are defined by {@link Object#equals(Object)};
         *     also, `null` exact-matches `null`.
         * </p>
         * @return `this` for method chaining
         */
        public BindingBuilder<T> tags(Object... tags)
        {
            if(tags==null) throw new IllegalArgumentException("tags==null");

            return tagsMatch(new ExactTagMatch(tags));
        }

        /**
         * Specify how tags will be matched.
         * <p>
         *     The tags in `OD.get(type,tags)` will be fed to `predicate`
         *     to check whether they match this binding.
         *     For example, to specify that the binding matches any lookup tags
         * </p>
         * <pre>
         *     OD.bind(Foo.class).tagsMatch(tags-&gt;true).to(foo);
         * </pre>
         * @return `this` for method chaining
         */
        public BindingBuilder<T> tagsMatch(Predicate<Object[]> predicate)
        {
            this.tagMatcher =predicate;
            return this;
        }

        // overload ambiguity to(null) - use to((Foo)null) instead

        /**
         * Create a Binding to a single instance.
         * <p>
         *     This method is equivalent to {@link #to(OD.Supplier)}
         *     with a supplier that always returns `instance`.
         * </p>
         *
         * @return the Binding created
         */
        public Binding to(T instance)
        {
            return finish( new InstanceBinding<T>(type, tagMatcher, instance));
            // while we could simply forward to `to( ()->instance )`,
            // we want a named class with descriptive toString(), for diagnosis.
        }
        /**
         * Create a Binding to a supplier of T.
         * <p>
         *     The supplier will be invoke for every `OD.get()` call
         *     that matches this binding. For example,
         * </p>
         * <pre>
         *     OD.bind(Foo.class).to(FooImpl::new);
         * </pre>
         * @return the Binding created
         */
        public Binding to(Supplier<? extends T> supplier)
        {
            return finish( new SupplierBinding<T>(type, tagMatcher, supplier));
        }
        /**
         * Create a Binding to an implementation class of T.
         * <p>
         *     In simple cases, the zero-arg constructor of `implClass` will be invoked
         *     for each `OD.get()`. For example
         * </p>
         * <pre>
         *     OD.bind(Foo.class).to(FooImpl.class);
         *
         *     OD.get(Foo.class); // returns `new FooImpl()`
         * </pre>
         * <p>
         *     <b>Type Argument</b>
         * </p>
         * <p>
         *     If `implClass` is generic, it might want to know the exact type arguments upon instantiation.
         *     This can be done by a constructor that accepts type arguments. For example
         * </p>
         * <pre>
         *     public class FooImpl&lt;T&gt; implements Foo&lt;T&gt;
         *     {
         *         public FooImpl(Class&lt;T&gt; classT)
         *         {
         *             ...
         * </pre>
         * <p>
         *     If we `bind(Foo.class).to(FooImpl.class)`,
         *     the binding will match a lookup for `Foo&lt;Bar&gt;`,
         *     and the constructor `FooImpl(Class)` will be invoked with `Bar` class.
         * </p>
         * <p>
         *     More generally,
         *     suppose `implClass` has n type parameters &lt;T1, ..., Tn&gt;,
         *     the constructor can have any number of arguments; each argument type must be one of
         *     {@link java.lang.Class Class&lt;X&gt;},
         *     {@link bayou.jtype.ClassType ClassType&lt;X&gt;},
         *     <!-- {@link bayou.jtype.ArrayType ArrayType&lt;X&gt;}, --><!-- omit it for now; a little confusing -->
         *     or
         *     {@link bayou.jtype.ReferenceType ReferenceType&lt;X&gt;},
         *     where `X` must be one of T1...Tn. For example,
         * </p>
         * <pre>
         *     public class BarImpl&lt;K, V&gt;  implements Bar&lt;Map&lt;K, List&lt;V&gt;&gt;&gt;
         *     {
         *         public BarImpl(ClassType&lt;V&gt; typeV, Class&lt;K&gt; clazzK)
         *
         * </pre>
         * @return the Binding created
         */
        public Binding to(Class<? extends T> implClass)
        {
            return finish( ImplClassBinding.of(type, tagMatcher, implClass));
        }

        Binding _toSupplierClass(Class<? extends Supplier> supplierClass)
        {
            return finish(SupplierClassBinding.of(type, tagMatcher, supplierClass));
        }

        Binding finish(Binding binding)
        {
            if(local)
                LocalBindings.addLocal(binding);
            else
                GlobalBindings.addGlobal(binding);
            return binding;
        }
    }


    //=============================================================================// misc impls

    // used as cache keys
    static class TypeAndTags<T>
    {
        final ClassType<T> type;
        final Object[] tags;
        final int hashCode;
        TypeAndTags(ClassType<T> type, Object[] tags)
        {
            this.type = type;
            // not cloning `tags`; that's fine.
            this.tags = tags;
            // this object is to be used as key in maps. calc its hash eagerly.
            this.hashCode = type.hashCode() + 31 * Arrays.hashCode(tags);
        }

        public int hashCode()
        {
            return hashCode;
        }
        public boolean equals(Object obj)
        {
            if(!(obj instanceof TypeAndTags))
                return false;
            TypeAndTags that = (TypeAndTags)obj;
            return this.type.equals(that.type)
                && Arrays.equals(this.tags, that.tags);
        }
        public String toString()
        {
            return "OD.TypeAndTags("+type.toString(false)+", "+Arrays.toString(tags)+")";
        }
    }




    // usually for bindings with a min applicable [typeB, tagsB].  a query [typeQ, tagsQ] is applicable if
    //     Q.class=B.class and Q :> B
    static boolean match(ClassType typeBind, ClassType typeQuery, Predicate<Object[]> tagMatcher, Object[] tagsQuery)
    {
        return typeBind.getTheClass()==typeQuery.getTheClass()
            && tagMatcher.test(tagsQuery)
            && TypeMath.isSubType(typeBind, typeQuery); // more expensive, checked last
    }

    static <T> Supplier<T> findSupplier(List<Binding> bindings, ClassType<T> type, Object[] tags)
    {
        for(int i=bindings.size()-1; i>=0; i--)
        {
            Binding binding = bindings.get(i);
            Supplier<? extends T> supplier = binding.map(type, tags); // throws
            if(supplier !=null)
                return cast(supplier);
        }
        return null;
    }
    static <T> void findSuppliers(ArrayList<Supplier<T>> suppliers,
                                  List<Binding> bindings, ClassType<T> type, Object[] tags)
    {
        for(Binding binding : bindings)
        {
            Supplier<T> supplier = cast(binding.map(type, tags)); // throws
            if(supplier !=null)
                suppliers.add(supplier);
        }
    }

    // caller makes sure that the cast is safe
    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj)
    {
        return (T)obj;
    }

}
