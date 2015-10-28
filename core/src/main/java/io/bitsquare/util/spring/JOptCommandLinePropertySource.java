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

package io.bitsquare.util.spring;

import joptsimple.OptionSet;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Customizes {@link io.bitsquare.util.spring.JOptCommandLinePropertySource#getOptionValues(String)}
 * to allow for proper use of {@link joptsimple.ArgumentAcceptingOptionSpec#ofType(Class)}.
 * To be removed once https://github.com/spring-projects/spring-framework/pull/693 has
 * been merged and made available in a release.
 */
public class JOptCommandLinePropertySource extends org.springframework.core.env.JOptCommandLinePropertySource {

    public JOptCommandLinePropertySource(String name, OptionSet options) {
        super(name, options);
    }

    @Override
    public List<String> getOptionValues(String name) {
        List<?> argValues = this.source.valuesOf(name);
        List<String> stringArgValues = argValues.stream().map(argValue -> argValue instanceof String ? (String) argValue : argValue.toString()).collect
                (Collectors.toList());
        if (stringArgValues.isEmpty()) {
            return (this.source.has(name) ? Collections.<String>emptyList() : null);
        }
        return Collections.unmodifiableList(stringArgValues);
    }
}
