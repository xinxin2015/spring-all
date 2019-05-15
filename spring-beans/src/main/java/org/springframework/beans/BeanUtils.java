package org.springframework.beans;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class BeanUtils {

    private static final Log logger = LogFactory.getLog(BeanUtils.class);

    private static final Set<Class<?>> unknownEditorTypes =
            Collections.newSetFromMap(new ConcurrentReferenceHashMap<>(64));

    private static final Map<Class<?>,Object> DEFAULT_TYPE_VALUES;

    static {
        Map<Class<?>,Object> values = new HashMap<>();
        values.put(boolean.class,false);
        values.put(byte.class,(byte)0);
        values.put(short.class,(short)0);
        values.put(int.class,0);
        values.put(long.class,(long)0);
        DEFAULT_TYPE_VALUES = Collections.unmodifiableMap(values);
    }

    @Deprecated
    public static <T> T instantiate(Class<T> clazz) throws BeanInstantiationException {
        Assert.notNull(clazz,"Class must not be null");
        if (clazz.isInterface()) {
            throw new BeanInstantiationException(clazz, "Specified class is an interface");
        }
        try {
            return clazz.newInstance();
        } catch (InstantiationException ex) {
            throw new BeanInstantiationException(clazz, "Is it an abstract class?", ex);
        } catch (IllegalAccessException ex) {
            throw new BeanInstantiationException(clazz, "Is the constructor accessible?", ex);
        }
    }

    public static <T> T instantiateClass(Constructor<T> ctor,Object... args) throws BeanInstantiationException {
        Assert.notNull(ctor,"Constructor must not be null");
        try {
            ReflectionUtils.makeAccessible(ctor);
            Class<?>[] parameterTypes = ctor.getParameterTypes();
            Assert.isTrue(args.length <= parameterTypes.length, "Can't specify more arguments than constructor parameters");
            Object[] argsWithDefaultValues = new Object[args.length];
            for (int i = 0;i < args.length;i ++) {
                if (args[i] == null) {
                    Class<?> parameterType = parameterTypes[i];
                    argsWithDefaultValues[i] = parameterType.isPrimitive() ? DEFAULT_TYPE_VALUES.get(parameterType) :
                            null;
                } else {
                    argsWithDefaultValues[i] = args[i];
                }
            }
            return ctor.newInstance(argsWithDefaultValues);
        } catch (InstantiationException ex) {
            throw new BeanInstantiationException(ctor, "Is it an abstract class?", ex);
        } catch (IllegalAccessException ex) {
            throw new BeanInstantiationException(ctor, "Is the constructor accessible?", ex);
        } catch (IllegalArgumentException ex) {
            throw new BeanInstantiationException(ctor, "Illegal arguments for constructor", ex);
        } catch (InvocationTargetException ex) {
            throw new BeanInstantiationException(ctor, "Constructor threw exception", ex.getTargetException());
        }
    }

    @Nullable
    public static <T> Constructor<T> findPrimaryConstructor(Class<T> clazz) {
        Assert.notNull(clazz,"Class must not be null");
        return null;
    }

    @Nullable
    public static Method findDeclaredMethod(Class<?> clazz,String methodName,Class<?>... paramTypes) {
        try {
            return clazz.getDeclaredMethod(methodName,paramTypes);
        } catch (NoSuchMethodException ex) {
            if (clazz.getSuperclass() != null) {
                return findDeclaredMethod(clazz.getSuperclass(),methodName,paramTypes);
            }
            return null;
        }
    }

}
