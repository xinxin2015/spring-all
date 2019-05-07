package org.springframework.beans;

import org.springframework.lang.Nullable;

import java.io.Serializable;

public class PropertyValue extends BeanMetadataAttributeAccessor implements Serializable {

    private final String name;

    @Nullable
    private final Object value;

    private boolean optional = false;

    private boolean converted = false;

    @Nullable
    private Object convertedValue;



}
