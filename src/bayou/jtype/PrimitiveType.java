package bayou.jtype;

/**
 * Primitive type.
 * <p>
 *     <code>`T`</code> is the boxed type of the primitive type, for example
 * </p>
 * <pre>
 *     PrimitiveType&lt;Boolean&gt; type_boolean = PrimitiveType.of(boolean.class);
 * </pre>
 */
final public class PrimitiveType<T> extends JavaType<T>
{
    // the Class object representing the primitive type, e.g. boolean.class.
    final Class<T> clazz;

    PrimitiveType(Class<T> clazz)
    {
        if(!clazz.isPrimitive())
            throw new IllegalArgumentException("clazz is not primitive: "+clazz);
        this.clazz = clazz;
    }

    /**
     * Return the PrimitiveType representing the primitive type.
     * <p>
     *     For example
     * </p>
     * <pre>
     *     PrimitiveType&lt;Boolean&gt; type_boolean = PrimitiveType.of(boolean.class);
     * </pre>
     *
     */
    public static <T> PrimitiveType<T> of(Class<T> clazz)
    {
        return new PrimitiveType<T>(clazz);
    }

    /**
     * The Class representing the primitive type, for example `boolean.class`.
     */
    public Class<T> getTheClass()
    {
        return clazz;
    }

    int genHash()
    {
        return clazz.hashCode();
    }

    boolean eqX(Object obj){ return this.equals((PrimitiveType<?>)obj); }
    boolean equals(PrimitiveType<?> that)
    {
        return eq(this.clazz, that.clazz);
    }

    /**
     * A textual description  of the type.
     * <p>
     *     The name of the primitive type is returned, e.g. "boolean".
     * </p>
     */
    public String toString(boolean full)
    {
        return clazz.getName();
    }

}
