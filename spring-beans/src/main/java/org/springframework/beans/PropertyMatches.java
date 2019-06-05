package org.springframework.beans;


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
 * @since 2.0
 * @see #forProperty(String, Class)
 * @see #forField(String, Class)
 */
public abstract class PropertyMatches {

    public static final int DEFAULT_MAX_INSTANCE = 2;

    private final String propertyName;

    private final String[] possibleMatches;

    private PropertyMatches(String propertyName,String[] possibleMatches) {
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
            }
            else if (i == this.possibleMatches.length - 2) {
                msg.append("', or ");
            }
        }
        msg.append("'?");
    }

    private static int calculateStringDistance(String s1,String s2) {
        if (s1.isEmpty()) {
            return s2.length();
        }
        if (s2.isEmpty()) {
            return s1.length();
        }

        int[][] d = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0;i < s1.length();i ++) {
            d[i][0] = i;
        }
        for (int i = 0;i < s2.length();i ++) {
            d[0][i] = i;
        }

        for (int i = 1;i <= s1.length();i ++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1;j <= s2.length();j ++) {
                int cost;
                char c2 = s2.charAt(j - 1);
                if (c1 == c2) {
                    cost = 0;
                } else {
                    cost = 1;
                }
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1,d[i][j - 1] + 1),d[i - 1][j - 1] + cost);
            }
        }
        return d[s1.length()][s2.length()];
    }

    private static class BeanPropertyMatches extends PropertyMatches {

    }

}
