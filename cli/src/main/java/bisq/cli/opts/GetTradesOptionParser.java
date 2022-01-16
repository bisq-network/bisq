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


import bisq.proto.grpc.GetTradesRequest;

import joptsimple.OptionSpec;

import java.util.function.Predicate;

import static bisq.cli.opts.OptLabel.OPT_CATEGORY;
import static bisq.proto.grpc.GetTradesRequest.Category.CLOSED;
import static bisq.proto.grpc.GetTradesRequest.Category.FAILED;
import static bisq.proto.grpc.GetTradesRequest.Category.OPEN;
import static java.util.Arrays.stream;

public class GetTradesOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    // Map valid CLI option values to gRPC request parameters.
    private enum CATEGORY {
        // Lower case enum fits CLI method and parameter style.
        open(OPEN),
        closed(CLOSED),
        failed(FAILED);

        private final GetTradesRequest.Category grpcRequestCategory;

        CATEGORY(GetTradesRequest.Category grpcRequestCategory) {
            this.grpcRequestCategory = grpcRequestCategory;
        }
    }

    final OptionSpec<String> categoryOpt = parser.accepts(OPT_CATEGORY,
                    "category of trades (open|closed|failed)")
            .withRequiredArg()
            .defaultsTo(CATEGORY.open.name());

    private final Predicate<String> isValidCategory = (c) ->
            stream(CATEGORY.values()).anyMatch(v -> v.name().equalsIgnoreCase(c));

    public GetTradesOptionParser(String[] args) {
        super(args);
    }

    public GetTradesOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (options.has(categoryOpt)) {
            String category = options.valueOf(categoryOpt);
            if (category.isEmpty())
                throw new IllegalArgumentException("no category (open|closed|failed) specified");

            if (!isValidCategory.test(category))
                throw new IllegalArgumentException("category must be open|closed|failed");
        }

        return this;
    }

    public GetTradesRequest.Category getCategory() {
        String cliOpt = options.valueOf(categoryOpt);
        return CATEGORY.valueOf(cliOpt).grpcRequestCategory;
    }
}
