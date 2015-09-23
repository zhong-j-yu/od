package bayou.jtype;

/**
 * Type argument.
 * <p>
 *     A type argument is either a {@link bayou.jtype.ReferenceType} or a {@link bayou.jtype.Wildcard}.
 * </p>
 * <p>
 *     For example
 * </p>
 * <pre>
 *    TypeArg K = ClassType.of(String.class);
 *    TypeArg V = Wildcard.extends_(Number.class);
 *
 *    JavaType&lt;?&gt; type = ClassType.of(Map.class, K, V); // Map&lt;String, ? extends Number&gt;
 * </pre>
 */
// no out-of-package subclass (tho we can't enforce that)
public interface TypeArg
{

    /**
     * A textual description  of the type argument.
     * <p>
     *     If `full==false`, simpler names are used.
     * </p>
     * <p>
     *     See also {@link JavaType#toString(boolean)}.
     * </p>
     */
    abstract public String toString(boolean full);

}
