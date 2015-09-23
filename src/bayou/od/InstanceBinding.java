package bayou.od;

import bayou.jtype.ClassType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import bayou.od.OD.Predicate;
import bayou.od.OD.Supplier;

class InstanceBinding<X> implements OD.Binding
{
    final ClassType<X> type;
    final Predicate<Object[]> tagMatcher;
    final X instance;

    InstanceBinding(ClassType<X> type, Predicate<Object[]> tagMatcher, X instance)
    {
        this.type = type;
        this.tagMatcher = tagMatcher;
        this.instance = instance;
    }

    public String toString()
    {
        return String.format("InstanceBinding(instance=%s, type=%s, tags=%s)",
            instance, type.toString(false), tagMatcher);
    }

    public <T> Supplier<? extends T> map(ClassType<T> type, Object... tags)
    {
        if(!OD.match(this.type, type, tagMatcher, tags))
            return null;

        return OD.cast( new InstanceSupplier<X>(instance) );
    }
    public Set<? extends Class> getApplicableClasses()
    {
        return Collections.singleton(type.getTheClass());
    }
}
