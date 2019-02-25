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

import java.util.Properties;

/**
 * Does some pre-computation for a configurable class.
 *
 * @author Florian Reimair
 */
public abstract class Configurable {

    protected Properties configuration = new Properties();

    private String name;

    /**
     * Filters all java properties starting with {@link Configurable#getName()} of
     * the class and makes them available. Does <em>NOT</em> parse the content of
     * the properties!
     * <p>
     * For example, if the implementing class sets its name (using
     * {@link Configurable#setName(String)}) to <code>MyName</code>, the list of
     * properties is scanned for properties starting with <code>MyName</code>.
     * Matching lines are made available to the class without the prefix. For
     * example, a property <code>MyName.answer=42</code> is made available as
     * <code>configuration.getProperty("answer")</code> resulting in
     * <code>42</code>.
     *
     * @param properties a set of configuration properties
     */
    public void configure(final Properties properties) {
        // only configure the Properties which belong to us
        final Properties myProperties = new Properties();
        properties.forEach((k, v) -> {
            String key = (String) k;
            if (key.startsWith(getName()))
                myProperties.put(key.substring(key.indexOf(".") + 1), v);
        });

        // configure all properties that belong to us
        this.configuration = myProperties;
    }

    protected String getName() {
        return name;
    }

    /**
     * Set the name used to filter through configuration properties. See
     * {@link Configurable#configure(Properties)}.
     *
     * @param name the name of the configurable
     */
    protected void setName(String name) {
        this.name = name;
    }
}
