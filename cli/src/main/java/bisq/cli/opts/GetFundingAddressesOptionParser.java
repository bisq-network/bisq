package bisq.cli.opts;

import joptsimple.OptionSpec;

import static bisq.cli.opts.OptLabel.OPT_ONLY_FUNDED;

public class GetFundingAddressesOptionParser extends AbstractMethodOptionParser implements MethodOpts {
    final OptionSpec<Boolean> onlyFundedOpt = parser.accepts(OPT_ONLY_FUNDED, "only funded addresses")
            .withOptionalArg()
            .ofType(boolean.class)
            .defaultsTo(Boolean.FALSE);

    public GetFundingAddressesOptionParser(String[] args) {
        super(args);
    }

    public GetFundingAddressesOptionParser parse() {
        super.parse();

        return this;
    }

    public boolean getIsOnlyFunded() {
        return options.valueOf(onlyFundedOpt);
    }
}
