# OD

**`OD`** is a simple Service Locator library.


### Basic Usage

To lookup an object of a certain type, use `OD.get(type)`

    Foo foo = OD.get( Foo.class );

To bind the type to an object or a supplier, use `OD.bind(type)`

    OD.bind( Foo.class ).to( fooInstance );   // bind to a singleton

    OD.bind( Foo.class ).to( FooImpl::new );  // bind a supplier

See full document at <http://zhong-j-yu.github.io/od/1.0.0/>

----

Download: [od-1.0.0.jar](http://zhong-j-yu.github.io/od/1.0.0/od-1.0.0.jar)

Java: 6+

License: Public Domain

Discussion: https://groups.google.com/forum/#!forum/od-service-locator


