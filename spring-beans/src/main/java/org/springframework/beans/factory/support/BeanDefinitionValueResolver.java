package org.springframework.beans.factory.support;


import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.*;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class BeanDefinitionValueResolver {

    private final AbstractBeanFactory beanFactory;

    private final String beanName;

    private final BeanDefinition beanDefinition;

    private final TypeConverter typeConverter;

    BeanDefinitionValueResolver(AbstractBeanFactory beanFactory, String beanName, BeanDefinition beanDefinition,
                                TypeConverter typeConverter) {
        this.beanFactory = beanFactory;
        this.beanName = beanName;
        this.beanDefinition = beanDefinition;
        this.typeConverter = typeConverter;
    }

    @Nullable
    Object resolveValueIfNecessary(Object argName, @Nullable Object value) {
        // We must check each value to see whether it requires a runtime reference
        // to another bean to be resolved.
        if (value instanceof RuntimeBeanReference) {
            RuntimeBeanReference ref = (RuntimeBeanReference) value;
            return resolveReference(argName, ref);
        } else if (value instanceof RuntimeBeanNameReference) {
            String refName = ((RuntimeBeanNameReference) value).getBeanName();
            refName = String.valueOf(doEvaluate(refName));
            if (!this.beanFactory.containsBean(refName)) {
                throw new BeanDefinitionStoreException(
                        "Invalid bean name '" + refName + "' in bean reference for " + argName);
            }
            return refName;
        } else if (value instanceof BeanDefinitionHolder) {
            // Resolve BeanDefinitionHolder: contains BeanDefinition with name and aliases.
            BeanDefinitionHolder bdHolder = (BeanDefinitionHolder) value;
            return resolveInnerBean(argName, bdHolder.getBeanName(), bdHolder.getBeanDefinition());
        } else if (value instanceof BeanDefinition) {
            // Resolve plain BeanDefinition, without contained name: use dummy name.
            BeanDefinition bd = (BeanDefinition) value;
            String innerBeanName =
                    "(inner bean)" + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR + ObjectUtils.getIdentityHexString(bd);
            return resolveInnerBean(argName, innerBeanName, bd);
        } else if (value instanceof ManagedArray) {
            // May need to resolve contained runtime reference.
            ManagedArray array = (ManagedArray) value;
            Class<?> elementType = array.resolvedElementType;
            if (elementType == null) {
                String elementTypeName = array.getElementTypeName();
                if (StringUtils.hasText(elementTypeName)) {
                    try {
                        elementType = ClassUtils.forName(elementTypeName, this.beanFactory.getBeanClassLoader());
                        array.resolvedElementType = elementType;
                    } catch (Throwable ex) {
                        // Improve the message by showing the context.
                        throw new BeanCreationException(
                                this.beanDefinition.getResourceDescription(), this.beanName,
                                "Error resolving array type for " + argName, ex);
                    }
                } else {
                    elementType = Object.class;
                }
            }
            return resolveManagedArray(argName, (List<?>) value, elementType);
        } else if (value instanceof ManagedList) {
            // May need to resolve contained runtime references.
            return resolveManagedList(argName, (List<?>) value);
        }
    }

    @Nullable
    private Object doEvaluate(@Nullable String value) {
        return this.beanFactory.evaluateBeanDefinitionString(value, this.beanDefinition);
    }

    @Nullable
    protected Class<?> resolveTargetType(TypedStringValue value) throws ClassNotFoundException {
        if (value.hasTargetType()) {
            return value.getTargetType();
        }
        return value.resolveTargetType(this.beanFactory.getBeanClassLoader());
    }

    @Nullable
    private Object resolveInnerBean(Object argName, String innerBeanName, BeanDefinition innerBd) {
        RootBeanDefinition mbd = null;
        try {
            mbd = this.beanFactory.getMergedBeanDefinition(innerBeanName, innerBd, this.beanDefinition);
            // Check given bean name whether it is unique. If not already unique,
            // add counter - increasing the counter until the name is unique.
            String actualInnerBeanName = innerBeanName;
            if (mbd.isSingleton()) {
                actualInnerBeanName = adaptInnerBeanName(innerBeanName);
            }
            this.beanFactory.registerContainedBean(actualInnerBeanName, this.beanName);
            // Guarantee initialization of beans that the inner bean depends on.
            String[] dependsOn = mbd.getDependsOn();
            if (dependsOn != null) {
                for (String dependsOnBean : dependsOn) {
                    this.beanFactory.registerDependentBean(dependsOnBean, actualInnerBeanName);
                    this.beanFactory.getBean(dependsOnBean);
                }
            }
            // Actually create the inner bean instance now...
            Object innerBean = this.beanFactory.createBean(actualInnerBeanName, mbd, null);
            if (innerBean instanceof FactoryBean) {
                boolean synthetic = mbd.isSynthetic();
                innerBean = this.beanFactory.getObjectFromFactoryBean((FactoryBean<?>) innerBean, actualInnerBeanName
                        , !synthetic);
            }
            if (innerBean instanceof NullBean) {
                innerBean = null;
            }
            return innerBean;
        } catch (BeansException ex) {
            throw new BeanCreationException(
                    this.beanDefinition.getResourceDescription(), this.beanName,
                    "Cannot create inner bean '" + innerBeanName + "' " +
                            (mbd != null && mbd.getBeanClassName() != null ? "of type [" + mbd.getBeanClassName() +
                                    "] " : "") +
                            "while setting " + argName, ex);
        }
    }

    private String adaptInnerBeanName(String innerBeanName) {
        String actualInnerBeanName = innerBeanName;
        int counter = 0;
        while (this.beanFactory.isBeanNameInUse(actualInnerBeanName)) {
            counter++;
            actualInnerBeanName = innerBeanName + BeanFactoryUtils.GENERATED_BEAN_NAME_SEPARATOR + counter;
        }
        return actualInnerBeanName;
    }

    @Nullable
    private Object resolveReference(Object argName, RuntimeBeanReference ref) {
        try {
            Object bean;
            String refName = ref.getBeanName();
            refName = String.valueOf(doEvaluate(refName));
            if (ref.isToParent()) {
                if (this.beanFactory.getParentBeanFactory() == null) {
                    throw new BeanCreationException(
                            this.beanDefinition.getResourceDescription(), this.beanName,
                            "Can't resolve reference to bean '" + refName +
                                    "' in parent factory: no parent factory available");
                }
                bean = this.beanFactory.getParentBeanFactory().getBean(refName);
            } else {
                bean = this.beanFactory.getBean(refName);
                this.beanFactory.registerDependentBean(refName, this.beanName);
            }
            if (bean instanceof NullBean) {
                bean = null;
            }
            return bean;
        } catch (BeansException ex) {
            throw new BeanCreationException(
                    this.beanDefinition.getResourceDescription(), this.beanName,
                    "Cannot resolve reference to bean '" + ref.getBeanName() + "' while setting " + argName, ex);
        }
    }

    private Object resolveManagedArray(Object argName, List<?> ml, Class<?> elementType) {
        Object resolved = Array.newInstance(elementType, ml.size());
        for (int i = 0; i < ml.size(); i++) {
            Array.set(resolved, i, resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
        }
        return resolved;
    }

    private List<?> resolveManagedList(Object argName, List<?> ml) {
        List<Object> resolved = new ArrayList<>(ml.size());
        for (int i = 0; i < ml.size(); i++) {
            resolved.add(
                    resolveValueIfNecessary(new KeyedArgName(argName, i), ml.get(i)));
        }
        return resolved;
    }

    private Set<?> resolveManagedSet(Object argName, Set<?> ms) {
        Set<Object> resolved = new LinkedHashSet<>(ms.size());
        int i = 0;
        for (Object m : ms) {
            resolved.add(resolveValueIfNecessary(new KeyedArgName(argName, i), m));
            i++;
        }
        return resolved;
    }

    private static class KeyedArgName {

        private final Object argName;

        private final Object key;

        private KeyedArgName(Object argName, Object key) {
            this.argName = argName;
            this.key = key;
        }

        @Override
        public String toString() {
            return this.argName + " with key " + BeanWrapper.PROPERTY_KEY_PREFIX +
                    this.key + BeanWrapper.PROPERTY_KEY_SUFFIX;
        }
    }

}
