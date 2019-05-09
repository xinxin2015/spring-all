package org.springframework.beans.factory;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BeanFactoryUtils {

    public static final String GENERATED_BEAN_NAME_SEPARATOR = "#";

    private static final Map<String,String> transformedBeanNameCache = new ConcurrentHashMap<>();

    public static boolean isFactoryDereference(@Nullable String name) {
        return name != null && name.startsWith(BeanFactory.FACTORY_BEAN_PREFIX);
    }

    public static String transformedBeanName(String name) {
        Assert.notNull(name,"'name' must not be null");
        if (!name.startsWith(BeanFactory.FACTORY_BEAN_PREFIX)) {
            return name;
        }
        return transformedBeanNameCache.computeIfAbsent(name,beanName -> {
            do {
                beanName = beanName.substring(BeanFactory.FACTORY_BEAN_PREFIX.length());
            } while (beanName.startsWith(BeanFactory.FACTORY_BEAN_PREFIX));
            return beanName;
        });
    }

    public static boolean isGeneratedBeanName(@Nullable String name) {
        return name != null && name.contains(GENERATED_BEAN_NAME_SEPARATOR);
    }

    public static String originalBeanName(String name) {
        Assert.notNull(name,"'name' must not be null");
        int separatorIndex = name.indexOf(GENERATED_BEAN_NAME_SEPARATOR);
        return separatorIndex != -1 ? name.substring(0,separatorIndex) : name;
    }

    private static <T> T uniqueBean(Class<T> type,Map<String,T> matchingBeans) {
        int count = matchingBeans.size();
        if (count == 1) {
            return matchingBeans.values().iterator().next();
        } else if (count > 1) {
            throw new NoUniqueBeanDefinitionException(type, matchingBeans.keySet());
        } else {
            throw new NoSuchBeanDefinitionException(type);
        }
    }

}
