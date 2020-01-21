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

package bisq.common.config;

import joptsimple.ValueConverter;

import com.google.common.base.Enums;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * A {@link joptsimple.ValueConverter} that supports case-insensitive conversion from
 * String to an enum label. Useful in conjunction with
 * {@link joptsimple.ArgumentAcceptingOptionSpec#ofType(Class)} when the type in question
 * is an enum.
 */
class EnumValueConverter implements ValueConverter<Enum> {

    private final Class<? extends Enum> enumType;

    public EnumValueConverter(Class<? extends Enum> enumType) {
        this.enumType = enumType;
    }

    /**
     * Attempt to resolve an enum of the specified type by looking for a label with the
     * given value, trying all case variations in the process.
     *
     * @return the matching enum label (if any)
     * @throws ConfigException if no such label matching the given value is found.
     */
    @Override
    public Enum convert(String value) {
        Set<String> candidates = Sets.newHashSet(value, value.toUpperCase(), value.toLowerCase());
        for (String candidate : candidates) {
            Optional<? extends Enum> result = Enums.getIfPresent(enumType, candidate);
            if (result.isPresent())
                return result.get();
        }
        throw new ConfigException("Enum label %s.{%s} does not exist",
                enumType.getSimpleName(), String.join("|", candidates));
    }

    @Override
    public Class<? extends Enum> valueType() {
        return enumType;
    }

    @Override
    public String valuePattern() {
        return null;
    }
}
