package bayou.od;

import bayou.jtype.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/*
    Given a class B<V1..Vm>, if we new B<v1..vm>(), due to erasure,
    the constructor does not know the value of v1..vm, which it may need.
    We solve this problem by having a "type-arg" constructor which accepts v1..vm through its method parameters.

    a candidate type-arg constructor: B(A1..Ak)
        Ai=Ci<Ui>, Ci one of is ReferenceType, ArrayType, ClassType, Class,
        Ui is one of V1..Vm.
    the type-arg constructor:
        the candidate with the most number of parameters. (error if multiple such candidates)

    if the type-arg constructor is found, it is impossible that B is an inner member class,
    otherwise the constructor would require an enclosing instance as first method parameter.
    B could still be local/anonymous in a static context, that's fine by us.

    B must not be abstract or interface.

    We don't require that the constructor is accessible; we'll bypass access checking.
    User can use a type-arg constructor not accessible to general public, that is fine.

    Given type args v1..vm for V1..Vm, we get constructor args a1..ak for A1..Ak.
    If Ui=Vj, ai=vj. Type of vj must match Ci: e.g. error if vj is ArrayType, Ci is ClassType.

*/
class TypeArgConstructor
{
    Constructor constructor;
    int k;
    Class[] argC;  // ReferenceType, ArrayType, ClassType or Class
    int[]   argU;  // if Ui=Vj, argU[i]=j

    // return a1..ak
    // if arg types don't match, return null
    Object[] getConstructorArgs(ClassType<?> B_v1_vm)
    {
        // v1..vm
        List<ReferenceType<?>> typeArgs = OD.cast(B_v1_vm.getTypeArgs()); // no wildcard
        // a1..ak
        Object[] consArgs = new Object[k];
        for(int i=0; i<k; i++)
        {
            int j = argU[i];
            ReferenceType<?> vj = typeArgs.get(j);
            Class Ci = argC[i];
            if(Ci==Class.class) // Ai=Class<Vj>, vj must be a ClassType
            {
                if(!(vj instanceof ClassType))
                    return null;
                consArgs[i] = ((ClassType<?>)vj).getTheClass(); // ai must be a Class
            }
            else // more normal cases, match type of vj to Ci
            {
                if(!Ci.isInstance(vj))
                    return null;
                consArgs[i] = vj;
            }
        }
        return consArgs;
    }

    static TypeArgConstructor of(Class<?> clazz) throws RuntimeException
    {
        // TypeMath.assertIsClassOrInterface(clazz); // caller did that

        if(Modifier.isAbstract(clazz.getModifiers())) // also true if clazz is interface
            throw new IllegalArgumentException(String.format("%s cannot be instantiated", clazz));

        ClassType<?> decl = ClassType.of(clazz);
        List<TypeVar<?>> V1_Vm = decl.getTypeVars();

        //shortcut for m=0: simply the default constructor
        if(V1_Vm.size()==0)
            return ofNonGeneric(clazz);

        int maxK = -1;
        ArrayList<TypeArgConstructor> candidates = new ArrayList<TypeArgConstructor>();
        for(Constructor cons : clazz.getDeclaredConstructors())
        {
            TypeArgConstructor tac = tryCandidate(cons, maxK, V1_Vm);
            if(tac!=null)
            {
                // bc.k >= maxK
                if(tac.k > maxK)
                {
                    maxK = tac.k;
                    candidates.clear();
                }
                candidates.add(tac);
            }
        }
        if(candidates.size()==0)
            throw new IllegalArgumentException("no type-arg constructor found for "+clazz);
        if(candidates.size()>1)
            throw new IllegalArgumentException(String.format("multiple type-arg constructors found:%n %s %n %s",
                candidates.get(0).constructor, candidates.get(1).constructor));

        TypeArgConstructor tac = candidates.get(0);
        tac.constructor.setAccessible(true);
        return tac;
    }

    //shortcut for m=0: simply the default constructor
    static TypeArgConstructor ofNonGeneric(Class<?> clazz) throws RuntimeException
    {
        Constructor cons;
        try
        {   cons = clazz.getDeclaredConstructor();    } // the default one
        catch (NoSuchMethodException e)
        {   throw new IllegalArgumentException("no default constructor found for "+clazz); }
        TypeArgConstructor tac = new TypeArgConstructor();
        tac.constructor = cons;
        tac.k = 0;
        tac.argC = new Class[tac.k];
        tac.argU = new int[tac.k];
        tac.constructor.setAccessible(true);
        return tac;
    }

    static TypeArgConstructor tryCandidate(Constructor cons, int maxK, List<TypeVar<?>> V1_Vm)
    {
        Type[] A1_Ak = cons.getGenericParameterTypes();
        if(A1_Ak.length < maxK)
            return null; // don't care even if this is a candidate

        TypeArgConstructor tac = new TypeArgConstructor();
        tac.constructor = cons;
        tac.k = A1_Ak.length;
        tac.argC = new Class[tac.k];
        tac.argU = new int[tac.k];
        for(int i=0; i<tac.k; i++)
        {
            Type Ai_1 = A1_Ak[i];
            JavaType<?> Ai_2 = JavaType.convertFrom(Ai_1);
            if(!(Ai_2 instanceof ClassType)) // must be in the form of Ci<Ui>
                return null;
            ClassType<?> Ai = (ClassType<?>)Ai_2;
            Class Ci = Ai.getTheClass();
            if(!validCi(Ci))
                return null;
            if(Ai.getTypeArgs().size()!=1) // no Ui
                return null;
            TypeArg Ui_1 = Ai.getTypeArgs().get(0);
            if(!(Ui_1 instanceof TypeVar))
                return null;
            TypeVar<?> Ui = (TypeVar<?>)Ui_1;
            int j = find_j(Ui, V1_Vm);
            if(j<0)
                return null;
            // Ai is good
            tac.argC[i] = Ci;
            tac.argU[i] = j;
        }
        // this is a candidate
        return tac;
    }

    static boolean validCi(Class Ci)
    {
        return Ci==Class.class || Ci==ClassType.class || Ci==ArrayType.class || Ci==ReferenceType.class;
    }

    static int find_j(TypeVar<?> Ui, List<TypeVar<?>> V1_Vm)
    {
        for(int j=0; j<V1_Vm.size(); j++) // ok, m is small
            if(Ui.equals(V1_Vm.get(j)))
                return j;
        return -1;
    }

}
