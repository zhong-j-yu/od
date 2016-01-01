# OD

**`OD`** is a simple *Service Locator* library for Java 6+.

While the Service Locator pattern is widely discredited,
it may still have merits in some situations; see article
[*In Defense of Service Locator*](http://bayou.io/draft/In_Defense_of_Service_Locator.html).
We created this library for people who may consider using Service Locator in their applications.



[\[ od-1.0.0.jar \]](od-1.0.0.jar)
&nbsp;&nbsp;&nbsp;&nbsp;
[\[ javadoc \]](javadoc/)
&nbsp;&nbsp;&nbsp;&nbsp;
[\[ github repo \]](https://github.com/zhong-j-yu/od)
&nbsp;&nbsp;&nbsp;&nbsp;
[\[ google group \]](https://groups.google.com/forum/#!forum/od-service-locator)

=TOC=

## Lookup and Binding

To lookup an object of a certain type, call [`OD.get(type)`](javadoc/bayou/od/OD.html#get-bayou.jtype.ClassType-java.lang.Object...-)

    Foo foo = OD.get( Foo.class );

Prior to the lookup, typically during the startup phase,
the application needs to bind the type to a [supplier](javadoc/bayou/od/OD.BindingBuilder.html#to-bayou.od.OD.Supplier-).
For example

    OD.bind( Foo.class ).to( ()->new FooImpl() );   //  or, `to(FooImpl::new)`

    OD.bind( Foo.class ).to( FooImpl.class );  // if lambda is not available

The supplier will be invoked for every call of `OD.get(Foo.class)`.

To bind `Foo` to a singleton

    OD.bind( Foo.class ).to( new FooImpl() );


## Tags

If `type` alone is not sufficient as lookup key,
[`tags`](javadoc/bayou/od/OD.BindingBuilder.html#tags-java.lang.Object...-) can be added as qualifiers.
Tags can be an arbitrary array of `Object`s, for example

    OD.bind(Integer.class).tags("bar", 1).to(42);

    ...
    OD.get(Integer.class, "bar", 1);  // returns `42`




## Local Binding

A *local binding* is only visible to the current thread

    OD.Local.bind( Foo.class ).to( someFoo );

    // later in the same thread
    OD.get(Foo.class);  // someFoo

Local bindings can be saved and restored

    List<OD.Binding> prev = OD.Local.getBindings();
    ...
    OD.Local.setBindings(prev); // restore bindings






## Generics

Generic class or interface types can be used in lookup/binding;
these types are represented by [`ClassType`](javadoc/bayou/jtype/ClassType.html).


For example, to bind and look up on a generic type `List<Integer>`

        OD.bind( new ClassType< List<Integer> >(){} ).to( Arrays.asList(7,8,9) );
        // type literal         -------------

        OD.get( ClassType.of(List.class, Integer.class) );
        // type factory      ----        -------

See package [`bayou.jtype`](javadoc/bayou/jtype/package-summary.html) for type representations in our library.



### Wildcard

To avoid confusion,
we do not automatically extend bindings to *super classes/interfaces*.
For example, a binding of `Integer` does not imply a binding of `Number`.
If you do need that, you can manually add multiple bindings.

On the other hand, we do extend bindings to *super types* through
[wildcards](http://bayou.io/draft/Capturing_Wildcards.html#Wild_Type);
therefore, a binding of `List<Integer>` can be looked up by `List<? extends Number>`.

    OD.get( new ClassType< List<? extends Number> >(){} );
                           ----------------------


## Binding List

Now, let's give a formal description of how **`OD`** works.

A [`Binding`](javadoc/bayou/od/OD.Binding.html)
is an arbitrary mapping of `(type,tags)->supplier`.
A binding is *applicable* to a particular `(type,tags)` if it's mapped to a non-null supplier.

There is a *global* binding list, populated by [`OD.bind()`](javadoc/bayou/od/OD.html#bind-bayou.od.OD.Binding-).

There is a *local* binding list per thread, managed by [`OD.Local`](http://localhost:8080/od/1.0.0/javadoc/bayou/od/OD.Local.html).

For each lookup on `(type,tags)`, the local binding list is checked first;
if no applicable local binding is found, the global binding list is checked.

A binding list is ordered;
if there are multiple bindings applicable to a `(type,tags)`,
the latest one is used.



## Custom Binding

You can create a custom [`Binding`](javadoc/bayou/od/OD.Binding.html)
that implements arbitrary binding strategy.  For example,

        // bind every class to its no-arg constructor, if there's one.

        OD.Binding defaultBinding = new OD.Binding()
        {
            @Override
            public <T> OD.Supplier<? extends T> map(ClassType<T> type, Object... tags)
            {
                Constructor<T> constructor;
                try {
                    constructor = type.getTheClass().getConstructor();
                } catch (Exception e) {
                    return null;
                }

                return ()->
                {
                    try {
                        return constructor.newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
            }

            @Override
            public Set<? extends Class> getApplicableClasses()
            {
                return null;  // `null` means set of all classes
            }
        };

        OD.bind(defaultBinding);



### Tags

Tags are not intended to be arbitrary construction parameters,
even though you could do that in a custom binding.

Tags are intended to be a limited set of lookup keys;
typically, they should be compile time constants.

For example, instead of doing something like

    OD.get(Shoe.class, size, color)  // get a Shoe of any size and color

it's better to redesign it with a factory that takes construction parameters

    OD.get(ShoeMaker.class).make(size, color);





## Template Binding

When we do a binding like

    OD.bind(Dao.class).to(MyDao.class);

where `Dao` and `MyDao` are generic

    public class MyDao<T> implements Dao<T>
    {
        ...

the binding is actually equivalent to multiple bindings of
`Dao<t>` to `MyDao<t>`, for every type `t`.
Therefore, if we do a lookup on `Dao<Cat>`, we'll get a `new MyDao<Cat>()`.

### Type args

The constructor of `MyDao` can accept parameters that represent the type arguments of the generic class

    public class MyDao<T> implements Dao<T>
    {
        public MyDao(Class<T> classT)
        {
            //System.out.println(classT);

When we do a lookup on `Dao<Cat>`, we'll get a `new MyDao<Cat>(Cat.class)`.

For more details, see javadoc of [`bind(type).to(implClass)`](javadoc/bayou/od/OD.BindingBuilder.html#to-java.lang.Class-).


See also source code of
[`TypeArgConstructor`](https://github.com/zhong-j-yu/od/blob/master/src/bayou/od/TypeArgConstructor.java)
and [`ImplClassBinding`](https://github.com/zhong-j-yu/od/blob/master/src/bayou/od/ImplClassBinding.java).



&nbsp;

&nbsp;

----

<center><strong><code>OD</code></strong></center>

&nbsp;

&nbsp;

