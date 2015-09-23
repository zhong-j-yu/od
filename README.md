# OD

**`OD`** is a small library for the Service Locator pattern.

`OD` supports Java 1.6 and up.

public domain

Download jar

Javadoc

google group

document

## Basic

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


See document