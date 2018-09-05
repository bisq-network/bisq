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

package bisq.asset;

import bisq.common.util.Tuple2;

import java.util.Comparator;

public class PrintTool {

    public static void main(String[] args) {
        // Prints out all coins in the format used in the FAQ webpage.
        // Run that and copy paste the result to the FAQ webpage at new releases.
        StringBuilder sb = new StringBuilder();
        new AssetRegistry().stream()
                .sorted(Comparator.comparing(o -> o.getName().toLowerCase()))
                .filter(e -> !e.getTickerSymbol().equals("BSQ")) // BSQ is not out yet...
                .filter(e -> !e.getTickerSymbol().equals("BTC"))
                .map(e -> new Tuple2(e.getName(), e.getTickerSymbol())) // We want to get rid of duplicated entries for regtest/testnet...
                .distinct()
                .forEach(e -> sb.append("<li>&#8220;")
                        .append(e.second)
                        .append("&#8221;, &#8220;")
                        .append(e.first)
                        .append("&#8221;</li>")
                        .append("\n"));
        System.out.println(sb.toString());
    }
}
