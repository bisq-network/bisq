package bisq.common.util;

import java.util.Collection;
import java.util.Map;

/**
 * Collection utility methods copied from Spring Framework v4.3.6's
 * {@code org.springframework.util.CollectionUtils} class in order to make it possible to
 * drop Bisq's dependency on Spring altogether. The name of the class and methods have
 * been preserved here to minimize the impact to the Bisq codebase of making this change.
 * All that is necessary to swap this implementation in is to change the CollectionUtils
 * import statement.
 */
public class CollectionUtils {

    /**
     * Return {@code true} if the supplied Collection is {@code null} or empty.
     * Otherwise, return {@code false}.
     * @param collection the Collection to check
     * @return whether the given Collection is empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return (collection == null || collection.isEmpty());
    }

    /**
     * Return {@code true} if the supplied Map is {@code null} or empty.
     * Otherwise, return {@code false}.
     * @param map the Map to check
     * @return whether the given Map is empty
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return (map == null || map.isEmpty());
    }
}
