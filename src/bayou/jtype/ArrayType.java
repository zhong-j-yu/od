package bayou.jtype;

/**
 * Array type.
 * <p>
 *     For example:
 * </p>
 * <pre>
 *     ArrayType&lt; String[] &gt; arrayString = new ArrayType&lt;&gt;( ClassType.of(String.class) );
 *
 *     ArrayType&lt; int[] &gt; array_int = new ArrayType&lt;&gt;( PrimitiveType.of(int.class) );
 * </pre>
 * <p>
 *     Note that `T` represents the array type, not the component type (which can be primitive).
 * </p>
 */

// represents an array type E[]. T=E[]. E can be primitive. We can't express that T must be array. Users beware.
final public class ArrayType<T> extends ReferenceType< T >
{

    final JavaType<?> componentType;

    /**
     * Create an array type for the component type.
     * <p>
     *     For example:
     * </p>
     * <pre>
     *     ArrayType&lt; String[] &gt; arrayString = new ArrayType&lt;&gt;( ClassType.of(String.class) );
     *
     *     ArrayType&lt; int[] &gt; array_int = new ArrayType&lt;&gt;( PrimitiveType.of(int.class) );
     * </pre>
     *
     */
    public ArrayType(JavaType<?> componentType)
    {
        this.componentType = componentType;
    }

    /**
     * Get the component type of the array.
     */
    public JavaType<?> getComponentType()
    {
        return componentType;
    }

    int genHash()
    {
        return ArrayType.class.hashCode()
            + 31 * componentType.hashCode();
    }

    boolean eqX(Object obj){ return this.equals((ArrayType<?>)obj); }
    boolean equals(ArrayType<?> that)
    {
        return eq(this.componentType, that.componentType);
    }

    /**
     * A textual description  of the array type.
     *
     * <p>
     *     Examples: <code>"int[]", "java.lang.String[]"</code>
     * </p>
     */
    public String toString(boolean full)
    {
        return componentType.toString(full) + "[]";
    }
}
