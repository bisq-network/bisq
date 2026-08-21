package bisq.common.config;

import bisq.common.BisqException;

public class ConfigException extends BisqException {

    public ConfigException(String format, Object... args) {
        super(format, args);
    }
}
