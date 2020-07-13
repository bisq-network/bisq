/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest;

import java.util.function.Predicate;

import java.lang.reflect.Method;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;



import bisq.apitest.annotation.Skip;

public class ApiTestCase {

    protected static final char CHECK = '\u2714';
    protected static final char CROSS_MARK = '\u274c';

    public int countTestCases;
    public int countFailedTestCases;
    public int countSkippedTestCases;
    public int countPassedTestCases;

    private final Predicate<Class<?>> skipAll = (c) -> c.getAnnotation(Skip.class) != null;
    private final Predicate<Method> skip = (m) -> m.getAnnotation(Skip.class) != null;

    protected boolean isSkipped(String methodName) {
        try {
            if (skipAll.test(this.getClass()) || skip.test(getMethod(methodName))) {
                countSkippedTestCases++;
                return true;
            } else {
                return false;
            }
        } finally {
            countTestCases++;  // Increment the test case count, skipped or not.
        }
    }

    protected Method getMethod(String methodName) {
        try {
            return this.getClass().getMethod(methodName);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(format("No method '%s' exists in class '%s'",
                    methodName, this.getClass().getName()),
                    ex);
        }
    }

    protected String reportString() {
        return format("Total %d Passed %d Failed %d Skipped %d",
                countTestCases,
                countPassedTestCases,
                countFailedTestCases,
                countSkippedTestCases);
    }

    protected void sleep(long ms) {
        try {
            MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
