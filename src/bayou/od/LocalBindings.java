package bayou.od;

import java.util.Map.Entry;
import java.util.*;
import bayou.od.OD.Supplier;

import bayou.jtype.TypeMath;
import bayou.od.OD.*;

class LocalBindings
{
    static final ThreadLocal<LocalBindings> localBindings_TL = new ThreadLocal<LocalBindings>();

    boolean shared; // if bindingList is shared by another instance
    int version;

    BindingList bindingList;
    HashMap<TypeAndTags, Supplier> cache;
    HashSet<Class> cachedClasses; // classes covered by cache

    LocalBindings()
    {
        shared = false;
        version = 0;
        bindingList = new BindingList();
        cache = new HashMap<TypeAndTags, Supplier>();
        cachedClasses = new HashSet<Class>();
    }
    private LocalBindings(LocalBindings that) // copy constructor, for backup/restore
    {
        // don't do actual copying here; share.
        that.shared=true;

        shared =true;
        version = that.version;
        bindingList = that.bindingList;
        cache = that.cache;
        cachedClasses = that.cachedClasses;
    }


    void add0(Binding binding)
    {
        if(shared) // must make private copy before mutation
        {
            bindingList = new BindingList(bindingList);
            cache = new HashMap<TypeAndTags, Supplier>(cache);
            cachedClasses = new HashSet<Class>(cachedClasses);

            shared = false;
        }

        version++;
        bindingList.add(binding);
        fixCache(binding);
    }

    // -------------------------------------------------------------------

    static void addLocal(Binding binding)
    {
        LocalBindings lb = localBindings_TL.get();
        if(lb==null)
            localBindings_TL.set( lb = new LocalBindings() );

        lb.add0(binding);
    }

    // marker value used in cache, representing no supplier is mapped to [type,tags]
    // null result is cached for local lookup; for repeated lookup, we want to quickly
    // hand over to global, if we knew local doesn't satisfy it.
    // note, null result is NOT cached for global lookup, because it's likely
    // a config error if we reach there and nobody satisfies the lookup.
    static final Supplier NO_SUPPLIER = new Supplier<Object>(){
        public Object get()
        {    throw new AssertionError(); }
    };

    static <T> Supplier<T> getSupplier(TypeAndTags<T> tnt)
    {
        LocalBindings lb = localBindings_TL.get();
        if(lb==null)
            return null;

        Supplier supplier = lb.cache.get(tnt);
        if(supplier ==null)
        {
            Class clazz = tnt.type.getTheClass();

            int version0 = lb.version;
            List<Binding> bindings = lb.bindingList.forClass(clazz); // this list is constant

            supplier = OD.findSupplier(bindings, tnt.type, tnt.tags); // alien code! //throws
            // it's possible that local bindings have changed now

            if(supplier ==null) // mask null
                supplier = NO_SUPPLIER;

            // cache the result, only if local bindings have not been changed.
            if(lb.version==version0)
            {
                lb.cache.put(tnt, supplier);
                lb.cachedClasses.add(clazz);
            }
            // actually, `lb` could have been kicked out of `localBindings_TL`; the current LocalBindings
            // could be another instance or null. no need to check for that case, which should be very rare.
            // there's still no harm to populate `lb.cache` in any case as long as `lb.bindingList` didn't change.

            // note: cache can be shared too, updates to lb1.cache can be visible in lb2.cache.
            // this is ok as long as they share the same bindingList.
            // when they no longer share bindingList, they'll have separate caches as well.
        }

        if(supplier == NO_SUPPLIER) // unmask null
            supplier =null;
        return OD.cast(supplier);
    }

    // the new binding may invalidate some cache entries.
    // that should be rare; we try to preserve cache entries.
    void fixCache(Binding newBinding)
    {
        if(cache.isEmpty()) // common during initial local bindings
            return;

        // a cache entry may become invalid, if type.class is applicable in the new binding.
        // we don't do more complicated stuff, e.g. test newBinding.getSupplier(tnt)

        Set<? extends Class> appClasses = newBinding.getApplicableClasses();
        if(appClasses==null) // this should be very rare. don't return null!
        {
            // no idea what classes this new binding may affect. clear all.
            // O(n), n=capacity
            cache.clear();
            cachedClasses.clear();
            return;
        }

        for(Class clazz : appClasses) // most likely the set contains only 1 class
        {
            // usually a new binding is for a new class, which has not been covered in the cache
            if(!cachedClasses.contains(clazz)) // common case
                continue;

            // rare case: some cache entries are for this clazz; evict them.
            Iterator<Entry<TypeAndTags, Supplier>> iter = cache.entrySet().iterator();
            while(iter.hasNext())
            {
                Entry<TypeAndTags, Supplier> entry = iter.next();
                if(clazz==entry.getKey().type.getTheClass())
                    iter.remove();
            }
            cachedClasses.remove(clazz); // now cache is free of this clazz
        }

    }

    // typical usage idiom of local bindings:
    //     save snapshot
    //     add bindings
    //     ...
    //     restore snapshot
    static List<Binding> getSnapshot()
    {
        LocalBindings lb = localBindings_TL.get();
        if(lb==null)
            return Collections.emptyList();
        else
            return new LocalBindings(lb).asList(); // a snapshot

        // although no actual copying is done here, it's very likely that `lb` immediately
        // gets new bindings; that's the motive for saving snapshot in the 1st place.
        // therefore very likely `lb` will trigger copying immediately.

        // so it's not very cheap; we could do further optimizations,
        // e.g. multiple BindingList can share internal data, if only one of them mutates.
        // cache copying can be delayed until it is to be updated.
        // for now, we think save-restore happens infrequently, so it's acceptable.
    }

    static void setAll(List<Binding> bindings)
    {
        LocalBindings lb;
        if(bindings==null || bindings.isEmpty())
        {
            lb = null;
        }
        else if(bindings instanceof ListWrapper)
        {
            // restore a prev snapshot of LocalBinding; client got it from getSnapshot().
            lb = new LocalBindings( ((ListWrapper)bindings).unwrap() );
            // no actual copying here.
            // in typical usage it's rare that `lb` will mutate after calling this method.
            // our complexity of `shared` strategy pays off here. is it worth it?
        }
        else // client does sophisticated manipulations of local binding list.
        {
            // from blank
            lb = new LocalBindings();
            for(Binding binding : bindings)
                lb.bindingList.add(binding);
        }

        localBindings_TL.set(lb);
        // lb can be null, in which case we could do localBindings_TL.remove();
        // set(null) is fine tho.
    }


    List<Binding> asList(){ return new ListWrapper(); }
    class ListWrapper extends AbstractList<Binding>
    {
        LocalBindings unwrap(){ return LocalBindings.this; }
        public Binding get(int index) // per AbstractList
        {
            return bindingList.allBindings.get(index);
        }
        public int size() // per AbstractList
        {
            return bindingList.allBindings.size;
        }
    }
}
