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

public class QwertycoinTest extends AbstractAssetTest {

    public QwertycoinTest() {
        super(new Qwertycoin());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("QWC1NStUeRB9hZiYH8sG5RAWEt7YycyB44YZnpZQBpgq4CLwmLw4vAk9tU3h7Td21NL9aMbLHxseDGKdEv3gRexo2QCodNEZWa");
        assertValidAddress("QWC1anUNJRo2HePBmenLFkGu8rnug4odGLCjHaCqAwMxboiZZS3Gv9ACLfn2zvcsGVcCc51eqZXB8Dot9X5qAt3F53F8BxjDrG");
        assertValidAddress("QWC1hgpbsxsPrpxH9H3wL771p4KdgS7vA369PQTiznHiCC3NjZxKJSmBtJPVCJBBUKE346FcPTsQ18W6fgiDzj762BHNgo2sir");
        assertValidAddress("QWC1YAvWpYBVs8XT2eSt2JV5iAJSdm8CwbQhDruuBeTzRNKSdtdK8Mn3WjaXQrFvjMMWWTf24x89p31mWppJN2Br9uiA5zdYQu");
        assertValidAddress("QWC1YzR91Zmcj7fpf1HRZhSfz6cgXbxqAVTjQTtrUV6Bfv1ysEzb78qgVojE7FuQWSRnVqSb3LyxP9nH2q4vWyo82Fonutfkzr");
        assertValidAddress("QWC1KYAwX6sRXK94HabKLCFNMjfC12KFC74cRjTgFtsD79VUBydTtMd3G2z4xLg2e1LKaXsTt3zkYibH3pBrAMjd5z5ConjRXn");
        assertValidAddress("QWC1ZgSyFwS3tUbmCRPGDBi224ynMZXgXCHxvQ5pEmtuZSCrmid4z1de1DWRjhZKRZXe4E5LYhtP6e7FmpN8R2MM2SHGFvg12z");
        assertValidAddress("QWC1W7223e83cBdseddQp461j49bhr7y4VHh8FTPs7qWArhpqBzNvrYR5QXyFtc3eRaoASo3QVhuT6ogAa6AHhgt4bVMUNpZGh");
        assertValidAddress("QWC1NgBcSwvXghUkEqGttNPSSmPEgEdknXELNLyTG444Fx3cKkV2oJ9iCwzySbps7y9BqqkWAKbkvdkA8FTspfdm29ScDzASK1");
        assertValidAddress("QWC1FVgbYqkafwnpW8KU2gKXLTKoraMXuEJ2c1yG6PNdesh6BA3Wq8d1mgRYqfsbCn53g5VLHuxyLT8CXnGRLxN64wHssuSa9D");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("");
        assertInvalidAddress("QW009s5NiYva6XS9bhhVc6jKYgXsH9wuHhZhWsqyAoPoWPUEiwEo9AZCDNbksvknyvZk73zwhHWSiXdgcDGLyhk5teEM7yxTgk");
        assertInvalidAddress("WC115a2NPZy7Xe2WZt3nKMZBsBpgNdevnKfy6PLk99eCjYw5fWQ5nu4uM6LerRBvVKTJpdGp2acZ25X3QDPHc7ocTxz1WfN2Za");
        assertInvalidAddress("34BQWC8imA1UH29uR6PHiGpcz9MYdnL3rku27gGeLosn5XuSedFC7jPBmzaB9DoxmDA5VUZa5hPv6PSe3tJH2MJhBktMEJXaZ8");
        assertInvalidAddress("KWC45Ghz2JRTTrLh8Z4bm6QhzZxVbq7LPiKbgjjhHPNDvVXZAJLop91zRu9A7wJMjyrU89uF7QpZB5kHhniuGZ88MJv7jRZXNi");
        assertInvalidAddress("ABc58FFmFEGcS52mTWmhAskQaKSSiX1BnHo8YcDjuhPdYBpWT9Q6ZCDz54k6cs3jPF2nk6desb1T6vRfHLfthiNf561qPct2SY");
        assertInvalidAddress("2K267rMF5ve4nt2wTHYJ1pZ6j3o2YP5KDBnE7GDxnr6bpem9WcqeHzw9yKWXvtxYdpDXCBbLiX9nm97r4aEtnXq8YNb9WPn15f");
        assertInvalidAddress("798Qr9sWTprQ2sH2y5PGpfV3RAnFxUsJYY2a2VQWCA9GjZ3MiyScD8VEh8ifWk4toYRCcbLZmRJw2dSsJBJAJ1Ava8WBzW7J12");
        assertInvalidAddress("A2o85CQSLDNNKR4HGHwhtsxhm8jheYEvk6ngf44AhqCRWDV2XsaTHr6ittDuyfCjinAP1SzBqnVJfqNhYGDJLzxq4Y7FBVofXV");
        assertInvalidAddress("QW9AeKW87bkao59oadmTXGf8Jv7sMYByPrKahRbnmZEmGzRgoxGRbWqmmXuPDW6jPJSUAdpZRZn6E5B9935LtWD5gHAPpZQA");
    }
}
