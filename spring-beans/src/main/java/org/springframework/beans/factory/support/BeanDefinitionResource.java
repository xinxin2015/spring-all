package org.springframework.beans.factory.support;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.io.AbstractResource;
import org.springframework.util.Assert;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Descriptive {@link org.springframework.core.io.Resource} wrapper for
 * a {@link org.springframework.beans.factory.config.BeanDefinition}.
 *
 * @author Juergen Hoeller
 * @since 2.5.2
 * @see org.springframework.core.io.DescriptiveResource
 */
class BeanDefinitionResource extends AbstractResource {

    private final BeanDefinition beanDefinition;

    /**
     * Create a new BeanDefinitionResource.
     * @param beanDefinition the BeanDefinition object to wrap
     */
    BeanDefinitionResource(BeanDefinition beanDefinition) {
        Assert.notNull(beanDefinition,"BeanDefinition must not be null");
        this.beanDefinition = beanDefinition;
    }

    /**
     * Return the wrapped BeanDefinition object.
     */
    public BeanDefinition getBeanDefinition() {
        return beanDefinition;
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean isReadable() {
        return false;
    }

    @Override
    public String getDescription() {
        return "BeanDefinition defined in " + this.beanDefinition.getResourceDescription();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new FileNotFoundException(
                "Resource cannot be opened because it points to " + getDescription());
    }

    /**
     * This implementation compares the underlying BeanDefinition.
     */
    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof BeanDefinitionResource &&
                ((BeanDefinitionResource) other).beanDefinition.equals(this.beanDefinition)));
    }

    /**
     * This implementation returns the hash code of the underlying BeanDefinition.
     */
    @Override
    public int hashCode() {
        return this.beanDefinition.hashCode();
    }
}
