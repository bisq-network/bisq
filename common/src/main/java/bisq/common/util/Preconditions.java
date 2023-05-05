package bisq.common.util;

import com.google.common.collect.Ordering;

import java.io.File;

import java.util.Comparator;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

/**
 * Custom preconditions similar to those found in
 * {@link com.google.common.base.Preconditions}.
 */
public class Preconditions {

    /**
     * Ensures that {@code dir} is a non-null, existing and read-writeable directory.
     * @param dir the directory to check
     * @return the given directory, now validated
     */
    public static File checkDir(File dir) {
        if (dir == null)
            throw new IllegalArgumentException("Directory must not be null");
        if (!dir.exists())
            throw new IllegalArgumentException(format("Directory '%s' does not exist", dir));
        if (!dir.isDirectory())
            throw new IllegalArgumentException(format("Directory '%s' is not a directory", dir));
        if (!dir.canRead())
            throw new IllegalArgumentException(format("Directory '%s' is not readable", dir));
        if (!dir.canWrite())
            throw new IllegalArgumentException(format("Directory '%s' is not writeable", dir));

        return dir;
    }

    // needed since Guava makes it impossible to create an ImmutableSorted[Set|Map] with a null comparator:
    public static void checkComparatorNullOrNatural(Comparator<?> comparator, Object errorMessage) {
        checkArgument(comparator == null || comparator.equals(Ordering.natural()) ||
                comparator.equals(Comparator.naturalOrder()), errorMessage);
    }
}
