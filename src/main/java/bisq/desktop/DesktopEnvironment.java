package bisq.desktop;

import bisq.core.app.BisqEnvironment;
import joptsimple.OptionSet;

public class DesktopEnvironment extends BisqEnvironment {

    private boolean enabled;

    public DesktopEnvironment(OptionSet options)
    {
        super(options);
        enabled = !options.has("gui") || (boolean) options.valueOf("gui");
    }

    public boolean isEnabled()
    {
        return enabled;
    }
}
