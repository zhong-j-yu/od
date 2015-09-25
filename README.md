# OD

**`OD`** is a simple *Service Locator* library.


### Basic Usage

To lookup an object of a certain type, use `OD.get(type)`

      Foo foo = OD.get( Foo.class );

To bind the type to an object or a supplier, use `OD.bind(type)`

      OD.bind( Foo.class ).to( someInstance );  // bind to a singleton

      OD.bind( Foo.class ).to( FooImpl::new );  // bind to a supplier


----

**Document** - <http://zhong-j-yu.github.io/od/>

**Download** - [od-1.0.0.jar](http://zhong-j-yu.github.io/od/1.0.0/od-1.0.0.jar)

**Java version** - 6+

**License** - Public Domain

**Discussion** - https://groups.google.com/forum/#!forum/od-service-locator


