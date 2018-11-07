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

package bisq.core.payment;

import bisq.core.proto.CoreProtoResolver;

import org.junit.Ignore;

import static org.junit.Assert.assertEquals;

public class PaymentAccountTest {

    @Ignore("TODO InvalidKeySpecException at bisq.common.crypto.Sig.getPublicKeyFromBytes(Sig.java:135)")
    public void test() {
        OKPayAccount account = new OKPayAccount();
        String name = "name";
        account.setAccountName(name);
        String nr = "nr";
        account.setAccountNr(nr);

        OKPayAccount newVo = (OKPayAccount) PaymentAccount.fromProto(account.toProtoMessage(), new CoreProtoResolver());
        assertEquals(name, newVo.getAccountName());
        assertEquals(nr, newVo.getAccountNr());

    }

}
