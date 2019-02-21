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

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class XDRTest extends AbstractAssetTest {

    public XDRTest() {
        super(new XDR());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("2WeY8JpRJgrvWQxbSPuyhsBMjtZMMN7cADEomPHh2bCkdZ7xQW");
        assertValidAddress("NTvSfK1Gr5Jg97UvJo2wvi7BTZo8KqJzgSL2FCGucF6nUH7yq");
        assertValidAddress("ztNdPsuyfDWt1ufCbDqaCDQH3FXvucXNZqVrdzsWvzDHPrkSh");
        assertValidAddress("jkvx3z98rJmuVKqMSktDpKTSBrsqJEtTBW1CBSWJEtchDGkDX");
        assertValidAddress("is2YXBxk91d4Lw4Pet7RoP8KAxCKFHUC6iQyaNgmac5ies6ko");
        assertValidAddress("2NNEr5YLniGxWajoeXiiAZPR68hJXncnhEmC4GWAaV5kwaLRcP");
        assertValidAddress("wGmjgRu8hgjgRsRV8k6h2puis1K9UQCTKWZEPa4yS8mrmJUpU");
        assertValidAddress("i8rc9oMunRtVbSxA4VBESxbYzHnfhP39aM5M1srtxVZ8oBiKD");
        assertValidAddress("vP4w8khXHFQ7cJ2BJNyPbJiV5kFfBHPVivHxKf5nyd8cEgB9U");
        assertValidAddress("QQQZZa46QJ3499RL8CatuqaUx4haKQGUuZ4ZE5SeL13Awkf6m");
        assertValidAddress("qqqfpHD3VbbyZXTHgCW2VX8jvoERcxanzQkCqVyHB8fRBszMn");
        assertValidAddress("BiSQkPqCCET4UovJASnnU1Hk5bnqBxBVi5bjA5wLZpN9HCA6A");
        assertValidAddress("bisqFm6Zbf6ULcpJqQ2ibn2adkL2E9iivQFTAP15Q18daQxnS");
        assertValidAddress("miLEgbhGv4ARoPG2kAhTCy8UGqBcFbsY6rr5tXq63nH8RyqcE");


    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("1WeY8JpRJgrvWQxbSPuyhsBMjtZMMN7cADEomPHh2bCkdZ7xQW");
        assertInvalidAddress("2WeY8JpRJgrvWQxbSPuyhsBMjtZMMN3cADEomPHh2bCkdZ7xQW");
        assertInvalidAddress("2WeY8JpRJgrvWQxbSPuyhsBMjtZMMN7cADEomPHh2bCkdZ7xQ1");
        assertInvalidAddress("2WeY8JpRJgrvWQxbSPuyhsBMjtZMMN7cADEomPHh2bCkdZ7xQ");
        assertInvalidAddress("WeY8JpRJgrvWQxbSPuyhsBMjtZMMN7cADEomPHh2bCkdZ7xQW");
        assertInvalidAddress("2WeY8JpRJgrvWQx");
        assertInvalidAddress("2WeY8JpRJgrvWQxbSPuyhsBMjtZMMN7cADEomPHh2bCkdZ7xQW1");
        assertInvalidAddress("milEgbhGv4ARoPG2kAhTCy8UGqBcFbsY6rr5tXq63nH8RyqcE");
        assertInvalidAddress("miLegbhGv4ARoPG2kAhTCy8UGqBcFbsY6rr5tXq63nH8RyqcE");
        assertInvalidAddress("1111111");
    }
}
