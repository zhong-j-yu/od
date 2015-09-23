package bayou.jtype;

import _bayou._tmp._Array2ReadOnlyList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Intersection type.
 * <p>
 *     An intersection type takes the form
 *     <code> T1 &amp; .. &amp; Tn </code> ;
 *     we call <code>T1,...Tn</code> the super types of the intersection type.
 * </p>
 */

// this impl will flatten intersections, i.e. no Ti is an IntersectionType.
// this impl doesn't do simplification; the list of T1..Tn is preserved as given.
final public class IntersectionType<T> extends ReferenceType<T>
{
    final List<ReferenceType<?>> superTypes;
    IntersectionType(List<ReferenceType<?>> superTypes)
    {
        ArrayList<ReferenceType<?>> flat = new ArrayList<ReferenceType<?>>();
        flattenIntersection(superTypes, flat);
        this.superTypes = Collections.unmodifiableList(flat);
    }

    /**
     * Create an intersection type.
     */
    public IntersectionType(ReferenceType<?>... superTypes)
    {
        this(new _Array2ReadOnlyList<ReferenceType<?>>(superTypes));
    }

    /**
     * Get the super types of the intersection.
     */
    public List<ReferenceType<?>> getSuperTypes()
    {
        return superTypes;
    }

    int genHash()
    {
        return IntersectionType.class.hashCode() + 31 * superTypes.hashCode();
    }
    // syntactic hashCode/equals. it's unlikely they'll be needed by anyone.
    boolean eqX(Object obj) { return this.equals((IntersectionType<?>)obj); }
    boolean equals(IntersectionType<?> that)
    {
        // syntactic comparison
        return eq(this.superTypes, that.superTypes);
    }

    /**
     * A textual description  of the type.
     * <p>
     *     Example: <code>"Foo &amp; Runnable"</code>
     * </p>
     */
    public String toString(boolean full)
    {
        if(superTypes.size()==0) // uh?
            return ClassType.OBJECT.toString(full);

        int i=0;
        StringBuilder sb = new StringBuilder();
        for(ReferenceType<?> type : superTypes)
        {
            if(i>0)
                sb.append(" & ");
            sb.append( type.toString(full) );
            i++;
        }
        return sb.toString();
    }

    static void flattenIntersection(List<ReferenceType<?>> intersection, List<ReferenceType<?>> result)
    {
        for(ReferenceType<?> type : intersection)
        {
            if(type instanceof IntersectionType)
                flattenIntersection(((IntersectionType<?>) type).superTypes, result);
            else
                result.add(type);
        }
    }

}
