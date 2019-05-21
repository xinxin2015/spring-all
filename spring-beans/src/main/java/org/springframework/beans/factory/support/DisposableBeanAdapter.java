package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
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

    @Override
    public void run() {

    }

    @Override
    public void destroy() throws Exception {

    }
}
