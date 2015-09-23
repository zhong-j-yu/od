package bayou.od;

import _bayou._tmp._Array2ReadOnlyList;
import bayou.od.OD.Binding;

import java.util.*;

// list of bindings. indexed by applicable class. the only supported mutation is add().
class BindingList
{
    final BindingQueue allBindings;
    // bindings that may apply to a class
    final HashMap<Class, BindingQueue> classBindings;
    // bindings whose applicable classes set is null, i.e. all classes.
    final BindingQueue wildBindings;

    BindingList()
    {
        allBindings = new BindingQueue();
        classBindings = new HashMap<Class, BindingQueue>();
        wildBindings = new BindingQueue();
    }
    BindingList(BindingList that) //copy
    {
        this.allBindings = new BindingQueue(that.allBindings);
        this.classBindings = copy(that.classBindings);
        this.wildBindings = new BindingQueue(that.wildBindings);
    }
    static HashMap<Class,BindingQueue> copy(HashMap<Class,BindingQueue> map)
    {
        HashMap<Class,BindingQueue> copy = new HashMap<Class,BindingQueue>(map);
        // must copy values BindingQueue as well
        for(Map.Entry<Class,BindingQueue> entry : copy.entrySet())
            entry.setValue( new BindingQueue( entry.getValue() ) );
        return copy;
    }

    void add(Binding binding)
    {
        allBindings.add(binding);
        Set<? extends Class> appClasses = binding.getApplicableClasses();
        if(appClasses==null)
        {
            wildBindings.add(binding);
            // this wild binding may apply to all classes
            for(BindingQueue cb : classBindings.values())
                cb.add(binding);
        }
        else
        {
            for(Class clazz : appClasses)
            {
                BindingQueue cb = classBindings.get(clazz);
                if(cb==null)
                {
                    // preceded by all prev wild bindings
                    cb = new BindingQueue(wildBindings);
                    classBindings.put(clazz, cb);
                }
                cb.add(binding);
            }
        }
    }
    // returned list is immutable, not affected by add(). this method is very cheap to call.
    List<Binding> forClass(Class clazz)
    {
        BindingQueue cb = classBindings.get(clazz);
        if(cb==null)
            cb = wildBindings;

        return cb.snapshot();
    }

    // only supported mutation is add(). can cheaply return an immutable snapshot.
    // concurrency note: this class is single threaded; locking is done by higher up.
    static class BindingQueue
    {
        Binding[] array;
        int size;
        List<Binding> prev_snapshot; // not null. immutable. may be outdated.

        BindingQueue()
        {
            this.array = new Binding[16];
            this.size=0;
            prev_snapshot = Collections.emptyList();
        }
        BindingQueue(BindingQueue that) // copy
        {
            this.array = that.array.clone();
            this.size = that.size;
            this.prev_snapshot = that.prev_snapshot;
        }

        void add(Binding e)
        {
            int capacity = array.length;
            if(size==capacity) // full
                array = Arrays.copyOf(array, (capacity+1)*3/2); // increase capacity by 50%

            array[size++]=e;
            // prev_snapshot becomes outdated
        }

        List<Binding> snapshot()
        {
            // very likely that a binding list remains constant after initial setup.
            // so the cached snapshot is valid forever

            if(prev_snapshot.size()!=this.size) // rare
                prev_snapshot = new _Array2ReadOnlyList<Binding>(array, 0, this.size);

            return prev_snapshot;
        }

        // used by LocalBindings
        Binding get(int index)
        {
            if(index>=size)
                throw new IndexOutOfBoundsException();
            return array[index];
        }
    }

}
