/**
 * Representations of Java types.
 * <p>
 *     The official {@link java.lang.reflect.Type java.lang.reflect.Type} hierarchy is a messy representation
 *     of Java types.
 * </p>
 * <p>
 *     This package provides a better representation
 *     that is aligned with the language specification.
 *     See {@link bayou.jtype.JavaType}.
 * </p>
 * <p>
 *     Use {@link bayou.jtype.JavaType#convertFrom(java.lang.reflect.Type)} for conversion.
 * </p>
 * <p>
 *     This package also include some type algorithms (e.g. subtyping) in {@link bayou.jtype.TypeMath}.
 * </p>
 */
package bayou.jtype;