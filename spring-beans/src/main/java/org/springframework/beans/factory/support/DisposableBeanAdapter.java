package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that implements the {@link DisposableBean} and {@link Runnable}
 * interfaces performing various destruction steps on a given bean instance:
 * <ul>
 * <li>DestructionAwareBeanPostProcessors;
 * <li>the bean implementing DisposableBean itself;
 * <li>a custom destroy method specified on the bean definition.
 * </ul>
 *
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Stephane Nicoll
 * @since 2.0
 * @see AbstractBeanFactory
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
 * @see AbstractBeanDefinition#getDestroyMethodName()
 */
@SuppressWarnings("serial")
class DisposableBeanAdapter implements DisposableBean, Runnable, Serializable {

    private static final String CLOSE_METHOD_NAME = "close";

    private static final String SHUTDOWN_METHOD_NAME = "shutdown";

    private static final Log logger = LogFactory.getLog(DisposableBeanAdapter.class);

    private final Object bean;

    private final String beanName;

    private final boolean invokeDisposableBean;

    private final boolean nonPublicAccessAllowed;

    @Nullable
    private final AccessControlContext acc;

    @Nullable
    private String destroyMethodName;

    @Nullable
    private transient Method destroyMethod;

    @Nullable
    private List<DestructionAwareBeanPostProcessor> beanPostProcessors;

    DisposableBeanAdapter(Object bean, String beanName, RootBeanDefinition beanDefinition,
                                 List<BeanPostProcessor> postProcessors,
                                 @Nullable AccessControlContext acc) {
        Assert.notNull(bean,"Disposable bean must not be null");
        this.bean = bean;
        this.beanName = beanName;
        this.invokeDisposableBean =
                this.bean instanceof DisposableBean && !beanDefinition.isExternallyManagedDestroyMethod("destroy");
        this.nonPublicAccessAllowed = beanDefinition.isNonPublicAccessAllowed();
        this.acc = acc;
        String destroyMethodName = inferDestroyMethodIfNecessary(bean,beanDefinition);
        if (destroyMethodName != null && !(this.invokeDisposableBean && "destroy".equals(destroyMethodName)) &&
                !beanDefinition.isExternallyManagedDestroyMethod(destroyMethodName)) {
            this.destroyMethodName = destroyMethodName;
            this.destroyMethod = determineDestroyMethod(destroyMethodName);
            if (this.destroyMethod == null) {
                if (beanDefinition.isEnforceDestroyMethod()) {
                    throw new BeanDefinitionValidationException("Could not find a destroy method named '" +
                            destroyMethodName + "' on bean with name '" + beanName + "'");
                }
            } else {
                Class<?>[] paramsTypes = this.destroyMethod.getParameterTypes();
                if (paramsTypes.length > 1) {
                    throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
                            beanName + "' has more than one parameter - not supported as destroy method");
                } else if (paramsTypes.length == 1 && boolean.class != paramsTypes[0]) {
                    throw new BeanDefinitionValidationException("Method '" + destroyMethodName + "' of bean '" +
                            beanName + "' has a non-boolean parameter - not supported as destroy method");
                }
            }
        }
        this.beanPostProcessors = filterPostProcessors(postProcessors,bean);
    }

    @Nullable
    private String inferDestroyMethodIfNecessary(Object bean,RootBeanDefinition beanDefinition) {
        String destroyMethodName = beanDefinition.getDestroyMethodName();
        if (AbstractBeanDefinition.INFER_METHOD.equals(destroyMethodName) ||
                (destroyMethodName == null && bean instanceof AutoCloseable)) {
            // Only perform destroy method inference or Closeable detection
            // in case of the bean not explicitly implementing DisposableBean
            if (!(bean instanceof DisposableBean)) {
                try {
                    return bean.getClass().getMethod(CLOSE_METHOD_NAME).getName();
                } catch (NoSuchMethodException ex) {
                    try {
                        return bean.getClass().getMethod(SHUTDOWN_METHOD_NAME).getName();
                    } catch (NoSuchMethodException ignore) {

                    }
                }
            }
            return null;
        }
        return StringUtils.hasLength(destroyMethodName) ? destroyMethodName : null;
    }

    @Nullable
    private List<DestructionAwareBeanPostProcessor> filterPostProcessors(List<BeanPostProcessor> processors,Object bean) {
        List<DestructionAwareBeanPostProcessor> filteredPostProcessors = null;
        if (!CollectionUtils.isEmpty(processors)) {
            filteredPostProcessors = new ArrayList<>(processors.size());
            for (BeanPostProcessor processor : processors) {
                if (processor instanceof DestructionAwareBeanPostProcessor) {
                    DestructionAwareBeanPostProcessor dabpp = (DestructionAwareBeanPostProcessor) processor;
                    if (dabpp.requiresDestruction(bean)) {
                        filteredPostProcessors.add(dabpp);
                    }
                }
            }
        }
        return filteredPostProcessors;
    }

    @Override
    public void run() {
        destroy();
    }

    @Override
    public void destroy() {

    }

    @Nullable
    private Method determineDestroyMethod(String name) {
        try {
            if (System.getSecurityManager() != null) {
                return AccessController.doPrivileged((PrivilegedAction<Method>) () -> findDestroyMethod(name));
            } else {
                return findDestroyMethod(name);
            }
        } catch (IllegalArgumentException ex) {
            throw new BeanDefinitionValidationException("Could not find unique destroy method on bean with name '" +
                    this.beanName + ": " + ex.getMessage());
        }
    }

    @Nullable
    private Method findDestroyMethod(String name) {
        return this.nonPublicAccessAllowed ?
                BeanUtils.findMethodWithMinimalParameters(this.bean.getClass(),name) :
                BeanUtils.findMethodWithMinimalParameters(this.bean.getClass().getMethods(),name);
    }
}
