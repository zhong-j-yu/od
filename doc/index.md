# OD

**`OD`** is a simple *Service Locator* library.

Why service locator?
While *Dependency Injection* is great, it might be too heavy handed for some applications.



`OD` works with Java 6+; it is released to Public Domain.

[\[ od-1.0.0.jar \]](od-1.0.0.jar)
&nbsp;&nbsp;&nbsp;&nbsp;
[\[ javadoc \]](javadoc/)
&nbsp;&nbsp;&nbsp;&nbsp;
[\[ github repo \]](https://github.com/zhong-j-yu/od)
&nbsp;&nbsp;&nbsp;&nbsp;
[\[ google group \]](https://groups.google.com/forum/#!forum/od-service-locator)

=TOC=

## Lookup and Binding

To lookup an object of a certain type, call `OD.get(type)`

    Foo foo = OD.get( Foo.class );

Prior to the lookup, typically during the startup phase,
the application needs to bind the type to a supplier.
For example

    OD.bind( Foo.class ).to( ()->new FooImpl() );   //  or, `to(FooImpl::new)`

    OD.bind( Foo.class ).to( FooImpl.class );  // if lambda is not available

The supplier will be invoked for every call of `OD.get(Foo.class)`.

To bind `Foo` to a singleton

    OD.bind( Foo.class ).to( new FooImpl() );


## Tags

If type alone is not sufficient as the lookup key, `tags` can be added as qualifiers.
Tags can be an arbitrary array of Objects. For example

    OD.bind(Foo.class).tags("bar", 1).to(foo1);

    ...
    OD.get(Foo.class, "bar", 1);  // foo1




## Local Binding

A *local binding* is only visible to the current thread

    OD.Local.bind( Foo.class ).to( someFoo );

    // later in the same thread
    OD.get(Foo.class);  // someFoo

Local bindings can be saved and restored

    List<OD.Binding> prev = OD.Local.getBindings();
    ...
    OD.Local.setBindings(prev); // restore bindings



## Search OD usage




## Generics
wildcard



## Binding List

There is a global binding list, populated by `OD.bind()`;
there is a local binding list for every thread, populate by `OD.Local.bind()`.

Every lookup first checks the local bindings, then the global bindings.

last wins




## Custom Binding

    OD.Binding binding = (type,tags)->{...}

Tags are intended to be used as limited lookup keys,
not arbitrary construction parameters. For example, instead of

    OD.get(Shoe.class, size, color)  // get a Shoe of size and color

it's better to design it with a factory that takes the parameters

    OD.get(ShoeMaker.class).make(size, color);





## Template Binding

## Why OD