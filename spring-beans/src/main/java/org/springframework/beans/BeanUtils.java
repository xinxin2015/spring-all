package org.springframework.beans;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

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

    public static <T> T instantiateClass(Class<T> clazz) throws BeanInstantiationException {
        Assert.notNull(clazz,"Class must not be null");
        if (clazz.isInterface()) {
            throw new BeanInstantiationException(clazz,"Specified class is an interface");
        }
        try {
            return instantiateClass(clazz.getDeclaredConstructor());
        } catch (NoSuchMethodException ex) {
            Constructor<T> ctor = findPrimaryConstructor(clazz);
            if (ctor != null) {
                return instantiateClass(ctor);
            }
            throw new BeanInstantiationException(clazz,"No default constructors found",ex);
        } catch (LinkageError err) {
            throw new BeanInstantiationException(clazz,"Unresolvable class definition",err);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T instantiateClass(Class<?> clazz,Class<T> assignableTo) throws BeanInstantiationException {
        Assert.isAssignable(assignableTo,clazz);
        return (T) instantiateClass(clazz);
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
    public static Method findMethod(Class<?> clazz,String methodName,Class<?>... paramTypes) {
        try {
            return clazz.getMethod(methodName,paramTypes);
        } catch (NoSuchMethodException e) {
            return findDeclaredMethod(clazz,methodName,paramTypes);
        }
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

    @Nullable
    public static Method findMethodWithMinimalParameters(Class<?> clazz,String methodName)
            throws IllegalArgumentException {
        Method targetMethod = findMethodWithMinimalParameters(clazz.getMethods(),methodName);
        if (targetMethod == null) {
            targetMethod = findDeclaredMethodWithMinimalParameters(clazz,methodName);
        }
        return targetMethod;
    }

    @Nullable
    public static Method findDeclaredMethodWithMinimalParameters(Class<?> clazz,String methodName)
            throws IllegalArgumentException {
        Method targetMethod = findMethodWithMinimalParameters(clazz.getDeclaredMethods(),methodName);
        if (targetMethod == null && clazz.getSuperclass() != null) {
            targetMethod = findDeclaredMethodWithMinimalParameters(clazz.getSuperclass(),methodName);
        }
        return targetMethod;
    }

    @Nullable
    public static Method findMethodWithMinimalParameters(Method[] methods,String methodName)
            throws IllegalArgumentException{
        Method targetMethod = null;
        int numMethodsFoundWithCurrentMinimumArgs = 0;
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                int numParams = method.getParameterCount();
                if (targetMethod == null || numParams < targetMethod.getParameterCount()) {
                    targetMethod = method;
                    numMethodsFoundWithCurrentMinimumArgs = 1;
                } else if (!method.isBridge() && targetMethod.getParameterCount() == numParams) {
                    if (targetMethod.isBridge()) {
                        // Prefer regular method over bridge...
                        targetMethod = method;
                    } else {
                        // Additional candidate with same length
                        numMethodsFoundWithCurrentMinimumArgs ++;
                    }
                }
            }
        }
        if (numMethodsFoundWithCurrentMinimumArgs > 1) {
            throw new IllegalArgumentException("Cannot resolve method '" + methodName +
                    "' to a unique method. Attempted to resolve to overloaded method with " +
                    "the least number of parameters but there were " +
                    numMethodsFoundWithCurrentMinimumArgs + " candidates.");
        }
        return targetMethod;
    }

    @Nullable
    public static Method resolveSignature(String signature,Class<?> clazz) {
        Assert.hasText(signature,"'signature' must not be empty");
        Assert.notNull(clazz,"Class must not be null");
        int startParen = signature.indexOf('(');
        int endParen = signature.indexOf(')');
        if (startParen > -1 && endParen == -1) {
            throw new IllegalArgumentException("Invalid method signature '" + signature +
                    "': expected closing ')' for args list");
        } else if (startParen == -1 && endParen > -1) {
            throw new IllegalArgumentException("Invalid method signature '" + signature +
                    "': expected opening '(' for args list");
        } else if (startParen == -1) {
            return findMethodWithMinimalParameters(clazz,signature);
        } else {
            String methodName = signature.substring(0,startParen);
            String[] parameterTypeNames =
                    StringUtils.commaDelimitedListToStringArray(signature.substring(startParen + 1,endParen));
            Class<?>[] parameterTypes = new Class<?>[parameterTypeNames.length];
            for (int i = 0;i < parameterTypeNames.length;i ++) {
                String parameterTypeName = parameterTypeNames[i].trim();
                try {
                    parameterTypes[i] = ClassUtils.forName(parameterTypeName,clazz.getClassLoader());
                } catch (Throwable ex) {
                    throw new IllegalArgumentException("Invalid method signature: unable to resolve type [" +
                            parameterTypeName + "] for argument " + i + ". Root cause: " + ex);
                }
            }
            return findMethod(clazz,methodName,parameterTypes);
        }
    }



}
