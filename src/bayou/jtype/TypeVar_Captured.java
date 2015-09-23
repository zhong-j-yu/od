package bayou.jtype;

import java.lang.reflect.GenericDeclaration;
import java.util.concurrent.atomic.AtomicLong;

// type variable created by capture conversion.
final class TypeVar_Captured<T> extends TypeVar<T>
{
    // capture conversion is required to narrow down the type of an expression.
    // e.g. in a method invocation expression E: `o.f(y)`, conversions on types `o` and `y` are done first,
    // before evaluating E; then capture conversion is applied on `f` return type, to get the type of E.

    // capture conversion is also needed in subtyping and inference.

    // this class is not immutable. the two bound fields are not `final`.
    // it's difficult to achieve because cross references between captured vars.
    // but it's ok to be not immutable, since instances of this class either
    //   1. created and used during subtyping/inference, used by single thread
    //   2. leaked to outside as part of other types, which are immutable.

    static final AtomicLong idSeq = new AtomicLong(0);

    final long id;
    final TypeVar<?> var0;
    final Wildcard wildcard;

    TypeVar_Captured(TypeVar<?> var0, Wildcard wildcard)
    {
        assert var0.getDeclaringSite() instanceof Class;

        this.id = idSeq.incrementAndGet();
        this.var0 = var0;
        this.wildcard = wildcard;
        // bounds not set here
    }

    ReferenceType<?> upperBound;
    ReferenceType<?> lowerBound;

    public String getName()
    {
        return "Cap#"+id;
    }
    public GenericDeclaration getDeclaringSite()
    {
        return null;
    }
    public ReferenceType<?> getUpperBound()
    {
        return upperBound;
    }
    public ReferenceType<?> getLowerBound()
    {
        return lowerBound;
    }
    int genHash()
    {
        return System.identityHashCode(this);
    }
    // compare identity
    public boolean equals(Object obj){ return this==obj; }
    boolean eqX(Object obj){ return this==obj; }

    public String toString(boolean full)
    {
        // example
        // !full: Cap#12(? extends String)
        //  full: Cap#12(? super java.lang.Integer)(@java.util.Map$K)

        StringBuilder sb = new StringBuilder();
        sb.append("Cap#").append(id);
        sb.append("(");
        sb.append( wildcard.toString(full) );
        sb.append(")");
        if(full)
        {
            sb.append("(@");
            sb.append( var0.toString(full) );
            sb.append(")");
        }
        return sb.toString();
    }
}
