/**
 * A simple library for Service Locator pattern.
 * <p>
 *     This library contains bind and lookup operations, based on (type,tags). For example
 * </p>
 * <pre>
 *
 *     OD.bind(Foo.class).to(foo);
 *     ...
 *     OD.get(Foo.class);  // lookup
 * </pre>
 * <p>
 *     There is a global binding list, which is totally ordered;
 *     multiple bindings may match a lookup, the latest binding has priority.
 * </p>
 * <p>
 *     Each thread also contains a local binding list;
 *     local bindings are only visible to lookups in the same thread.
 *     Local bindings have priority over global bindings.
 * </p>
 * <pre>
 *
 *     OD.Local.bind(Foo.class).to(foo2);
 *
 *     // later in the same thread
 *     OD.get(Foo.class); // sees `foo2`
 * </pre>
 * <p>
 *     Type in (type,tags) can be generic; see {@link bayou.jtype.ClassType}.
 * </p>
 * <p>
 *     Tags in (type,tags) can be arbitrary objects, including `null`. For example
 * </p>
 * <pre>
 *     OD.bind(Foo.class).tags("x", 1).to(x1);
 *
 *     OD.get(Foo.class, "x", 1); // sees `x1`
 * </pre>
 */
package bayou.od;