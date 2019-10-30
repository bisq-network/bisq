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

package bisq.core.notifications;

import bisq.common.util.Tuple2;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

@Slf4j
public class MobileModelTest {

    @Test
    public void testParseDescriptor() {
        MobileModel mobileModel = new MobileModel();
        List<Tuple2<String, Boolean>> list = Arrays.asList(
                new Tuple2<>("iPod Touch 5", false),
                new Tuple2<>("iPod Touch 6", false),
                new Tuple2<>("iPhone 4", false),
                new Tuple2<>("iPhone 4s", false),
                new Tuple2<>("iPhone 5", false),
                new Tuple2<>("iPhone 5c", false),
                new Tuple2<>("iPhone 5s", false),

                new Tuple2<>("iPhone 6", false),
                new Tuple2<>("iPhone 6 Plus", false),
                new Tuple2<>("iPhone 6s", true),
                new Tuple2<>("iPhone 6s Plus", true),

                new Tuple2<>("iPhone 7", true),
                new Tuple2<>("iPhone 7 Plus", true),
                new Tuple2<>("iPhone SE", false), // unclear
                new Tuple2<>("iPhone 8", true),
                new Tuple2<>("iPhone 8 Plus", true),
                new Tuple2<>("iPhone X", true),
                new Tuple2<>("iPhone XS", true),
                new Tuple2<>("iPhone XS Max", true),
                new Tuple2<>("iPhone XR", true),
                new Tuple2<>("iPhone 11", true),
                new Tuple2<>("iPhone 11 Pro", true),
                new Tuple2<>("iPhone 11 Pro Max", true),
                new Tuple2<>("iPhone 11S", true), // not sure if this model will exist, but based on past versioning it is possible
                                                  // need to ensure it will be parsed correctly just in case

                new Tuple2<>("iPad 2", false),
                new Tuple2<>("iPad 3", false),
                new Tuple2<>("iPad 4", false),
                new Tuple2<>("iPad Air", false),
                new Tuple2<>("iPad Air 2", false),
                new Tuple2<>("iPad 5", false),
                new Tuple2<>("iPad 6", false),
                new Tuple2<>("iPad Mini", false),
                new Tuple2<>("iPad Mini 2", false),
                new Tuple2<>("iPad Mini 3", false),
                new Tuple2<>("iPad Mini 4", false),

                new Tuple2<>("iPad Pro 9.7 Inch", true),
                new Tuple2<>("iPad Pro 12.9 Inch", true),
                new Tuple2<>("iPad Pro 12.9 Inch 2. Generation", true),
                new Tuple2<>("iPad Pro 10.5 Inch", true)
        );

        list.forEach(tuple -> {
            log.info(tuple.toString());
            assertEquals("tuple: " + tuple, mobileModel.parseDescriptor(tuple.first), tuple.second);
        });

    }
}
