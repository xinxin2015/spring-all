package org.springframework.beans;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.CollectionFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.*;
import java.security.PrivilegedActionException;
import java.util.*;

public abstract class AbstractNestablePropertyAccessor extends AbstractPropertyAccessor {

    private static final Log logger = LogFactory.getLog(AbstractNestablePropertyAccessor.class);

    private int autoGrowCollectionLimit = Integer.MAX_VALUE;

    @Nullable
    Object wrappedObject;

    private String nestedPath = "";

    @Nullable
    Object rootObject;

    @Nullable
    private Map<String,AbstractNestablePropertyAccessor> nestedPropertyAccessors;

    protected AbstractNestablePropertyAccessor() {
        this(true);
    }

    protected AbstractNestablePropertyAccessor(boolean registerDefaultEditors) {
        if (registerDefaultEditors) {
            registerDefaultEditors();
        }
        this.typeConverterDelegate = new TypeConverterDelegate(this);
    }

    protected AbstractNestablePropertyAccessor(Object object) {
        registerDefaultEditors();
        setWrappedInstance(object);
    }

    protected AbstractNestablePropertyAccessor(Class<?> clazz) {
        registerDefaultEditors();
        setWrappedInstance(BeanUtils.instantiateClass(clazz));
    }

    protected AbstractNestablePropertyAccessor(Object object,String nestedPath,Object rootObject) {
        registerDefaultEditors();
        setWrappedInstance(object,nestedPath,rootObject);
    }

    protected AbstractNestablePropertyAccessor(Object object,String nestedPath,
                                               AbstractNestablePropertyAccessor parent) {
        setWrappedInstance(object,nestedPath,parent.getWrappedInstance());
        setExtractOldValueForEditor(parent.isExtractOldValueForEditor());
        setAutoGrowNestedPaths(parent.isAutoGrowNestedPaths());
        setAutoGrowCollectionLimit(parent.getAutoGrowCollectionLimit());
        setConversionService(parent.getConversionService());
    }

    public void setAutoGrowCollectionLimit(int autoGrowCollectionLimit) {
        this.autoGrowCollectionLimit = autoGrowCollectionLimit;
    }

    public int getAutoGrowCollectionLimit() {
        return autoGrowCollectionLimit;
    }

    public void setWrappedInstance(Object object) {
        setWrappedInstance(object,"",null);
    }

    public void setWrappedInstance(Object object,@Nullable String nestedPath,@Nullable Object rootObject) {
        this.wrappedObject = ObjectUtils.unwrapOptional(object);
        Assert.notNull(this.wrappedObject,"Target object must not be null");
        this.nestedPath = nestedPath != null ? nestedPath : "";
        this.rootObject = !this.nestedPath.isEmpty() ? rootObject : wrappedObject;
        this.nestedPropertyAccessors = null;
        this.typeConverterDelegate = new TypeConverterDelegate(this,this.wrappedObject);
    }

    public final Object getWrappedInstance() {
        Assert.state(this.wrappedObject != null,"No wrapped object");
        return this.wrappedObject;
    }

    public final Class<?> getWrappedClass() {
        return getWrappedInstance().getClass();
    }

    public final String getNestedPath() {
        return this.nestedPath;
    }

    public final Object getRootInstance() {
        Assert.state(this.rootObject != null,"No root object");
        return this.rootObject;
    }

    public final Class<?> getRootClass() {
        return getRootInstance().getClass();
    }

    @Override
    public void setPropertyValue(String propertyName, Object value) throws BeansException {
        AbstractNestablePropertyAccessor nestedPa;
        try {
            nestedPa = getPropertyAccessorForPropertyPath(propertyName);
        } catch (NotReadablePropertyException ex) {
            throw new NotWritablePropertyException(getRootClass(), this.nestedPath + propertyName,
                    "Nested property in path '" + propertyName + "' does not exist", ex);
        }
        PropertyTokenHolder tokens = getPropertyNameTokens(getFinalPath(nestedPa,propertyName));
        nestedPa.setPropertyValue(tokens,new PropertyValue(propertyName,value));
    }

    @Override
    public void setPropertyValue(PropertyValue pv) throws BeansException {
        PropertyTokenHolder tokens = (PropertyTokenHolder) pv.resolvedTokens;
        if (tokens == null) {
            String propertyName = pv.getName();
            AbstractNestablePropertyAccessor nestedPa;
            try {
                nestedPa = getPropertyAccessorForPropertyPath(propertyName);
            } catch (NotReadablePropertyException ex) {
                throw new NotWritablePropertyException(getRootClass(), this.nestedPath + propertyName,
                        "Nested property in path '" + propertyName + "' does not exist", ex);
            }
            tokens = getPropertyNameTokens(getFinalPath(nestedPa,propertyName));
            if (nestedPa == this) {
                pv.getOriginalPropertyValue().resolvedTokens = tokens;
            }
            nestedPa.setPropertyValue(tokens,pv);
        } else {
            setPropertyValue(tokens,pv);
        }
    }

    protected void setPropertyValue(PropertyTokenHolder tokens, PropertyValue pv) throws BeansException {
        if (tokens.keys != null) {
            processKeyedProperty(tokens,pv);
        } else {
            processLocalProperty(tokens,pv);
        }
    }

    @SuppressWarnings("unchecked")
    private void processKeyedProperty(PropertyTokenHolder tokens,PropertyValue pv) {
        Object propValue = getPropertyHoldingValue(tokens);
        PropertyHandler ph = getLocalPropertyHandler(tokens.actualName);
        if (ph == null) {
            throw new InvalidPropertyException(
                    getRootClass(), this.nestedPath + tokens.actualName, "No property handler found");
        }
        Assert.state(tokens.keys != null,"No token keys");
        String lastKey = tokens.keys[tokens.keys.length - 1];
        if (propValue.getClass().isArray()) {
            Class<?> requiredType = propValue.getClass().getComponentType();
            int arrayIndex = Integer.parseInt(lastKey);
            Object oldValue = null;
            try {
                if (isExtractOldValueForEditor() && arrayIndex < Array.getLength(propValue)) {
                    oldValue = Array.get(propValue,arrayIndex);
                }
                Object convertedValue = convertIfNecessary(tokens.canonicalName,oldValue,pv.getValue(),requiredType,
                        ph.nested(tokens.keys.length));
                int length = Array.getLength(propValue);
                if (arrayIndex >= length && arrayIndex < this.autoGrowCollectionLimit) {
                    Class<?> componentType = propValue.getClass().getComponentType();
                    Object newArray = Array.newInstance(componentType, arrayIndex + 1);
                    System.arraycopy(propValue, 0, newArray, 0, length);
                    setPropertyValue(tokens.actualName, newArray);
                    propValue = getPropertyValue(tokens.actualName);
                }
                Array.set(propValue,arrayIndex,convertedValue);
            } catch (IndexOutOfBoundsException ex) {
                throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
                        "Invalid array index in property path '" + tokens.canonicalName + "'", ex);
            }
        } else if (propValue instanceof List) {
            Class<?> requiredType = ph.getCollectionType(tokens.keys.length);
            List<Object> list = (List<Object>) propValue;
            int index = Integer.parseInt(lastKey);
            Object oldValue = null;
            if (isExtractOldValueForEditor() && index < list.size()) {
                oldValue = list.get(index);
            }
            Object convertedValue = convertIfNecessary(tokens.canonicalName,oldValue,pv.getValue(),requiredType,
                    ph.nested(tokens.keys.length));
            int size = list.size();
            if (index >= size && index < this.autoGrowCollectionLimit) {
                for (int i = size;i < index;i ++) {
                    try {
                        list.add(null);
                    } catch (NullPointerException ex) {
                        throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
                                "Cannot set element with index " + index + " in List of size " +
                                        size + ", accessed using property path '" + tokens.canonicalName +
                                        "': List does not support filling up gaps with null elements");
                    }
                }
                list.add(convertedValue);
            } else {
                try {
                    list.set(index,convertedValue);
                } catch (IndexOutOfBoundsException ex) {
                    throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
                            "Invalid list index in property path '" + tokens.canonicalName + "'", ex);
                }
            }
        } else if (propValue instanceof Map) {
            Class<?> mapKeyType = ph.getMapKeyType(tokens.keys.length);
            Class<?> mapValueType = ph.getMapValueType(tokens.keys.length);
            Map<Object, Object> map = (Map<Object, Object>) propValue;
            // IMPORTANT: Do not pass full property name in here - property editors
            // must not kick in for map keys but rather only for map values.
            TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(mapKeyType);
            Object convertedMapKey = convertIfNecessary(null, null, lastKey, mapKeyType, typeDescriptor);
            Object oldValue = null;
            if (isExtractOldValueForEditor()) {
                oldValue = map.get(convertedMapKey);
            }
            // Pass full property name and old value in here, since we want full
            // conversion ability for map values.
            Object convertedMapValue = convertIfNecessary(tokens.canonicalName, oldValue, pv.getValue(),
                    mapValueType, ph.nested(tokens.keys.length));
            map.put(convertedMapKey, convertedMapValue);
        } else {
            throw new InvalidPropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
                    "Property referenced in indexed property path '" + tokens.canonicalName +
                            "' is neither an array nor a List nor a Map; returned value was [" + propValue + "]");
        }
    }

    private Object getPropertyHoldingValue(PropertyTokenHolder tokens) {
        // Apply indexes and map keys: fetch value for all keys but the last one.
        Assert.state(tokens.keys != null,"No token keys");
        PropertyTokenHolder getterTokens = new PropertyTokenHolder(tokens.actualName);
        getterTokens.canonicalName = tokens.canonicalName;
        getterTokens.keys = new String[tokens.keys.length - 1];
        System.arraycopy(tokens.keys,0,getterTokens.keys,0,tokens.keys.length - 1);

        Object propValue;
        try {
            propValue = getPropertyValue(getterTokens);
        } catch (NotReadablePropertyException ex) {
            throw new NotWritablePropertyException(getRootClass(), this.nestedPath + tokens.canonicalName,
                    "Cannot access indexed value in property referenced " +
                            "in indexed property path '" + tokens.canonicalName + "'", ex);
        }

        if (propValue == null) {
            // null map value case
            if (isAutoGrowNestedPaths()) {
                int lastKeyIndex = tokens.canonicalName.lastIndexOf('[');
                getterTokens.canonicalName = tokens.canonicalName.substring(0,lastKeyIndex);
                propValue = setDefaultValue(getterTokens);
            } else {
                throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + tokens.canonicalName,
                        "Cannot access indexed value in property referenced " +
                                "in indexed property path '" + tokens.canonicalName + "': returned null");
            }
        }
        return propValue;
    }

    private void processLocalProperty(PropertyTokenHolder tokens,PropertyValue pv) {
        PropertyHandler ph = getLocalPropertyHandler(tokens.actualName);
        if (ph == null) {
            if (pv.isOptional()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Ignoring optional value for property '" + tokens.actualName +
                            "' - property not found on bean class [" + getRootClass().getName() + "]");
                }
                return;
            } else {
                throw createNotWritablePropertyException(tokens.canonicalName);
            }
        }

        Object oldValue = null;
        try {
            Object originalValue = pv.getValue();
            Object valueToApply = originalValue;
            if (!Boolean.FALSE.equals(pv.conversionNecessary)) {
                if (pv.isConverted()) {
                    valueToApply = pv.getConvertedValue();
                } else {
                    if (isExtractOldValueForEditor() && ph.isReadable()) {
                        try {
                            oldValue = pv.getValue();
                        } catch (Exception ex) {
                            if (ex instanceof PrivilegedActionException) {
                                ex = ((PrivilegedActionException) ex).getException();
                            }
                            if (logger.isDebugEnabled()) {
                                logger.debug("Could not read previous value of property '" +
                                        this.nestedPath + tokens.canonicalName + "'", ex);
                            }
                        }
                    }
                    valueToApply = convertForProperty(tokens.canonicalName,oldValue,originalValue,
                            ph.toTypeDescripter());
                }
                pv.getOriginalPropertyValue().conversionNecessary = valueToApply != originalValue;
            }
            ph.setValue(valueToApply);
        } catch (TypeMismatchException ex) {
            throw ex;
        } catch (InvocationTargetException ex) {
            PropertyChangeEvent propertyChangeEvent = new PropertyChangeEvent(
                    getRootInstance(), this.nestedPath + tokens.canonicalName, oldValue, pv.getValue());
            if (ex.getTargetException() instanceof ClassCastException) {
                throw new TypeMismatchException(propertyChangeEvent, ph.getPropertyType(), ex.getTargetException());
            }
            else {
                Throwable cause = ex.getTargetException();
                if (cause instanceof UndeclaredThrowableException) {
                    // May happen e.g. with Groovy-generated methods
                    cause = cause.getCause();
                }
                throw new MethodInvocationException(propertyChangeEvent, cause);
            }
        } catch (Exception ex) {
            PropertyChangeEvent pce = new PropertyChangeEvent(
                    getRootInstance(), this.nestedPath + tokens.canonicalName, oldValue, pv.getValue());
            throw new MethodInvocationException(pce, ex);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected Object getPropertyValue(PropertyTokenHolder tokens) throws BeansException {
        String propertyName = tokens.canonicalName;
        String actualName = tokens.actualName;
        PropertyHandler ph = getLocalPropertyHandler(actualName);
        if (ph == null || !ph.isReadable()) {
            throw new NotReadablePropertyException(getRootClass(), this.nestedPath + propertyName);
        }
        try {
            Object value = ph.getValue();
            if (tokens.keys != null) {
                if (value == null) {
                    if (isAutoGrowNestedPaths()) {
                        value = setDefaultValue(new PropertyTokenHolder(tokens.actualName));
                    } else {
                        throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + propertyName,
                                "Cannot access indexed value of property referenced in indexed " +
                                        "property path '" + propertyName + "': returned null");
                    }
                }
                StringBuilder indexedPropertyName = new StringBuilder(tokens.actualName);
                // apply indexes and map keys
                for (int i = 0;i < tokens.keys.length;i ++) {
                    String key = tokens.keys[i];
                    if (value == null) {
                        throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + propertyName,
                                "Cannot access indexed value of property referenced in indexed " +
                                        "property path '" + propertyName + "': returned null");
                    } else if (value.getClass().isArray()) {
                        int index = Integer.parseInt(key);
                        value = growArrayIfNecessary(value,index,indexedPropertyName.toString());
                        value = Array.get(value,index);
                    } else if (value instanceof List) {
                        int index = Integer.parseInt(key);
                        List<Object> list = (List<Object>) value;
                        growCollectionIfNecessary(list,index,indexedPropertyName.toString(),ph,i + 1);
                        value = list.get(index);
                    } else if (value instanceof Set) {
                        // Apply index to Iterator in case of a Set.
                        Set<Object> set = (Set<Object>) value;
                        int index = Integer.parseInt(key);
                        if (index < 0 || index > set.size()) {
                            throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
                                    "Cannot get element with index " + index + " from Set of size " +
                                            set.size() + ", accessed using property path '" + propertyName + "'");
                        }
                        Iterator<Object> it = set.iterator();
                        for (int j = 0;it.hasNext();j ++) {
                            Object elem = it.next();
                            if (j == index) {
                                value = elem;
                                break;
                            }
                        }
                    } else if (value instanceof Map) {
                        Map<Object,Object> map = (Map<Object, Object>) value;
                        Class<?> mapKeyType = ph.getResolvableType().getNested(i + 1).asMap().resolveGeneric(0);
                        // IMPORTANT: Do not pass full property name in here - property editors
                        // must not kick in for map keys but rather only for map values.
                        TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(mapKeyType);
                        Object convertedMapKey = convertIfNecessary(null,null,key,mapKeyType,typeDescriptor);
                        value = map.get(convertedMapKey);
                    } else {
                        throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
                                "Property referenced in indexed property path '" + propertyName +
                                        "' is neither an array nor a List nor a Set nor a Map; returned value was [" + value + "]");
                    }
                    indexedPropertyName.append(PROPERTY_KEY_PREFIX).append(key).append(PROPERTY_KEY_SUFFIX);
                }
            }
            return value;
        } catch (IndexOutOfBoundsException ex) {
            throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
                    "Index of out of bounds in property path '" + propertyName + "'", ex);
        } catch (NumberFormatException | TypeMismatchException ex) {
            throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
                    "Invalid index in property path '" + propertyName + "'", ex);
        } catch (InvocationTargetException ex) {
            throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
                    "Getter for property '" + actualName + "' threw exception", ex);
        } catch (Exception ex) {
            throw new InvalidPropertyException(getRootClass(), this.nestedPath + propertyName,
                    "Illegal attempt to get property '" + actualName + "' threw exception", ex);
        }
    }

    @Nullable
    protected PropertyHandler getPropertyHandler(String propertyName) throws BeansException {
        Assert.notNull(propertyName,"Property name must not be null");
        AbstractNestablePropertyAccessor nestedPa = getPropertyAccessorForPropertyPath(propertyName);
        return nestedPa.getLocalPropertyHandler(getFinalPath(nestedPa,propertyName));
    }

    @Nullable
    protected abstract PropertyHandler getLocalPropertyHandler(String propertyName);

    protected abstract AbstractNestablePropertyAccessor newNestedPropertyAccessor(Object object,String nestedPath);

    protected abstract NotWritablePropertyException createNotWritablePropertyException(String propertyName);

    private Object growArrayIfNecessary(Object array,int index,String name) {
        if (!isAutoGrowNestedPaths()) {
            return array;
        }
        int length = Array.getLength(array);
        if (index >= length && index < this.autoGrowCollectionLimit) {
            Class<?> componentType = array.getClass().getComponentType();
            Object newArray = Array.newInstance(componentType,index + 1);
            System.arraycopy(array,0,newArray,0,length);
            for (int i = length;i < Array.getLength(array);i ++) {
                Array.set(newArray,i,newValue(componentType,null,name));
            }
            setPropertyValue(name,newArray);
            Object defaultValue = getPropertyValue(name);
            Assert.state(defaultValue != null,"Default value must not be null");
            return defaultValue;
        } else {
            return array;
        }
    }

    private void growCollectionIfNecessary(Collection<Object> collection,int index,String name,PropertyHandler ph,
                                           int nestingLevel) {
        if (!isAutoGrowNestedPaths()) {
            return;
        }
        int size = collection.size();
        if (index >= size && index < this.autoGrowCollectionLimit) {
            Class<?> elementType = ph.getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric();
            if (elementType != null) {
                for (int i = collection.size();i < index + 1;i ++) {
                    collection.add(newValue(elementType,null,name));
                }
            }
        }
    }

    protected final String getFinalPath(AbstractNestablePropertyAccessor pa,String nestedPath) {
        if (pa == this) {
            return nestedPath;
        }
        return nestedPath.substring(PropertyAccessorUtils.getLastNestedPropertySeparatorIndex(nestedPath) + 1);
    }

    protected AbstractNestablePropertyAccessor getPropertyAccessorForPropertyPath(String propertyPath) {
        int pos = PropertyAccessorUtils.getFirstNestedPropertySeparatorIndex(propertyPath);
        // Handle nested properties recursively.
        if (pos > -1) {
            String nestedProperty = propertyPath.substring(0,pos);
            String nestedPath = propertyPath.substring(pos + 1);
            AbstractNestablePropertyAccessor nestedPa = getNestedPropertyAccessor(nestedProperty);
            return nestedPa.getPropertyAccessorForPropertyPath(nestedPath);
        } else {
            return this;
        }
    }

    private AbstractNestablePropertyAccessor getNestedPropertyAccessor(String nestedProperty) {
        if (this.nestedPropertyAccessors == null) {
            this.nestedPropertyAccessors = new HashMap<>();
        }
        // Get value of bean property.
        PropertyTokenHolder tokens = getPropertyNameTokens(nestedProperty);
        String canonicalName = tokens.canonicalName;
        Object value = getPropertyValue(tokens);
        if (value == null || (value instanceof Optional && !((Optional)value).isPresent())) {
            if (isAutoGrowNestedPaths()) {
                value = setDefaultValue(tokens);
            } else {
                throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + canonicalName);
            }
        }
        // Lookup cached sub-PropertyAccessor, create new one if not found.
        AbstractNestablePropertyAccessor nestedPa = this.nestedPropertyAccessors.get(canonicalName);
        if (nestedPa == null || nestedPa.getWrappedInstance() != ObjectUtils.unwrapOptional(value)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Creating new nested " + getClass().getSimpleName() + " for property '" + canonicalName + "'");
            }
            nestedPa = newNestedPropertyAccessor(value,this.nestedPath + canonicalName + NESTED_PROPERTY_SEPARATOR);
            // Inherit all type-specific PropertyEditors.
            copyDefaultEditorTo(nestedPa);
            copyCustomEditorsTo(nestedPa,canonicalName);
            this.nestedPropertyAccessors.put(canonicalName,nestedPa);
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Using cached nested property accessor for property '" + canonicalName + "'");
            }
        }
        return nestedPa;
    }

    private Object setDefaultValue(PropertyTokenHolder tokens) {
        PropertyValue pv = createDefaultPropertyValue(tokens);
        setPropertyValue(tokens,pv);
        Object defaultValue = getPropertyValue(tokens);
        Assert.state(defaultValue != null,"Default value must not be null");
        return defaultValue;
    }

    private PropertyValue createDefaultPropertyValue(PropertyTokenHolder tokens) {
        TypeDescriptor desc = getPropertyTypeDescriptor(tokens.canonicalName);
        if (desc == null) {
            throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + tokens.canonicalName,
                    "Could not determine property type for auto-growing a default value");
        }
        Object defaultValue = newValue(desc.getType(),desc,tokens.canonicalName);
        return new PropertyValue(tokens.canonicalName,defaultValue);
    }

    private Object newValue(Class<?> type,@Nullable TypeDescriptor desc,String name) {
        try {
            if (type.isArray()) {
                Class<?> componentType = type.getComponentType();
                //TODO - only handles 2-dimensional keys
                if (componentType.isArray()) {
                    Object array = Array.newInstance(componentType,1);
                    Array.set(array,0,Array.newInstance(componentType.getComponentType(),0));
                    return array;
                } else {
                    return Array.newInstance(componentType,0);
                }
            } else if (Collection.class.isAssignableFrom(type)) {
                TypeDescriptor elementDesc = desc != null ? desc.getElementTypeDescriptor() : null;
                return CollectionFactory.createCollection(type,elementDesc != null ? elementDesc.getType() : null,16);
            } else if (Map.class.isAssignableFrom(type)) {
                TypeDescriptor keyDesc = desc != null ? desc.getMapKeyTypeDescriptor() : null;
                return CollectionFactory.createMap(type,keyDesc != null ? keyDesc.getType() : null,16);
            } else {
                Constructor<?> ctor = type.getDeclaredConstructor();
                if (Modifier.isPrivate(ctor.getModifiers())) {
                    throw new IllegalAccessException("Auto-growing not allowed with private constructor: " + ctor);
                }
                return BeanUtils.instantiateClass(ctor);
            }
        } catch (Throwable ex) {
            throw new NullValueInNestedPathException(getRootClass(), this.nestedPath + name,
                    "Could not instantiate property type [" + type.getName() + "] to auto-grow nested property path", ex);
        }
    }

    private PropertyTokenHolder getPropertyNameTokens(String propertyName) {
        String actualName = null;
        List<String> keys = new ArrayList<>(2);
        int searchIndex = 0;
        while (searchIndex != -1) {
            int keyStart = propertyName.indexOf(PROPERTY_KEY_PREFIX,searchIndex);
            searchIndex = -1;
            if (keyStart != -1) {
                int keyEnd = propertyName.indexOf(PROPERTY_KEY_SUFFIX,keyStart + PROPERTY_KEY_PREFIX.length());
                if (keyEnd != -1) {
                    if (actualName == null) {
                        actualName = propertyName.substring(0,keyStart);
                    }
                    String key = propertyName.substring(keyStart + PROPERTY_KEY_PREFIX.length(),keyEnd);
                    if (key.length() > 1 && (key.startsWith("'") && key.endsWith("'")) ||
                            (key.startsWith("\"") && key.endsWith("\""))) {
                        key = key.substring(1, key.length() - 1);
                    }
                    keys.add(key);
                    searchIndex = keyEnd + PROPERTY_KEY_SUFFIX.length();
                }
            }
        }
        PropertyTokenHolder tokens = new PropertyTokenHolder(actualName != null ? actualName : propertyName);
        if (!keys.isEmpty()) {
            tokens.canonicalName += PROPERTY_KEY_PREFIX +
                    StringUtils.collectionToDelimitedString(keys, PROPERTY_KEY_SUFFIX + PROPERTY_KEY_PREFIX) +
                    PROPERTY_KEY_SUFFIX;
            tokens.keys = StringUtils.toStringArray(keys);
        }
        return tokens;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        if (this.wrappedObject != null) {
            sb.append(": wrapping object [").append(ObjectUtils.identityToString(this.wrappedObject)).append("]");
        }
        else {
            sb.append(": no wrapped object set");
        }
        return sb.toString();
    }

    protected abstract static class PropertyHandler {

        private final Class<?> propertyType;

        private final boolean readable;

        private final boolean writable;

        public PropertyTokenHolder(Class<?> propertyType,boolean readable,boolean writable) {
            this.propertyType = propertyType;
            this.readable = readable;
            this.writable = writable;
        }

        public Class<?> getPropertyType() {
            return propertyType;
        }

        public boolean isReadable() {
            return readable;
        }

        public boolean isWritable() {
            return writable;
        }

        public abstract TypeDescriptor toTypeDescripter();

        public abstract ResolvableType getResolvableType();

        @Nullable
        public Class<?> getMapKeyType(int nestingLevel) {
            return getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(0);
        }

        @Nullable
        public Class<?> getMapValueType(int nestingLevel) {
            return getResolvableType().getNested(nestingLevel).asMap().resolveGeneric(1);
        }

        @Nullable
        public Class<?> getCollectionType(int nestingLevel) {
            return getResolvableType().getNested(nestingLevel).asCollection().resolveGeneric();
        }

        @Nullable
        public abstract TypeDescriptor nested(int level);

        @Nullable
        public abstract Object getValue() throws Exception;

        public abstract void setValue(@Nullable Object value) throws Exception;
    }

    protected static class PropertyTokenHolder {

        PropertyTokenHolder(String name) {
            this.actualName = name;
            this.canonicalName = name;
        }

        String actualName;

        String canonicalName;

        @Nullable
        String[] keys;

    }
}
