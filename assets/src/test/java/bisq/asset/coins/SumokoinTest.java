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
 public class SumokoinTest extends AbstractAssetTest {
     public SumokoinTest() {
        super(new Sumokoin());
    }
     @Test
    public void testValidAddresses() {
        assertValidAddress("Sumoo5FNMbScY3aXqsuuxCg47ztau89XT79gHacw8MCz9fRt916LEMRHgJ88MMNocff77yedpzekLiAq8vvospATMNr281f1eSk");
        assertValidAddress("SumonzxPLnoCYidAY4un2DSpoUmd8VwaNiCb9vqUADJJcxsnpTycD454yup8RuLSWfHxitjLZ9RTQcbZmsGg3AW5KbbcusxT5w3");
        assertValidAddress("Sumonzk383z4UAXicDEHkoZTrv6yCxAWvgMSEB6nYhokVRzdNTU9zRgDu27yDBcrbKCvzvnHJpBMC6q26n8XC5UK8yKJW2NwbUr");
        assertValidAddress("SumonzoZJoA8A69XqvAHTb4Bx3KD1CHsLBDrrD2vohbi4TG12fxnrPPatb78Lvy9pTcQaJBoLGwPHTJ5NFse5hmBKs9BCPBxciy");
    }
     @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("Sumoo5FNMbScY3aXqsuuxCg47ztau89XT79gHacw8MCz9fRt916LEMRHgJ88MMNocff77yedpzekLiAq8vvospATMNr281f1eSkb");
        assertInvalidAddress("Sumoo5FNMbScY3aXqsuuxCg47ztau89XT79gHacw8MCz9fRt916LEMRHgJ88MMNocff77yedpzekLiAq8vvospATMNr281f1eS");
        assertInvalidAddress("SumoA5FNMbScY3aXqsuuxCg47ztau89XT79gHacw8MCz9fRt916LEMRHgJ88MMNocff77yedpzekLiAq8vvospATMNr281f1eSk");
        assertInvalidAddress("Sumo15FNMbScY3aXqsuuxCg47ztau89XT79gHacw8MCz9fRt916LEMRHgJ88MMNocff77yedpzekLiAq8vvospATMNr281f1eSk");
        assertInvalidAddress("SumonzxPLnoCYidAY4un2DSpoUmd8VwaNiCb9vqUADJJcxsnpTycD454yup8RuLSWfHxitjLZ9RTQcbZmsGg3AW5KbbcusxT5w35");
        assertInvalidAddress("SumonzaZJoA8A69XqvAHTb4Bx3KD1CHsLBDrrD2vohbi4TG12fxnrPPatb78Lvy9pTcQaJBoLGwPHTJ5NFse5hmBKs9BCPBxciy");
        assertInvalidAddress("Sumonz1ZJoA8A69XqvAHTb4Bx3KD1CHsLBDrrD2vohbi4TG12fxnrPPatb78Lvy9pTcQaJBoLGwPHTJ5NFse5hmBKs9BCPBxciy");
        assertInvalidAddress("SumonzhaMbScY3aXqsuuxCg47ztau89XT79gHacw8MCz9fRt916LEMRHgJ88MMNocff77yedpzekLiAq8vvospATMNr281f1eS");
    }
}
