package bisq.common.util;

import java.io.File;

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
}
