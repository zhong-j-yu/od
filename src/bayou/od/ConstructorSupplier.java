package bayou.od;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import bayou.od.OD.Supplier;

class ConstructorSupplier<T> implements Supplier<T>
{
    final Constructor<T> constructor;
    final Object[] args;

    ConstructorSupplier(Constructor<T> constructor, Object... args)
    {
        this.constructor = constructor;
        this.args = args;
    }

    public String toString()
    {
        return String.format("ConstructorSupplier(constructor=%s, args=%s)",
            constructor, Arrays.toString(args));
    }

    @SuppressWarnings("unchecked")
    public T get()
    {
        return (T)newInstance(constructor, args);
    }

    static Object newInstance(Constructor constructor, Object... args) throws RuntimeException
    {
        try
        {
            try
            {
                return constructor.newInstance(args);
            }
            catch (InvocationTargetException e)
            {
                throw e.getCause();
            }
        }
        catch(Error e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Throwable e) // checked
        {
            throw new RuntimeException(e);
        }
    }

}
