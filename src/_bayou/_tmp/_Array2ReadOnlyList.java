package _bayou._tmp;

import java.util.AbstractList;

// a read only List view to an array.
public class _Array2ReadOnlyList<E> extends AbstractList<E>
{
    final E[] sourceArray;
    final int offset, size;

    public _Array2ReadOnlyList(E[] sourceArray)
    {
        this.sourceArray = sourceArray;
        this.offset = 0;
        this.size = sourceArray.length;
    }
    public _Array2ReadOnlyList(E[] sourceArray, int offset, int size)
    {
        assert offset+size <= sourceArray.length;

        this.sourceArray = sourceArray;
        this.offset = offset;
        this.size = size;
    }

    public E get(int index)
    {
        if(index>=size)
            throw new IndexOutOfBoundsException();
        return sourceArray[index+offset];
    }
    public int size()
    {
        return size;
    }
}
