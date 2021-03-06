package org.springframework.beans.factory.support;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.Mergeable;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tag collection class used to hold managed Set values, which may
 * include runtime bean references (to be resolved into bean objects).
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 21.01.2004
 * @param <E> the element type
 */
@SuppressWarnings("serial")
public class ManagedSet<E> extends LinkedHashSet<E> implements Mergeable, BeanMetadataElement {

    @Nullable
    private Object source;

    @Nullable
    private String elementTypeName;

    private boolean mergeEnabled;

    public ManagedSet() {

    }

    public ManagedSet(int initialCapacity) {
        super(initialCapacity);
    }

    /**
     * Set the configuration source {@code Object} for this metadata element.
     * <p>The exact type of the object will depend on the configuration mechanism used.
     */
    public void setSource(@Nullable Object source) {
        this.source = source;
    }

    @Override
    @Nullable
    public Object getSource() {
        return this.source;
    }

    /**
     * Set the default element type name (class name) to be used for this set.
     */
    public void setElementTypeName(@Nullable String elementTypeName) {
        this.elementTypeName = elementTypeName;
    }

    /**
     * Return the default element type name (class name) to be used for this set.
     */
    @Nullable
    public String getElementTypeName() {
        return elementTypeName;
    }

    /**
     * Set whether merging should be enabled for this collection,
     * in case of a 'parent' collection value being present.
     */
    public void setMergeEnabled(boolean mergeEnabled) {
        this.mergeEnabled = mergeEnabled;
    }

    @Override
    public boolean isMergeEnabled() {
        return this.mergeEnabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object merge(Object parent) {
        if (!this.mergeEnabled) {
            throw new IllegalStateException("Not allowed to merge when the 'mergeEnabled' property is set to 'false'");
        }
        if (parent == null) {
            return this;
        }
        if (!(parent instanceof Set)) {
            throw new IllegalArgumentException("Cannot merge with object of type [" + parent.getClass() + "]");
        }
        Set<E> merged = new ManagedSet<>();
        merged.addAll((Collection<? extends E>) parent);
        merged.addAll(this);
        return merged;
    }
}
