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

package bisq.apitest.config;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes multiple JOptSimple {@link OptionSet} instances such that calls to
 * {@link #valueOf(OptionSpec)} and co will search all instances in the order they were
 * added and return any value explicitly set, otherwise returning the default value for
 * the given option or null if no default has been set. The API found here loosely
 * emulates the {@link OptionSet} API without going through the unnecessary work of
 * actually extending it. In practice, this class is used to compose options provided at
 * the command line with those provided via config file, such that those provided at the
 * command line take precedence over those provided in the config file.
 */
class CompositeOptionSet {

    private final List<OptionSet> optionSets = new ArrayList<>();

    public void addOptionSet(OptionSet optionSet) {
        optionSets.add(optionSet);
    }

    public boolean has(OptionSpec<?> option) {
        for (OptionSet optionSet : optionSets)
            if (optionSet.has(option))
                return true;

        return false;
    }

    public <V> V valueOf(OptionSpec<V> option) {
        for (OptionSet optionSet : optionSets)
            if (optionSet.has(option))
                return optionSet.valueOf(option);

        // None of the provided option sets specified the given option so fall back to
        // the default value (if any) provided by the first specified OptionSet
        return optionSets.get(0).valueOf(option);
    }
}
