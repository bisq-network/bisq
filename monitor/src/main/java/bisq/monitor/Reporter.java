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

package bisq.monitor;

import java.util.Map;

/**
 * Reports findings to a specific service/file/place using the proper means to
 * do so.
 *
 * @author Florian Reimair
 */
public abstract class Reporter extends Configurable {

    protected Reporter() {
        setName(this.getClass().getSimpleName());
    }

    /**
     * Report our findings.
     *
     * @param value the value to report
     */
    public abstract void report(long value);

    /**
     * Report our findings
     *
     * @param value the value to report
     * @param prefix a common prefix to be included in the tag name
     */
    public abstract void report(long value, String prefix);

    /**
     * Report our findings.
     *
     * @param values Map<metric name, metric value>
     */
    public abstract void report(Map<String, String> values);

    /**
     * Report our findings.
     *
     * @param values Map<metric name, metric value>
     * @param prefix for example "torStartupTime"
     */
    public abstract void report(Map<String, String> values, String prefix);

    /**
     * Report our findings one by one.
     *
     * @param key the metric name
     * @param value the value to report
     * @param timestamp a unix timestamp in milliseconds
     * @param prefix for example "torStartupTime"
     */
    public abstract void report(String key, String value, String timestamp, String prefix);

}
