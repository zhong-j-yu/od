package bayou.jtype;

/**
 * Any Java type.
 * <p>
 *     The hierarchy of types:
 * </p>
 * <pre>
 *     {@link bayou.jtype.JavaType}
 *         {@link bayou.jtype.PrimitiveType}
 *         {@link bayou.jtype.ReferenceType}
 *             {@link bayou.jtype.ClassType}
 *             {@link bayou.jtype.ArrayType}
 *             {@link bayou.jtype.TypeVar}
 *             {@link bayou.jtype.IntersectionType}
 *             {@link bayou.jtype.NullType}
 * </pre>
 * <p>
 *     Example usage:
 * </p>
 * <pre>
 *     JavaType&lt;String&gt; typeString = ClassType.of(String.class);
 * </pre>
 */

// ref: Daniel Smith 2007, Completing the Java Type System
// you can't subclass this class; all subclasses are defined in the same package
public abstract class JavaType<T>
{
    JavaType(){}  // forbid out-of-package subclass

    abstract int genHash();

    // obj is the same class as this class.
    abstract boolean eqX(Object obj);

    // does this type represents java.lang.Object
    boolean isObject(){ return false; }


    /**
     * A textual description  of the type.
     * <p>
     *     If `full==false`, simpler names are used for types.
     * </p>
     * <p>
     *     For example:
     * </p>
     * <pre>
     *    ClassType&lt; List&lt;String&gt; &gt; type = ClassType.of(List.class, String.class);
     *
     *    type.toString(true);    //  "java.util.List&lt;java.lang.String&gt;"
     *    type.toString(false);   //  "List&lt;String&gt;"
     * </pre>
     */
    abstract public String toString(boolean full);


    // hashCode and equals:
    // only check syntactically; two types A,B may be equivalent, i.e. A<:B and B<:A
    // but they can have diff hash codes, and A.equals(B) can be false.
    // still useful, e.g. in a map keyed by types, and it's ok to contain both A and B.
    // for TypeVar, syntactic identity matters in most app; it's possible for 2 distinct
    // vars T1 T2 (by capture conversion), T1<:T2 and T2<:T1, but they aren't interchangeable.

    int hashCode = 0;
    public int hashCode()
    {
        int h = hashCode;
        if(h==0)
            hashCode = h = genHash();
        return h;
    }

    /**
     * Whether this type is equal to another type.
     * <p>
     *     Equality is only tested syntactically.
     *     It is possible that
     *     type A,B are equivalent, e.g. `A&lt;:B and B&lt;:A`, yet `A.equals(B)==false`.
     * </p>
     * <p>
     *     To check equivalency through mutual subtyping,
     *     use {@link TypeMath#isSubType(ReferenceType, ReferenceType) TypeMath.isSubType}.
     * </p>
     */
    public boolean equals(Object obj)
    {
        return this == obj ||
            obj != null
            && this.getClass() == obj.getClass()
            && this.eqX(obj);
    }

    /**
     * A textual description  of the type.
     * <p>
     *     This method is equivalent to {@link #toString(boolean) toString(true)}.
     * </p>
     */
    public String toString(){ return toString(true); }

    static <T> boolean eq(T a, T b){ return a.equals(b); }

    /** to simple string */
    final String ss(){ return toString(false); }

    ////////////////////////////////////////////////////////////////////////


    // wildcard is not really a java type; don't pass a java.lang.reflect.WildcardType to this method.

    /**
     * Convert a {@link java.lang.reflect.Type java.lang.reflect.Type} to
     * {@link bayou.jtype.JavaType bayou.jtype.JavaType}
     * <p>
     *     This method does not accept {@link java.lang.reflect.WildcardType java.lang.reflect.WildcardType},
     *     because a wildcard is not a type.
     * </p>
     */
    static public <T> JavaType<T> convertFrom(java.lang.reflect.Type jlrType)
    {
        return TypeMath.convertType(jlrType);
    }



}
