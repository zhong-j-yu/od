package bayou.od;

import bayou.jtype.ClassType;
import bayou.jtype.TypeMath;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import bayou.od.OD.Predicate;
import bayou.od.OD.Supplier;

class ConstructorBinding<X> implements OD.Binding
{
    final ClassType<X> type;
    final Predicate<Object[]> tagMatcher;
    final Constructor<X> constructor;
    final Object[] constructorArgs;

    public ConstructorBinding(ClassType<X> type, Predicate<Object[]> tagMatcher, Constructor<X> constructor, Object[] constructorArgs)
    {
        this.type = type;
        this.tagMatcher = tagMatcher;
        this.constructor = constructor;
        this.constructorArgs = constructorArgs;
    }

    public String toString()
    {
        return String.format("ConstructorBinding(constructor=%s, args=%s, type=%s, tags=%s)",
            constructor, Arrays.toString(constructorArgs), type.toString(false), tagMatcher);
    }

    public <T> Supplier<? extends T> map(ClassType<T> type, Object... tags)
    {
        if(!OD.match(this.type, type, this.tagMatcher, tags))
            return null;

        ConstructorSupplier<X> supplier = new ConstructorSupplier<X>(constructor, constructorArgs);
        return OD.cast(supplier); // T=X
    }

    public Set<? extends Class> getApplicableClasses()
    {
        return Collections.singleton(type.getTheClass());
    }
}
