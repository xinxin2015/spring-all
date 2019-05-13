package org.springframework.beans;

import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.beans.PropertyEditor;
import java.util.*;

/**
 * Base implementation of the {@link PropertyEditorRegistry} interface.
 * Provides management of default editors and custom editors.
 * Mainly serves as base class for {@link BeanWrapperImpl}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 1.2.6
 * @see java.beans.PropertyEditorManager
 * @see java.beans.PropertyEditorSupport#setAsText
 * @see java.beans.PropertyEditorSupport#setValue
 */
public class PropertyEditorRegistrySupport implements PropertyEditorRegistry {

    @Nullable
    private ConversionService conversionService;

    private boolean defaultEditorsActive = false;

    private boolean configValueEditorsActive = false;

    @Nullable
    private Map<Class<?>, PropertyEditor> defaultEditors;

    @Nullable
    private Map<Class<?>, PropertyEditor> overriddenDefaultEditors;

    @Nullable
    private Map<Class<?>, PropertyEditor> customEditors;

    @Nullable
    private Map<String,CustomEditorHolder> customEditorsForPath;

    @Nullable
    private Map<Class<?>,PropertyEditor> customEditorCache;

    public void setConversionService(@Nullable ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Nullable
    public ConversionService getConversionService() {
        return conversionService;
    }

    protected void registerDefaultEditors() {
        this.defaultEditorsActive = true;
    }

    public void useConfigValueEditors() {
        this.configValueEditorsActive = true;
    }

    public void overrideDefaultEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
        if (this.overriddenDefaultEditors == null) {
            this.overriddenDefaultEditors = new HashMap<>();
        }
        this.overriddenDefaultEditors.put(requiredType, propertyEditor);
    }

    @Nullable
    public PropertyEditor getDefaultEditor(Class<?> requiredType) {
        if (!this.defaultEditorsActive) {
            return null;
        }
        if (this.overriddenDefaultEditors != null) {
            PropertyEditor editor = this.overriddenDefaultEditors.get(requiredType);
            if (editor != null) {
                return editor;
            }
        }
        if (this.defaultEditors == null) {
            createDefaultEditors();
        }
        return this.defaultEditors.get(requiredType);
    }

    private void createDefaultEditors() {
        this.defaultEditors = new HashMap<>();

        //TODO
    }

    protected void copyDefaultEditorTo(PropertyEditorRegistrySupport target) {
        target.defaultEditorsActive = this.defaultEditorsActive;
        target.configValueEditorsActive = this.configValueEditorsActive;
        target.defaultEditors = this.defaultEditors;
        target.overriddenDefaultEditors = this.overriddenDefaultEditors;
    }

    @Override
    public void registerCustomerEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
        registerCustomerEditor(requiredType,null,propertyEditor);
    }

    @Override
    public void registerCustomerEditor(Class<?> requiredType, String propertyPath, PropertyEditor propertyEditor) {
        if (requiredType == null && propertyPath == null) {
            throw new IllegalArgumentException("Either requiredType or propertyPath is required");
        }
        if (propertyPath != null) {
            if (this.customEditorsForPath == null) {
                this.customEditorsForPath = new LinkedHashMap<>(16);
            }
            this.customEditorsForPath.put(propertyPath,new CustomEditorHolder(propertyEditor,requiredType));
        } else {
            if (this.customEditors == null) {
                this.customEditors = new LinkedHashMap<>(16);
            }
            this.customEditors.put(requiredType,propertyEditor);
            this.customEditorCache = null;
        }
    }

    @Override
    @Nullable
    public PropertyEditor findCustomEditor(@Nullable Class<?> requiredType, @Nullable String propertyPath) {
        Class<?> requiredTypeToUse = requiredType;
        if (propertyPath != null) {
            if (this.customEditorsForPath != null) {
                // Check property-specific editor first.
                PropertyEditor editor = getCustomEditor(propertyPath,requiredType);
                if (editor == null) {
                    List<String> strippedPaths = new ArrayList<>();
                    addStrippedPropertyPaths(strippedPaths,"",propertyPath);
                    for (Iterator<String> it = strippedPaths.iterator(); it.hasNext() && editor == null;) {
                        String strippedPath = it.next();
                        editor = getCustomEditor(strippedPath,requiredType);
                    }
                }
                if (editor != null) {
                    return editor;
                }
            }
            if (requiredType == null) {
                requiredTypeToUse = getPropertyType(propertyPath);
            }
        }
        // No property-specific editor -> check type-specific editors
        return getCustomEditor(requiredTypeToUse);
    }

    @Nullable
    protected Class<?> getPropertyType(String propertyPath) {
        return null;
    }

    @Nullable
    private PropertyEditor getCustomEditor(String propertyName,@Nullable Class<?> requiredType) {
        CustomEditorHolder holder =
                this.customEditorsForPath != null ? this.customEditorsForPath.get(propertyName) : null;
        return holder != null ? holder.getPropertyEditor(requiredType) : null;
    }

    @Nullable
    private PropertyEditor getCustomEditor(@Nullable Class<?> reuqiredType) {
        if (reuqiredType == null || this.customEditors == null) {
            return null;
        }
        // Check directly registered editor for type
        PropertyEditor editor = this.customEditors.get(reuqiredType);
        if (editor == null) {
            // Check cached editor for type, registered for superclass or interface.
            if (this.customEditorCache != null) {
                editor = this.customEditorCache.get(reuqiredType);
            }
            if (editor == null) {
                // Find editor for superclass or interface.
                for (Iterator<Class<?>> it = this.customEditors.keySet().iterator();it.hasNext() && editor == null;) {
                    Class<?> key = it.next();
                    if (key.isAssignableFrom(reuqiredType)) {
                        editor = this.customEditors.get(key);
                        // Cache editor for search type, to avoid the overhead
                        // of repeated assignable-from checks.
                        if (this.customEditorCache == null) {
                            this.customEditorCache = new HashMap<>();
                        }
                        this.customEditorCache.put(reuqiredType,editor);
                    }
                }
            }
        }
        return editor;
    }

    private void addStrippedPropertyPaths(List<String> strippedPaths,String nestedPath,String propertyPath) {
        int startIndex = propertyPath.indexOf(PropertyAccessor.PROPERTY_KEY_PREFIX_CHAR);
        if (startIndex != -1) {
            int endIndex = propertyPath.indexOf(PropertyAccessor.PROPERTY_KEY_SUFFIX_CHAR);
            if (endIndex != -1) {
                String prefix = propertyPath.substring(0,startIndex);
                String key = propertyPath.substring(startIndex,endIndex + 1);
                String suffix = propertyPath.substring(endIndex + 1,propertyPath.length());
                // Strip the first key
                strippedPaths.add(nestedPath + prefix + suffix);
                // Search for further keys to strip, with the first key stripped.
                addStrippedPropertyPaths(strippedPaths,nestedPath + prefix,suffix);
                // Search for further keys to strip, with the first key not stripped.
                addStrippedPropertyPaths(strippedPaths,nestedPath + prefix + key,suffix);
            }
        }
    }

    private static final class CustomEditorHolder {

        private final PropertyEditor propertyEditor;

        @Nullable
        private final Class<?> registeredType;

        private CustomEditorHolder(PropertyEditor propertyEditor, @Nullable Class<?> registeredType) {
            this.propertyEditor = propertyEditor;
            this.registeredType = registeredType;
        }

        private PropertyEditor getPropertyEditor() {
            return this.propertyEditor;
        }

        @Nullable
        public Class<?> getRegisteredType() {
            return registeredType;
        }

        @Nullable
        private PropertyEditor getPropertyEditor(@Nullable Class<?> requiredType) {
            // Special case: If no required type specified, which usually only happens for
            // Collection elements, or required type is not assignable to registered type,
            // which usually only happens for generic properties of type Object -
            // then return PropertyEditor if not registered for Collection or array type.
            // (If not registered for Collection or array, it is assumed to be intended
            // for elements.)
            if (this.registeredType == null ||
                    (requiredType != null &&
                            (ClassUtils.isAssignable(this.registeredType, requiredType) ||
                                    ClassUtils.isAssignable(requiredType, this.registeredType))) ||
                    (requiredType == null &&
                            (!Collection.class.isAssignableFrom(this.registeredType) && !this.registeredType.isArray()))) {
                return this.propertyEditor;
            } else {
                return null;
            }
        }
    }
}
