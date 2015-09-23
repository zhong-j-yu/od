package bayou.od;

import java.util.Arrays;
import bayou.od.OD.Predicate;

class ExactTagMatch implements Predicate<Object[]>
{
    final Object[] tags;
    public ExactTagMatch(Object[] tags)
    {
        this.tags = tags;
    }

    @Override
    public boolean test(Object[] tags)
    {
        return Arrays.equals(this.tags, tags);
    }

    @Override
    public String toString()
    {
        return Arrays.toString(this.tags);
    }
}
