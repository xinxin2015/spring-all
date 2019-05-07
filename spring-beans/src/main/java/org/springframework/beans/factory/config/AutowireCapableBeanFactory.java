package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;

/**
 * Extension of the {@link org.springframework.beans.factory.BeanFactory}
 * interface to be implemented by bean factories that are capable of
 * autowiring, provided that they want to expose this functionality for
 * existing bean instances.
 *
 * <p>This subinterface of BeanFactory is not meant to be used in normal
 * application code: stick to {@link org.springframework.beans.factory.BeanFactory}
 * or {@link org.springframework.beans.factory.ListableBeanFactory} for
 * typical use cases.
 *
 * <p>Integration code for other frameworks can leverage this interface to
 * wire and populate existing bean instances that Spring does not control
 * the lifecycle of. This is particularly useful for WebWork Actions and
 * Tapestry Page objects, for example.
 *
 * <p>Note that this interface is not implemented by
 * {@link org.springframework.context.ApplicationContext} facades,
 * as it is hardly ever used by application code. That said, it is available
 * from an application context too, accessible through ApplicationContext's
 * {@link org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()}
 * method.
 *
 * <p>You may also implement the {@link org.springframework.beans.factory.BeanFactoryAware}
 * interface, which exposes the internal BeanFactory even when running in an
 * ApplicationContext, to get access to an AutowireCapableBeanFactory:
 * simply cast the passed-in BeanFactory to AutowireCapableBeanFactory.
 *
 * @author Juergen Hoeller
 * @since 04.12.2003
 * @see org.springframework.beans.factory.BeanFactoryAware
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory
 * @see org.springframework.context.ApplicationContext#getAutowireCapableBeanFactory()
 */
public interface AutowireCapableBeanFactory extends BeanFactory {

    int AUTOWIRE_NO = 0;

    int AUTOWIRE_BY_NAME = 1;

    int AUTOWIRE_BY_TYPE = 2;

    int AUTOWIRE_CONSTRUCTOR = 3;

    @Deprecated
    int AUTOWIRE_AUTODETECT = 4;

    String ORIGINAL_INSTANCE_SUFFIX = ".ORIGINAL";

    <T> T createBean(Class<T> beanClass) throws BeansException;

    void autowireBean(Object existingBean) throws BeansException;

    Object configureBean(Object existingBean,String beanName) throws BeansException;

    Object creatBean(Class<?> beanClass,int autowireMode,boolean dependencyCheck) throws BeansException;

    Object autowire(Class<?> beanClass,int autowireMode,boolean dependencyCheck) throws BeansException;

    void autowireBeanProperties(Object existingBean,int autowireMode,boolean dependencyCheck) throws BeansException;

    void applyBeanPropertyValues(Object existingBean,String beanName) throws BeansException;

    Object initializeBean(Object existingBean,String beanName) throws BeansException;

    Object applyBeanPostProcessorsBeforeInitialization(Object existingBean,String beanName) throws BeansException;

    Object applyBeanPostProcessorsAfterInitialization(Object existingBean,String beanName) throws BeansException;

    void destroyBean(Object existingBean);

    <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException;

}
