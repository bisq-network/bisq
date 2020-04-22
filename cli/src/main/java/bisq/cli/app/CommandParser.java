package bisq.cli.app;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.System.out;

final class CommandParser {

    private Optional<String> cmdToken;
    private final Map<String, String> creds = new HashMap<>();
    private final CliConfig config;

    public CommandParser(CliConfig config) {
        this.config = config;
        init();
    }

    public Optional<String> getCmdToken() {
        return this.cmdToken;
    }

    public Map<String, String> getCreds() {
        return this.creds;
    }

    private void init() {
        OptionParser parser = config.getOptionParser();
        OptionSpec<String> nonOptions = parser.nonOptions().ofType(String.class);
        OptionSet options = parser.parse(config.getParams());
        creds.putAll(rpcCredentials.apply(options));
        // debugOptionsSet(options, nonOptions);
        List<String> detectedOptions = nonOptions.values(options);
        cmdToken = detectedOptions.isEmpty() ? Optional.empty() : Optional.of(detectedOptions.get(0));
    }

    final Function<OptionSet, Map<String, String>> rpcCredentials = (opts) ->
            opts.asMap().entrySet().stream()
                    .filter(e -> e.getKey().options().size() == 1 && e.getKey().options().get(0).startsWith("rpc"))
                    .collect(Collectors.toUnmodifiableMap(m -> m.getKey().options().get(0), m -> (String) m.getValue().get(0)));

    private void debugOptionsSet(OptionSet options, OptionSpec<String> nonOptions) {
        // https://programtalk.com/java-api-usage-examples/joptsimple.OptionParser
        out.println("*** BEGIN Debug OptionSet ***");
        out.println("[argument acceptors]");
        options.asMap().entrySet().forEach(out::println);
        out.println("[rpc credentials map]");
        out.println(rpcCredentials.apply(options));
        out.println("[non options]");
        nonOptions.values(options).forEach(out::println);
        out.println("*** END Debug OptionSet ***");
    }
}
