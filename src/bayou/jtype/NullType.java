package bayou.jtype;

/**
 * The null type.
 * <p>
 *     There is a single instance of NullType : {@link NullType#INSTANCE NullType.INSTANCE}.
 * </p>
 */
final public class NullType extends ReferenceType<Object>
{
    /**
     * The singleton of NullType.
     */
    static public final NullType INSTANCE = new NullType();

    private NullType(){}

    int genHash()
    {
        return NullType.class.hashCode();
    }


    /**
     * Return true iff `obj` is also NullType.
     */
    public boolean equals(Object obj){ return this==obj; }
    boolean eqX(Object obj){ return this==obj; }

    /**
     * Return "Null".
     */
    public String toString(boolean full)
    {
        return "Null";
    }
}
