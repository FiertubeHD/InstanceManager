package de.fiertubehd;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InstanceManager<K, I>
{

    private static final Map<Class<?>, Class<?>> primitiveWrapperMap;

    static
    {
        primitiveWrapperMap = new HashMap<>();

        primitiveWrapperMap.put(Boolean.TYPE, Boolean.class);
        primitiveWrapperMap.put(Byte.TYPE, Byte.class);
        primitiveWrapperMap.put(Character.TYPE, Character.class);
        primitiveWrapperMap.put(Short.TYPE, Short.class);
        primitiveWrapperMap.put(Integer.TYPE, Integer.class);
        primitiveWrapperMap.put(Long.TYPE, Long.class);
        primitiveWrapperMap.put(Double.TYPE, Double.class);
        primitiveWrapperMap.put(Float.TYPE, Float.class);
        primitiveWrapperMap.put(Void.TYPE, Void.TYPE);
    }

    private final Map<K, Map<Integer, I>> instanceMap;
    private final Class<I> instanceClazz;


    private final Class<? extends Map> mapClazz;
    private final Object[] mapArgs;

    public InstanceManager(@NotNull Class<I> instanceClazz)
    {
        this(instanceClazz, HashMap.class, new Object[]{});
    }

    public InstanceManager(@NotNull Class<I> instanceClazz, @NotNull Class<? extends Map> mapClazz, @NotNull Object[] mapArgs) throws RuntimeException
    {
        this.instanceClazz = instanceClazz;

        this.mapClazz = mapClazz;
        this.mapArgs = mapArgs;

        try
        {
            instanceMap = createInstance(mapClazz, mapArgs);

        }catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }



    @Nullable
    public I getExistingInstance(@NotNull K key, int instanceId)
    {
        return getInstanceMapEntry(key, instanceId);
    }



    @NotNull
    public I getInstance(@NotNull K key, int instanceId, @NotNull Object[] constructorArguments) throws RuntimeException
    {
        validateInstanceId(instanceId);

        if(existsInstanceMapEntry(key, instanceId))
            return getInstanceMapEntry(key, instanceId);

        I instance = createInstance(instanceClazz, constructorArguments);

        setInstanceMapEntry(key, instanceId, instance);

        return instance;
    }

    @NotNull
    public I getInstance(@NotNull K key, int instanceId, @NotNull Constructor<I> constructor, @NotNull Object[] constructorArguments) throws RuntimeException
    {
        validateInstanceId(instanceId);

        if(existsInstanceMapEntry(key, instanceId))
            return getInstanceMapEntry(key, instanceId);

        I instance = createInstance(constructor, constructorArguments);

        setInstanceMapEntry(key, instanceId, instance);

        return instance;
    }

    public boolean removeInstance(@NotNull K key, int instanceId)
    {
        return removeInstanceMapEntry(key, instanceId);
    }

    public boolean existsInstance(@NotNull K key, int instanceId)
    {
        return existsInstanceMapEntry(key, instanceId);
    }



    public boolean isInstanceIdAssigned(@NotNull K key, int instanceId)
    {
        return instanceMap.containsKey(key) && instanceMap.get(key).containsKey(instanceId);
    }

    public int getUnassignedInstanceId(@NotNull K key)
    {
        if(!instanceMap.containsKey(key))
            return 0;

        int next = 0;

        Set<Integer> keys = instanceMap.get(key).keySet();
        while(keys.contains(next))
            next++;

        return next;
    }



    protected synchronized void setInstanceMapEntry(@NotNull K key, int instanceId, @NotNull I instance) throws RuntimeException
    {
        if(!instanceMap.containsKey(key))
        {
            try
            {
                instanceMap.put(key, createInstance(mapClazz, mapArgs));

            }catch(Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        instanceMap.get(key).put(instanceId, instance);
    }

    protected boolean removeInstanceMapEntry(@NotNull K key, int instanceId)
    {
        if(!instanceMap.containsKey(key))
            return false;

        return instanceMap.get(key).remove(instanceId) != null;
    }

    @Nullable
    protected I getInstanceMapEntry(@NotNull K key, int instanceId)
    {
        return (instanceMap.containsKey(key)) ? instanceMap.get(key).get(instanceId) : null;
    }

    protected boolean existsInstanceMapEntry(@NotNull K key, int instanceId)
    {
        return instanceMap.containsKey(key) && instanceMap.get(key).containsKey(instanceId);
    }





    @Nullable
    public static <E> E createInstance(@NotNull Constructor<E> instanceConstructor, @NotNull Object[] constructorArguments)
    {
        E instance = null;
        try
        {
            instanceConstructor.setAccessible(true);
            instance = instanceConstructor.newInstance(constructorArguments);

        }catch(Exception e)
        {
            throw new RuntimeException(e);
        }

        return instance;
    }

    @Nullable
    public static <E> E createInstance(@NotNull Class<E> instanceClazz, @NotNull Object[] constructorArguments) throws RuntimeException
    {
        E instance = null;
        try
        {
            Constructor<E> constructor = findMatchingConstructor(instanceClazz, constructorArguments);
            constructor.setAccessible(true);

            instance = constructor.newInstance(constructorArguments);

        }catch(Exception e)
        {
            throw new RuntimeException(e);
        }

        return instance;
    }

    @Nullable
    public static <E> Constructor<E> findMatchingConstructor(@NotNull Class<E> instanceClazz, @NotNull Object[] constructorArguments) throws RuntimeException
    {
        for(Constructor<?> declaredConstructor : instanceClazz.getDeclaredConstructors())
        {
            Class<?>[] declaredParameterTypes = declaredConstructor.getParameterTypes();
            if(declaredParameterTypes.length != constructorArguments.length) continue;

            boolean match = true;
            for(int i = 0; i < declaredParameterTypes.length; i++)
            {
                if(!primitiveToWrapper(declaredParameterTypes[i]).isInstance(constructorArguments[i]))
                {
                    match = false;
                    break;
                }
            }

            if(match) return (Constructor<E>) declaredConstructor;
        }

        throw new IllegalArgumentException(
                "No suitable constructor could be found in the class "
                        + instanceClazz.getName()
                        + " with the specified arguments("
                        + Arrays.stream(constructorArguments)
                        .map(o -> o == null ? null : o.getClass())
                        .map(c -> c == null ? "null" : c.getName())
                        .collect(Collectors.joining(",", "(", ")"))
        );
    }


    @Nullable("if clazz is null")
    public static Class<?> primitiveToWrapper(final Class<?> clazz)
    {
        Class<?> convertedClass = clazz;
        if(clazz != null && clazz.isPrimitive())
        {
            convertedClass = primitiveWrapperMap.get(clazz);
        }

        return convertedClass;
    }



    @NotNull
    public Class<I> getInstanceClazz()
    {
        return instanceClazz;
    }

    @NotNull
    public Class<? extends Map> getMapClazz()
    {
        return mapClazz;
    }

    @NotNull
    public Object[] getMapArgs()
    {
        return mapArgs;
    }

    @NotNull
    public Map<K, Map<Integer, I>> getInstanceMap()
    {
        return instanceMap;
    }



    public static boolean isValidInstanceId(int instanceId)
    {
        return instanceId >= 0 && instanceId <= 999;
    }

    public static void validateInstanceId(int instanceId) throws RuntimeException
    {
        if(!isValidInstanceId(instanceId))
            throw new IllegalArgumentException("InstanceId out of range. Must be 0-999");
    }
}