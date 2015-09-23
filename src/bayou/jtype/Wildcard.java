package bayou.jtype;

/**
 * Wildcard.
 * <p>
 *     A wildcard is a type argument, see {@link TypeArg}.
 * </p>
 * <p>
 *     See static convenience methods for creating `Wildcard` objects.
 * </p>
 * <p>
 *     Note: a wildcard is *not* a type.
 * </p>
 */
final public class Wildcard implements TypeArg
{
    /**
     * Create a wildcard with an upper bound.
     * <p>
     *     This method is equivalent to
     *     {@link #extends_(ReferenceType) extends_(ClassType.of(upperBound))}
     * </p>
     * <p>
     *     Example
     * </p>
     * <pre>
     *     TypeArg V = Wildcard.extends_(Number.class);  //  ? extends Number
     * </pre>
     */
    static public Wildcard extends_(Class<?> upperBound)
    {
        return extends_(ClassType.of(upperBound));
    }
    /**
     * Create a wildcard with an upper bound.
     * <p>
     *     The lower bound is the null type.
     * </p>
     */
    static public Wildcard extends_(ReferenceType<?> upperBound)
    {
        return new Wildcard(upperBound, NullType.INSTANCE);
    }

    /**
     * Create a wildcard with a lower bound.
     * <p>
     *     This method is equivalent to
     *     {@link #super_(ReferenceType) super_(ClassType.of(lowerBound))}
     * </p>
     * <p>
     *     Example
     * </p>
     * <pre>
     *     TypeArg V = Wildcard.super_(Number.class);  //  ? super Number
     * </pre>
     */
    static public Wildcard super_(Class<?> lowerBound)
    {
        return super_(ClassType.of(lowerBound));
    }
    /**
     * Create a wildcard with a lower bound.
     * <p>
     *     The upper bound is the <code>Object</code> type.
     * </p>
     */
    static public Wildcard super_(ReferenceType<?> lowerBound)
    {
        return new Wildcard(ClassType.OBJECT, lowerBound);
    }

    /**
     * Create an unbounded wildcard.
     * <p>
     *     The upper bound is `Object`, and the lower bound is the null type.
     * </p>
     * <p>
     *     Example
     * </p>
     * <pre>
     *     ClassType.of(List.class, Wildcard.unbounded());  //  List&lt;?&gt;
     * </pre>
     */
    static public Wildcard unbounded()
    {
        return new Wildcard(ClassType.OBJECT, NullType.INSTANCE);
    }

    final ReferenceType<?> upperBound;
    final ReferenceType<?> lowerBound;

    /**
     * Create a wildcard with bounds.
     * <p>
     *     `upperBound` must be non-null; it can be the {@link ClassType#OBJECT Object type}.
     * </p>
     * <p>
     *     `lowerBound` must be non-null; it can be the {@link NullType#INSTANCE null type}.
     * </p>
     */
    public Wildcard(ReferenceType<?> upperBound, ReferenceType<?> lowerBound)
    {
        if( upperBound==null ) throw new IllegalArgumentException("upperBound==null");
        if( lowerBound==null ) throw new IllegalArgumentException("lowerBound==null");

        this.upperBound = upperBound;
        this.lowerBound = lowerBound;

        // no well-formed-ness for wildcard; check it after capture conversion.
    }

    /**
     * Get the upper bound.
     */
    public ReferenceType<?> getUpperBound()
    {
        return upperBound;
    }
    /**
     * Get the lower bound.
     */
    public ReferenceType<?> getLowerBound()
    {
        return lowerBound;
    }

    int hashCode = 0;

    public int hashCode()
    {
        int h = hashCode;
        if(h==0)
            hashCode = h = genHash();
        return h;
    }

    int genHash()
    {
        int hash = Wildcard.class.hashCode();
        hash = 31*hash + upperBound.hashCode();
        hash = 31*hash + lowerBound.hashCode();
        return hash;
    }

    /**
     * Whether this wildcard is equal to another wildcard.
     * <p>
     *     Two wildcards are equal if their bounds are equal.
     * </p>
     */
    public boolean equals(Object obj)
    {
        return this==obj ||
            (obj instanceof Wildcard) && this.equals( (Wildcard)obj );
    }
    boolean equals(Wildcard that)
    {
        return this.upperBound.equals(that.upperBound)
            && this.lowerBound.equals(that.lowerBound);
    }

    /**
     * A textual description of the wildcard.
     * <p>
     *     If `full==false`, simpler names are used for bound types.
     * </p>
     */
    public String toString(boolean full)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("?");
        if(! upperBound.isObject() )
            sb.append(" extends ").append(upperBound.toString(full));
        if(!(lowerBound instanceof NullType))
            sb.append(" super "  ).append(lowerBound.toString(full));
        return sb.toString();
    }

    /**
     * A textual description  of the wildcard.
     * <p>
     *     This method is equivalent to {@link #toString(boolean) toString(true)}.
     * </p>
     */
    public String toString(){ return toString(true); }

}
