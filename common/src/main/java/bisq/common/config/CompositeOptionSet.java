package bisq.common.config;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.annotations.VisibleForTesting;

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
@VisibleForTesting
public class CompositeOptionSet {

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

    public List<String> valuesOf(ArgumentAcceptingOptionSpec<String> option) {
        for (OptionSet optionSet : optionSets)
            if (optionSet.has(option))
                return optionSet.valuesOf(option);

        // None of the provided option sets specified the given option so fall back to
        // the default value (if any) provided by the first specified OptionSet
        return optionSets.get(0).valuesOf(option);
    }
}
