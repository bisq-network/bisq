package bisq.common.config;

class ConfigFileOption {

    public final String name;
    public final String arg;

    public ConfigFileOption(String name, String arg) {
        this.name = name;
        this.arg = arg;
    }

    public static boolean isOption(String line) {
        return !line.isEmpty() && !line.startsWith("#");
    }

    public static ConfigFileOption parse(String option) {
        if (!option.contains("="))
            return new ConfigFileOption(option, null);

        String[] tokens = clean(option).split("=");
        String name = tokens[0].trim();
        String arg = tokens.length > 1 ? tokens[1].trim() : "";
        return new ConfigFileOption(name, arg);
    }

    public String toString() {
        return String.format("%s%s", name, arg != null ? ('=' + arg) : "");
    }

    public static String clean(String option) {
        return option
                .trim()
                .replace("\\:", ":");
    }
}
