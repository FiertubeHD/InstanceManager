package de.fiertubehd;

import de.fiertubehd.fluffyannotationslibrary.annotations.NotNull;
import de.fiertubehd.fluffyannotationslibrary.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class InstanceManager<K, I>
{

    private static final HashMap<Class<?>, Class<?>> primitiveWrapperMap;

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

    private final ConcurrentHashMap<K, ConcurrentHashMap<Integer, I>> instances;
    private final Class<I> instanceClazz;

    public InstanceManager(@NotNull Class<I> instanceClazz)
    {
        if(instanceClazz == null)
            throw new IllegalArgumentException("InstanceClazz cannot be null.");

        instances = new ConcurrentHashMap<>();

        this.instanceClazz = instanceClazz;
    }



    @Nullable
    public I getExistingInstance(@Nullable K key, int instanceId)
    {
        if(key == null || !isValidInstanceId(instanceId))
            return null;

        ConcurrentHashMap<Integer, I> innerMap = instances.get(key);
        if(innerMap == null)
            return null;

        return innerMap.get(instanceId);
    }

    public I getInstance(@NotNull K key, int instanceId, @NotNull Object[] parameters) throws RuntimeException
    {
        if(key == null)
            throw new IllegalArgumentException("Key cannot be null.");

        if(!isValidInstanceId(instanceId))
            throw new IllegalArgumentException("InstanceId cannot be smaller than 0.");

        if(parameters == null)
            throw new IllegalArgumentException("Parameters cannot be null.");

        ConcurrentHashMap<Integer, I> innerMap = instances.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        I instance = innerMap.computeIfAbsent(instanceId, integer ->
        {
            try
            {
                return createInstance(instanceClazz, parameters);

            }catch(Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        return instance;
    }

    public boolean unregisterInstance(@NotNull K key, int instanceId)
    {
        if(key == null)
            throw new IllegalArgumentException("Key cannot be null.");

        if(!isValidInstanceId(instanceId))
            throw new IllegalArgumentException("InstanceId cannot be smaller than 0.");

        ConcurrentHashMap<Integer, I> innerMap = instances.get(key);
        if(innerMap == null)
            return false;

        AtomicBoolean result = new AtomicBoolean(false);
        innerMap.computeIfPresent(instanceId, (integer, i) ->
        {
            result.set(true);
            return null;
        });

        return result.get();
    }

    public boolean existsInstance(@Nullable K key, int instanceId)
    {
        if(key == null || !isValidInstanceId(instanceId))
            return false;

        ConcurrentHashMap<Integer, I> innerMap = instances.get(key);
        if(innerMap == null)
            return false;

        return innerMap.containsKey(instanceId);
    }



    @NotNull
    public static <E> E createInstance(@NotNull Class<E> instanceClazz, @NotNull Object[] parameters) throws RuntimeException
    {
        if(instanceClazz == null)
            throw new IllegalArgumentException("InstanceClazz cannot be null.");

        if(parameters == null)
            throw new IllegalArgumentException("Parameters cannot be null.");

        try
        {
            Constructor<E> constructor = findMatchingConstructor(instanceClazz, parameters);
            constructor.setAccessible(true);

            return constructor.newInstance(parameters);

        }catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public static <E> E createInstance(@NotNull Constructor<E> constructor,  @NotNull Object[] parameters) throws RuntimeException
    {
        if(constructor == null)
            throw new IllegalArgumentException("Constructor cannot be null.");

        if(parameters == null)
            throw new IllegalArgumentException("Parameters cannot be null.");

        try
        {
            constructor.setAccessible(true);
            return constructor.newInstance(parameters);

        }catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public static <E> Constructor<E> findMatchingConstructor(@NotNull Class<E> instanceClazz, @NotNull Object[] parameters) throws RuntimeException
    {
        if(instanceClazz == null)
            throw new IllegalArgumentException("InstanceClazz cannot be null.");

        if(parameters == null)
            throw new IllegalArgumentException("Parameters cannot be null.");

        Constructor<?>[] declaredConstructors = null;
        try
        {
            declaredConstructors = instanceClazz.getDeclaredConstructors();

        }catch(Exception e)
        {
            throw new RuntimeException(e);
        }

        for(Constructor<?> declaredConstructor : declaredConstructors)
        {
            Class<?>[] declaredParameterTypes = declaredConstructor.getParameterTypes();
            if(declaredParameterTypes.length != parameters.length)
                continue;

            boolean match = true;
            for(int i = 0; i < declaredParameterTypes.length; i++)
            {
                if(!primitiveToWrapper(declaredParameterTypes[i]).isInstance(parameters[i]))
                {
                    match = false;
                    break;
                }
            }

            if(match)
                return (Constructor<E>) declaredConstructor;
        }

        throw new IllegalArgumentException
                        (
                        "No suitable constructor could be found in the class "
                        + instanceClazz.getName()
                        + " with the specified arguments"
                        + Arrays.stream(parameters)
                        .map(o -> o == null ? null : o.getClass())
                        .map(c -> c == null ? "null" : c.getName())
                        .collect(Collectors.joining(",", "(", ")"))
                        );
    }



    @Nullable("if clazz is null")
    public static Class<?> primitiveToWrapper(@Nullable final Class<?> clazz)
    {
        Class<?> convertedClass = clazz;
        if(clazz != null && clazz.isPrimitive())
            convertedClass = primitiveWrapperMap.get(clazz);

        return convertedClass;
    }

    public static boolean isValidInstanceId(int instanceId)
    {
        return instanceId >= 0;
    }

}
