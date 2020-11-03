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

package bisq.monitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;

/**
 * Calculates average, max, min, p25, p50, p75 off of a list of samples and
 * throws in the sample size for good measure.
 *
 * @author Florian Reimair
 */
public class StatisticsHelper {

    public static Map<String, String> process(Collection<Long> input) {

        List<Long> samples = new ArrayList<>(input);

        // aftermath
        Collections.sort(samples);

        // - average, max, min , sample size
        LongSummaryStatistics statistics = samples.stream().mapToLong(val -> val).summaryStatistics();

        Map<String, String> results = new HashMap<>();
        results.put("average", String.valueOf(Math.round(statistics.getAverage())));
        results.put("max", String.valueOf(statistics.getMax()));
        results.put("min", String.valueOf(statistics.getMin()));
        results.put("sampleSize", String.valueOf(statistics.getCount()));

        // - p25, median, p75
        Integer[] percentiles = new Integer[] { 25, 50, 75 };
        for (Integer percentile : percentiles) {
            double rank = statistics.getCount() * percentile / 100.0;
            Long percentileValue;
            if (samples.size() <= rank + 1)
                percentileValue = samples.get(samples.size() - 1);
            else if (Math.floor(rank) == rank)
                percentileValue = samples.get((int) rank);
            else
                percentileValue = Math.round(samples.get((int) Math.floor(rank))
                        + (samples.get((int) (Math.floor(rank) + 1)) - samples.get((int) Math.floor(rank)))
                                / (rank - Math.floor(rank)));
            results.put("p" + percentile, String.valueOf(percentileValue));
        }

        return results;
    }
}
