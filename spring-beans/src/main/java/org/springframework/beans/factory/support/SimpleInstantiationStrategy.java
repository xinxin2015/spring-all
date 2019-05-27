package org.springframework.beans.factory.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Simple object instantiation strategy for use in a BeanFactory.
 *
 * <p>Does not support Method Injection, although it provides hooks for subclasses
 * to override to add Method Injection support, for example by overriding methods.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */
public class SimpleInstantiationStrategy implements InstantiationStrategy {
    @Override
    public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner) throws BeansException {
        return null;
    }

    @Override
    public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner, Constructor<?> ctor, Object... args) throws BeansException {
        return null;
    }

    @Override
    public Object instantiate(RootBeanDefinition bd, String beanName, BeanFactory owner, Object factoryBean,
                              Method factoryMethod, Object... args) throws BeansException {
        return null;
    }
}
