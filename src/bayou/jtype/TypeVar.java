package bayou.jtype;

import java.lang.reflect.GenericDeclaration;

/**
 * Type variable.
 * <p>
 *     A type variable may be
 * </p>
 * <ul>
 *     <li>declared by a generic class/interface or a generic method/constructor</li>
 *     <li>created by {@link TypeMath#doCaptureConversion(ClassType) capture conversion}</li>
 * </ul>
 */
public abstract class TypeVar<T> extends ReferenceType<T>
{
    TypeVar(){} // forbid out-of-package subclass

    /**
     * Get the name of the type variable.
     * <p>
     *     For example: "T".
     * </p>
     */
    abstract public String getName();

    // where the var is declared, class/method/constructor.
    // could be null if this type var is created by capture conversion

    /**
     * Where the type variable is declared.
     * <p>
     *     The declaration site could be a generic class/interface or a generic method/constructor.
     * </p>
     * <p>
     *     This method returns null if this type variable is created by
     *     {@link TypeMath#doCaptureConversion(ClassType) capture conversion}.
     * </p>
     */
    abstract public GenericDeclaration getDeclaringSite();

    /**
     * The upper bound of the type variable.
     */
    abstract public ReferenceType<?> getUpperBound();

    /**
     * The lower bound of the type variable.
     * <p>
     *     The lower bound is usually the {@link NullType null type},
     *     unless this type variable is created by
     *     {@link TypeMath#doCaptureConversion(ClassType) capture conversion}
     *     for a lower-bounded wildcard.
     * </p>
     */

    abstract public ReferenceType<?> getLowerBound();

}
