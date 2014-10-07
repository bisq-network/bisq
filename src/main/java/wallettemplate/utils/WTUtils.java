/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package wallettemplate.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Some generic utilities to make Java a bit less annoying.
 */
public class WTUtils {
    private static final Logger log = LoggerFactory.getLogger(WTUtils.class);

    public interface UncheckedRun<T> {
        public T run() throws Throwable;
    }

    public interface UncheckedRunnable {
        public void run() throws Throwable;
    }

    public static <T> T unchecked(UncheckedRun<T> run) {
        try {
            return run.run();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static void uncheck(UncheckedRunnable run) {
        try {
            run.run();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static void ignoreAndLog(UncheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            log.error("Ignoring error", t);
        }
    }

    public static <T> T ignoredAndLogged(UncheckedRun<T> runnable) {
        try {
            return runnable.run();
        } catch (Throwable t) {
            log.error("Ignoring error", t);
            return null;
        }
    }

    public static boolean didThrow(UncheckedRun run) {
        try {
            run.run();
            return false;
        } catch (Throwable throwable) {
            return true;
        }
    }

    public static boolean didThrow(UncheckedRunnable run) {
        try {
            run.run();
            return false;
        } catch (Throwable throwable) {
            return true;
        }
    }
}
