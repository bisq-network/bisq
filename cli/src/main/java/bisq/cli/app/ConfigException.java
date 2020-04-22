package bisq.cli.app;

public class ConfigException extends BisqException {

    public ConfigException(String format, Object... args) {
        super(format, args);
    }

}
