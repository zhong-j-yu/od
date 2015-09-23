package bayou.jtype;

import _bayou._tmp._Array2ReadOnlyList;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

// type variable declared in source code, and obtained by java.lang.reflect.
// lower bound is null.
final class TypeVar_Declared<T> extends TypeVar<T>
{
    final TypeVariable jlrVar;

    // bounds may reference this var, so we can't convert them in constructor. converted on demand instead.
    private ReferenceType<?> upperBound;

    public TypeVar_Declared(TypeVariable jlrVar)
    {
        this.jlrVar = jlrVar;
    }

    public String getName()
    {
        return jlrVar.getName();
    }
    // where the var is declared, class/method/constructor.
    public GenericDeclaration getDeclaringSite()
    {
        return jlrVar.getGenericDeclaration();
    }

    public ReferenceType<?> getUpperBound()
    {
        ReferenceType<?> tmp = upperBound;
        if(tmp==null)
        {
            // java reflect:
            // getBounds().length >= 1. return {Object} if no explicit bound.
            Type[] jlrBound = jlrVar.getBounds();
            JavaType[] boundArray = TypeMath.convertTypes(jlrBound);
            _Array2ReadOnlyList<JavaType> boundList = new _Array2ReadOnlyList<JavaType>(boundArray);
            // the cast is safe, every element in `boundArray` must be a ReferenceType<?>
            _Array2ReadOnlyList<ReferenceType<?>> boundList2 = TypeMath.cast(boundList);
            if(boundList2.size()==1)
                tmp = boundList2.get(0); // a little simplification
            else
                tmp = new IntersectionType<Object>(boundList2);
            upperBound = tmp;
        }
        return tmp;
    }

    public ReferenceType<?> getLowerBound()
    {
        return NullType.INSTANCE;
    }

    int genHash()
    {
        return jlrVar.hashCode();
    }

    boolean eqX(Object obj){ return this.equals((TypeVar_Declared<?>)obj); }
    boolean equals(TypeVar_Declared<?> that)
    {
        return eq(this.jlrVar, that.jlrVar); // compare identity
    }

    public String toString(boolean full)
    {
        if(!full)
            return jlrVar.getName(); // hopefully its context is clear to caller

        // JLS3#13.1 binary name of a type variable of a class: className$T
        GenericDeclaration host = jlrVar.getGenericDeclaration();
        // if host is method/constructor, host.toString() can be ugly, non-distinct and inaccurate.
        // we don't care; we mostly deal with type parameters from classes.
        String hostStr = (host instanceof Class) ? ((Class)host).getName() : host.toString();
        return hostStr +"$"+ jlrVar.getName();

        // don't have to include bounds in full string. they are fixed and implied.
    }
}
