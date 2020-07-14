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

import java.lang.reflect.Method;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ApiTestCase {

    protected static final char CHECK = '\u2714';
    protected static final char CROSS_MARK = '\u274c';

    protected Method getMethod(String methodName) {
        try {
            return this.getClass().getMethod(methodName);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(format("No method '%s' exists in class '%s'",
                    methodName, this.getClass().getName()),
                    ex);
        }
    }

    protected void sleep(long ms) {
        try {
            MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
