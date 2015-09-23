package bayou.jtype;

/**
 * Reference type.
 * <p>
 *     A Java type is either a reference type or a primitive type.
 * </p>
 * <p>
 *     Reference types include
 * </p>
 * <pre>
 *     {@link bayou.jtype.ClassType}
 *     {@link bayou.jtype.ArrayType}
 *     {@link bayou.jtype.TypeVar}
 *     {@link bayou.jtype.IntersectionType}
 *     {@link bayou.jtype.NullType}
 * </pre>
 * <p>
 *     Any <code>ReferenceType</code> can be used as a {@link TypeArg}.
 * </p>
 */
public abstract class ReferenceType<T> extends JavaType<T> implements TypeArg
{
    ReferenceType(){} // forbid out-of-package subclass
}
