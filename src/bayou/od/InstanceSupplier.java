package bayou.od;

import bayou.od.OD.Supplier;

class InstanceSupplier<T> implements Supplier<T>
{
    final T instance;

    InstanceSupplier(T instance)
    {
        this.instance = instance;
    }

    public T get()
    {
        return instance;
    }

    public String toString()
    {
        return String.format("InstanceSupplier(instance=%s)", instance);
    }
}
