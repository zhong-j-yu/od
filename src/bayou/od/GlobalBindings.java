package bayou.od;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import bayou.od.OD.Supplier;

import bayou.jtype.TypeMath;
import bayou.od.OD.TypeAndTags;
import bayou.od.OD.Binding;

class GlobalBindings
{
    // we have only very short locking blocks. no alien code is invoked under lock.
    static final Object lock = new Object();

    static int version = 0;
    static final BindingList globalList = new BindingList();

    static final ConcurrentHashMap<TypeAndTags, Supplier> cache = new ConcurrentHashMap<TypeAndTags, Supplier>();
    static final HashSet<Class> cachedClasses = new HashSet<Class>(); // classes covered by cache

    static void addGlobal(Binding binding)
    {
        synchronized (lock)
        {
            version++;
            globalList.add(binding);
            fixCache(binding);
        }
    }

    static List<Binding> snapshot(Class clazz)
    {
        synchronized (lock)
        {
            return globalList.forClass(clazz);
        }
    }

    static <T> Supplier<T> getSupplier(TypeAndTags<T> tnt)
    {
        // in most cases will return quickly with just one concurrent map lookup.
        Supplier supplier = cache.get(tnt);
        if(supplier ==null)
        {
            Class clazz = tnt.type.getTheClass();
            int _version;
            List<Binding> _globalList;
            synchronized (lock)
            {
                _version = GlobalBindings.version;
                _globalList = GlobalBindings.globalList.forClass(clazz);
            }

            // must not hold lock - we are invoking alien code, they could be slow
            supplier = OD.findSupplier(_globalList, tnt.type, tnt.tags);  //throws
            // bindings could have changed by now
            // either by the previous alien code in the same thread (this is rare)
            // or by other code in other thread concurrently

            // cache non-null result, if bindings are not changed
            if(supplier !=null)
            {
                synchronized (lock) // ensure cache is consistent with bindings
                {
                    if(GlobalBindings.version==_version)
                    {
                        cache.put(tnt, supplier);
                        cachedClasses.add(clazz);
                    }
                }
            }
            // null not cached - if lookup fails, it's likely a config error.
        }

        return OD.cast(supplier);
    }

    // due to new binding, some cache entries are no longer valid. evict them.
    // eviction should be very rare though:
    // for global bindings, they are done during app startup, cache is likely already empty;
    //   (or the cache contains only a few entries, no big deal to clear them.)
    //   if a global binding is added sometime later, it could clear lots of cache entries.
    //   that is an acceptable punishment for improper usage.
    static void fixCache(Binding newBinding)
    {
        // caller holds lock

        if(cachedClasses.isEmpty()) // common case during init global bindings
            return;
        // testing cache.isEmpty() would be more expensive

        // a cache entry may become invalid, if type.class is applicable in the new binding.
        // we don't do more sophisticated stuff, e.g. test newBinding.getSupplier(tnt)

        Set<? extends Class> appClasses = newBinding.getApplicableClasses();
        if(appClasses==null) // this should be very rare. don't return null!
        {
            // no idea what classes this new binding may affect. clear all.
            cache.clear();           // O(n), n=capacity
            cachedClasses.clear();   // O(n)
            return;
        }

        for(Class clazz : appClasses) // most likely the set contains only 1 class
        {
            // usually a new binding is for a new class, which has not been covered in the cache
            if(!cachedClasses.contains(clazz)) // common case
                continue;

            // rare case: some cache entries are for this clazz; evict them.
            Iterator<Map.Entry<TypeAndTags, Supplier>> iter = cache.entrySet().iterator();
            while(iter.hasNext()) // note: no one else is updating cache concurrently
            {
                Map.Entry<TypeAndTags, Supplier> entry = iter.next();
                if(clazz==entry.getKey().type.getTheClass())
                    iter.remove();
            }
            cachedClasses.remove(clazz); // now cache is free of this clazz
        }
    }

}