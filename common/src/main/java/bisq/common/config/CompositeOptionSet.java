package bisq.common.config;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.ArrayList;
import java.util.List;

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

    public List<String> valuesOf(ArgumentAcceptingOptionSpec<String> option) {
        for (OptionSet optionSet : optionSets)
            if (optionSet.has(option))
                return optionSet.valuesOf(option);

        // None of the provided option sets specified the given option so fall back to
        // the default value (if any) provided by the first specified OptionSet
        return optionSets.get(0).valuesOf(option);
    }
}
