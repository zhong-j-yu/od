package bayou.od;

import bayou.jtype.ClassType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import bayou.od.OD.Predicate;
import bayou.od.OD.Supplier;

class SupplierBinding<X> implements OD.Binding
{
    final ClassType<X> type;
    final Predicate<Object[]> tagMatcher;
    final Supplier<? extends X> supplier;

    SupplierBinding(ClassType<X> type, Predicate<Object[]> tagMatcher, Supplier<? extends X> supplier)
    {
        this.type = type;
        this.tagMatcher = tagMatcher;
        this.supplier = supplier;
    }

    public String toString()
    {
        return String.format("SupplierBinding(supplier=%s, type=%s, tags=%s)",
            supplier, type.toString(false), tagMatcher);
    }

    public <T> Supplier<? extends T> map(ClassType<T> type, Object... tags)
    {
        if(!OD.match(this.type, type, this.tagMatcher, tags))
            return null;

        return OD.cast(supplier);
    }
    public Set<? extends Class> getApplicableClasses()
    {
        return Collections.singleton(type.getTheClass());
    }
}
