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

package bisq.cli.opts;


import joptsimple.OptionSpec;

import static bisq.cli.opts.OptLabel.OPT_OFFER_ID;

/**
 * Superclass for option parsers requiring an offer-id.  Avoids a small amount of
 * duplicated boilerplate.
 */
public class OfferIdOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> offerIdOpt = parser.accepts(OPT_OFFER_ID, "id of offer")
            .withRequiredArg();

    public OfferIdOptionParser(String[] args) {
        this(args, false);
    }

    public OfferIdOptionParser(String[] args, boolean allowsUnrecognizedOptions) {
        super(args);
        if (allowsUnrecognizedOptions)
            this.parser.allowsUnrecognizedOptions();
    }

    public OfferIdOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(offerIdOpt) || options.valueOf(offerIdOpt).isEmpty())
            throw new IllegalArgumentException("no offer id specified");

        return this;
    }

    public String getOfferId() {
        return options.valueOf(offerIdOpt);
    }
}
