package org.springframework.beans;


import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for calculating property matches, according to a configurable
 * distance. Provide the list of potential matches and an easy way to generate
 * an error message. Works for both java bean properties and fields.
 *
 * <p>Mainly for use within the framework and in particular the binding facility.
 *
 * @author Alef Arendsen
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @see #forProperty(String, Class)
 * @see #forField(String, Class)
 * @since 2.0
 */
public abstract class PropertyMatches {

    public static final int DEFAULT_MAX_INSTANCE = 2;

    private final String propertyName;

    private final String[] possibleMatches;

    public static PropertyMatches forField(String propertyName, Class<?> beanClass, int maxDistance) {
        return new
    }

    private PropertyMatches(String propertyName, String[] possibleMatches) {
        this.propertyName = propertyName;
        this.possibleMatches = possibleMatches;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String[] getPossibleMatches() {
        return possibleMatches;
    }

    public abstract String buildErrorMessage();

    protected void appendHintMessage(StringBuilder msg) {
        msg.append("Did you mean ");
        for (int i = 0; i < this.possibleMatches.length; i++) {
            msg.append('\'');
            msg.append(this.possibleMatches[i]);
            if (i < this.possibleMatches.length - 2) {
                msg.append("', ");
            } else if (i == this.possibleMatches.length - 2) {
                msg.append("', or ");
            }
        }
        msg.append("'?");
    }

    private static int calculateStringDistance(String s1, String s2) {
        if (s1.isEmpty()) {
            return s2.length();
        }
        if (s2.isEmpty()) {
            return s1.length();
        }

        int[][] d = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i < s1.length(); i++) {
            d[i][0] = i;
        }
        for (int i = 0; i < s2.length(); i++) {
            d[0][i] = i;
        }

        for (int i = 1; i <= s1.length(); i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= s2.length(); j++) {
                int cost;
                char c2 = s2.charAt(j - 1);
                if (c1 == c2) {
                    cost = 0;
                } else {
                    cost = 1;
                }
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[s1.length()][s2.length()];
    }

    private static class BeanPropertyMatches extends PropertyMatches {

        private BeanPropertyMatches(String propertyName, Class<?> beanClass, int maxDistance) {
            super(propertyName, calculateMatches(propertyName,BeanUtils.getPropertyDescriptors(beanClass),maxDistance));
        }

        private static String[] calculateMatches(String name, PropertyDescriptor[] descriptors, int maxDistance) {
            List<String> candidates = new ArrayList<>();
            for (PropertyDescriptor pd : descriptors) {
                if (pd.getWriteMethod() != null) {
                    String possibleAlternative = pd.getName();
                    if (PropertyMatches.calculateStringDistance(name, possibleAlternative) <= maxDistance) {
                        candidates.add(possibleAlternative);
                    }
                }
            }
            Collections.sort(candidates);
            return StringUtils.toStringArray(candidates);
        }

        @Override
        public String buildErrorMessage() {
            StringBuilder msg = new StringBuilder(160);
            msg.append("Bean property '").append(getPropertyName()).append(
                    "' is not writable or has an invalid setter method. ");
            if (!ObjectUtils.isEmpty(getPossibleMatches())) {
                appendHintMessage(msg);
            } else {
                msg.append("Does the parameter type of the setter match the return type of the getter?");
            }
            return msg.toString();
        }
    }

    private static class FieldPropertyMatches extends PropertyMatches {

        private FieldPropertyMatches(String propertyName,Class<?> beanClass,int maxDistance) {
            super(propertyName,calculateMatches(propertyName,beanClass,maxDistance));
        }

        private static String[] calculateMatches(final String name,Class<?> clazz,int maxDistance) {
            final List<String> candidates = new ArrayList<>();
            ReflectionUtils.doWithFields(clazz,field -> {
                String possibleAlternative = field.getName();
                if (PropertyMatches.calculateStringDistance(name,possibleAlternative) <= maxDistance) {
                    candidates.add(possibleAlternative);
                }
            });
            Collections.sort(candidates);
            return StringUtils.toStringArray(candidates);
        }

        @Override
        public String buildErrorMessage() {
            StringBuilder msg = new StringBuilder(80);
            msg.append("Bean property '").append(getPropertyName()).append("' has no matching field.");
            if (!ObjectUtils.isEmpty(getPossibleMatches())) {
                msg.append(' ');
                appendHintMessage(msg);
            }
            return msg.toString();
        }

    }

}
