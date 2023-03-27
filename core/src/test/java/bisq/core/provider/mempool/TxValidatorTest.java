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

package bisq.core.provider.mempool;

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.trade.DelayedPayoutAddressProvider;
import bisq.core.util.ParsingUtils;
import bisq.core.util.coin.BsqFormatter;

import org.bitcoinj.core.Coin;

import com.google.gson.Gson;

import org.apache.commons.io.IOUtils;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TxValidatorTest {
    private static final Logger log = LoggerFactory.getLogger(TxValidatorTest.class);

    private List<String> btcFeeReceivers = new ArrayList<>();

    public TxValidatorTest() {
        btcFeeReceivers.add("1EKXx73oUhHaUh8JBimtiPGgHfwNmxYKAj");
        btcFeeReceivers.add("1HpvvMHcoXQsX85CjTsco5ZAAMoGu2Mze9");
        btcFeeReceivers.add("3EfRGckBQQuk7cpU7SwatPv8kFD1vALkTU");
        btcFeeReceivers.add("13sxMq8mTw7CTSqgGiMPfwo6ZDsVYrHLmR");
        btcFeeReceivers.add("19qA2BVPoyXDfHKVMovKG7SoxGY7xrBV8c");
        btcFeeReceivers.add("19BNi5EpZhgBBWAt5ka7xWpJpX2ZWJEYyq");
        btcFeeReceivers.add("38bZBj5peYS3Husdz7AH3gEUiUbYRD951t");
        btcFeeReceivers.add(DelayedPayoutAddressProvider.BM2019_ADDRESS);
        btcFeeReceivers.add("1BVxNn3T12veSK6DgqwU4Hdn7QHcDDRag7");
        btcFeeReceivers.add("3A8Zc1XioE2HRzYfbb5P8iemCS72M6vRJV");
        btcFeeReceivers.add(DelayedPayoutAddressProvider.BM3_ADDRESS);
        log.warn("Known BTC fee receivers: {}", btcFeeReceivers.toString());
    }

    @Test
    public void testMakerTx() {
        String mempoolData, offerData;

        log.info("checking issue from user 2022-10-07");
        offerData = "1322804,5bec4007de1cb8cf18a5fa859d80d66031b8c78cfd99674e09ffd65cf23b50fc,9630000,137,0,757500";
        mempoolData = "{\"txid\":\"5bec4007de1cb8cf18a5fa859d80d66031b8c78cfd99674e09ffd65cf23b50fc\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":8921}},{\"vout\":1,\"prevout\":{\"value\":12155000}},{\"vout\":1,\"prevout\":{\"value\":2967000}}],\"vout\":[{\"scriptpubkey_address\":\"bc1qtyl6dququ2amxtsh4f3kx5rk9f5w9cuscz7ugm\",\"value\":8784},{\"scriptpubkey_address\":\"bc1qwj0jktuyjwj2ecwp9wgcrztxhve0hwn7n5lnxg\",\"value\":12519000},{\"scriptpubkey_address\":\"bc1qn3rd52mzkp6mgduz5wxprjw4rk9xpft6kga2mk\",\"value\":2600037}],\"size\":551,\"weight\":1229,\"fee\":3100,\"status\":{\"confirmed\":true,\"block_height\":757528,\"block_hash\":\"00000000000000000006b4426a0d2688a7e933e563e4d4fd80f572d935b12ae9\",\"block_time\":1665150690}}";
        assertTrue(createTxValidator(offerData).parseJsonValidateMakerFeeTx(mempoolData, btcFeeReceivers).getResult());

        log.info("expected: paid the correct amount of BSQ fees");
        offerData = "msimscqb,0636bafb14890edfb95465e66e2b1e15915f7fb595f9b653b9129c15ef4c1c4b,1000000,10,0,662390";
        mempoolData = "{\"txid\":\"0636bafb14890edfb95465e66e2b1e15915f7fb595f9b653b9129c15ef4c1c4b\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":7899}},{\"vout\":2,\"prevout\":{\"value\":54877439}}],\"vout\":[{\"scriptpubkey_address\":\"1FCUu7hqKCSsGhVJaLbGEoCWdZRJRNqq8w\",\"value\":7889},{\"scriptpubkey_address\":\"bc1qkj5l4wxl00ufdx6ygcnrck9fz5u927gkwqcgey\",\"value\":1600000},{\"scriptpubkey_address\":\"bc1qkw4a8u9l5w9fhdh3ue9v7e7celk4jyudzg5gk5\",\"value\":53276799}],\"size\":405,\"weight\":1287,\"fee\":650,\"status\":{\"confirmed\":true,\"block_height\":663140}}";
        assertTrue(createTxValidator(offerData).parseJsonValidateMakerFeeTx(mempoolData, btcFeeReceivers).getResult());

        log.info("expected: paid the correct amount of BSQ fees with two UTXOs");
        offerData = "qmmtead,94b2589f3270caa0df63437707d4442cae34498ee5b0285090deed9c0ce8584d,800000,11,0,705301";
        mempoolData = "{\"txid\":\"94b2589f3270caa0df63437707d4442cae34498ee5b0285090deed9c0ce8584d\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":577}},{\"vout\":0,\"prevout\":{\"value\":19989}},{\"vout\":2,\"prevout\":{\"value\":3008189}}],\"vout\":[{\"scriptpubkey_address\":\"bc1q48p2nvqf3tepjy7x33c5sfx3tp89e8c05z46cs\",\"value\":20555},{\"scriptpubkey_address\":\"bc1q9h69k8l0vy2yv3c72lw2cgn95sd7hlwjjzul05\",\"value\":920000},{\"scriptpubkey_address\":\"bc1qxmwscy2krw7zzfryw5g8868dexfy6pnq9yx3rv\",\"value\":2085750}],\"size\":550,\"weight\":1228,\"fee\":2450,\"status\":{\"confirmed\":true,\"block_height\":705301}}";
        assertTrue(createTxValidator(offerData).parseJsonValidateMakerFeeTx(mempoolData, btcFeeReceivers).getResult());

        log.info("expected: UNDERPAID expected 1.01 BSQ, actual fee paid 0.40 BSQ (USED 4.00 RATE INSTEAD OF 10.06 RATE");
        offerData = "48067552,3b6009da764b71d79a4df8e2d8960b6919cae2e9bdccd5ef281e261fa9cd31b3,10000000,40,0,667656";
        //mempoolData = "{\"txid\":\"3b6009da764b71d79a4df8e2d8960b6919cae2e9bdccd5ef281e261fa9cd31b3\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":9717}},{\"vout\":0,\"prevout\":{\"value\":4434912}},{\"vout\":2,\"prevout\":{\"value\":12809932}}],\"vout\":[{\"scriptpubkey_address\":\"1Nzqa4J7ck5bgz7QNXKtcjZExAvReozFo4\",\"value\":9637},{\"scriptpubkey_address\":\"bc1qhmmulf5prreqhccqy2wqpxxn6dcye7ame9dd57\",\"value\":11500000},{\"scriptpubkey_address\":\"bc1qx6hg8km2jdjc5ukhuedmkseu9wlsjtd8zeajpj\",\"value\":5721894}],\"size\":553,\"weight\":1879,\"fee\":23030,\"status\":{\"confirmed\":true,\"block_height\":667660}}";
        assertFalse(createTxValidator(offerData).validateBsqFeeTx(true).getResult());

        log.info("expected: LENIENCY Expected fee: 0.61 BSQ, actual fee paid: 0.35 BSQ (USED 5.75 RATE INSTEAD OF 10.06 RATE");
        offerData = "am7DzIv,4cdea8872a7d96210f378e0221dc1aae8ee9abb282582afa7546890fb39b7189,6100000,35,0,668195";
        //mempoolData = "{\"txid\":\"4cdea8872a7d96210f378e0221dc1aae8ee9abb282582afa7546890fb39b7189\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":23893}},{\"vout\":1,\"prevout\":{\"value\":1440000}},{\"vout\":2,\"prevout\":{\"value\":16390881}}],\"vout\":[{\"scriptpubkey_address\":\"1Kmrzq3WGCQsZw5kroEphuk1KgsEr65yB7\",\"value\":23858},{\"scriptpubkey_address\":\"bc1qyw5qql9m7rkse9mhcun225nrjpwycszsa5dpjg\",\"value\":7015000},{\"scriptpubkey_address\":\"bc1q90y3p6mg0pe3rvvzfeudq4mfxafgpc9rulruff\",\"value\":10774186}],\"size\":554,\"weight\":1559,\"fee\":41730,\"status\":{\"confirmed\":true,\"block_height\":668198}}";

        assertTrue(createTxValidator(offerData).validateBsqFeeTx(true).getResult());

        log.info("expected: LENIENCY expected 0.11 BSQ, actual fee paid 0.08 BSQ (USED 5.75 RATE INSTEAD OF 7.53");
        offerData = "F1dzaFNQ,f72e263947c9dee6fbe7093fc85be34a149ef5bcfdd49b59b9cc3322fea8967b,1440000,8,0,670822, bsq paid too little";
        //mempoolData = "{\"txid\":\"f72e263947c9dee6fbe7093fc85be34a149ef5bcfdd49b59b9cc3322fea8967b\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":15163}},{\"vout\":2,\"prevout\":{\"value\":6100000}}],\"vout\":[{\"scriptpubkey_address\":\"1MEsc2m4MSomNJWSr1p6fhnUQMyA3DRGrN\",\"value\":15155},{\"scriptpubkey_address\":\"bc1qztgwe9ry9a9puchjuscqdnv4v9lsm2ut0jtfec\",\"value\":2040000},{\"scriptpubkey_address\":\"bc1q0nstwxc0vqkj4x000xt328mfjapvlsd56nn70h\",\"value\":4048308}],\"size\":406,\"weight\":1291,\"fee\":11700,\"status\":{\"confirmed\":true,\"block_height\":670823}}";

        assertTrue(createTxValidator(offerData).validateBsqFeeTx(true).getResult());
    }

    @Test
    public void testTakerTx() {
        String mempoolData, offerData;

        log.info("========== test case: The fee was what we expected (5000 sats)");
        offerData = "00072328,3524364062c96ba0280621309e8b539d152154422294c2cf263a965dcde9a8ca,1000000,6000,1,614672";
        mempoolData = "{\"txid\":\"3524364062c96ba0280621309e8b539d152154422294c2cf263a965dcde9a8ca\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":1,\"prevout\":{\"value\":2971000}}],\"vout\":[{\"scriptpubkey_address\":\"3A8Zc1XioE2HRzYfbb5P8iemCS72M6vRJV\",\"value\":6000},{\"scriptpubkey_address\":\"1Hxu2X9Nr2fT3qEk9yjhiF54TJEz1Cxjoa\",\"value\":1607600},{\"scriptpubkey_address\":\"16VP6nHDDkmCMwaJj4PeyVHB88heDdVu9e\",\"value\":1353600}],\"size\":257,\"weight\":1028,\"fee\":3800,\"status\":{\"confirmed\":true,\"block_height\":614672}}";
        assertTrue(createTxValidator(offerData).parseJsonValidateTakerFeeTx(mempoolData, btcFeeReceivers).getResult());

        log.info("========== test case: The fee matched what we expected (BSQ)");
        offerData = "00072328,12f658954890d38ce698355be0b27fdd68d092c7b1b7475381918db060f46166,6250000,188,0,615955";
        mempoolData = "{\"txid\":\"12f658954890d38ce698355be0b27fdd68d092c7b1b7475381918db060f46166\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":19980}},{\"vout\":2,\"prevout\":{\"value\":2086015}},{\"vout\":0,\"prevout\":{\"value\":1100000}},{\"vout\":2,\"prevout\":{\"value\":938200}}],\"vout\":[{\"scriptpubkey_address\":\"17qiF1TYgT1YvsCPJyXQoKMtBZ7YJBW9GH\",\"value\":19792},{\"scriptpubkey_address\":\"16aFKD5hvEjJgPme5yRNJT2rAPdTXzdQc2\",\"value\":3768432},{\"scriptpubkey_address\":\"1D5V3QW8f5n4PhwfPgNkW9eWZwNJFyVU8n\",\"value\":346755}],\"size\":701,\"weight\":2804,\"fee\":9216,\"status\":{\"confirmed\":true,\"block_height\":615955}}";
        assertTrue(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case: No BSQ was burnt (error)");
        offerData = "NOBURN,12f658954890d38ce698355be0b27fdd68d092c7b1b7475381918db060f46166,6250000,0,0,615955";
        mempoolData = "{\"txid\":\"12f658954890d38ce698355be0b27fdd68d092c7b1b7475381918db060f46166\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":19980}},{\"vout\":2,\"prevout\":{\"value\":2086015}},{\"vout\":0,\"prevout\":{\"value\":1100000}},{\"vout\":2,\"prevout\":{\"value\":938200}}],\"vout\":[{\"scriptpubkey_address\":\"17qiF1TYgT1YvsCPJyXQoKMtBZ7YJBW9GH\",\"value\":19980},{\"scriptpubkey_address\":\"16aFKD5hvEjJgPme5yRNJT2rAPdTXzdQc2\",\"value\":3768432},{\"scriptpubkey_address\":\"1D5V3QW8f5n4PhwfPgNkW9eWZwNJFyVU8n\",\"value\":346755}],\"size\":701,\"weight\":2804,\"fee\":9216,\"status\":{\"confirmed\":true,\"block_height\":615955}}";
        assertFalse(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case: No BSQ input (error)");
        offerData = "NOBSQ,12f658954890d38ce698355be0b27fdd68d092c7b1b7475381918db060f46166,6250000,0,0,615955";
        mempoolData = "{\"txid\":\"12f658954890d38ce698355be0b27fdd68d092c7b1b7475381918db060f46166\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":2,\"prevout\":{\"value\":2086015}},{\"vout\":0,\"prevout\":{\"value\":1100000}},{\"vout\":2,\"prevout\":{\"value\":938200}}],\"vout\":[{\"scriptpubkey_address\":\"16aFKD5hvEjJgPme5yRNJT2rAPdTXzdQc2\",\"value\":3768432},{\"scriptpubkey_address\":\"1D5V3QW8f5n4PhwfPgNkW9eWZwNJFyVU8n\",\"value\":346755}],\"size\":701,\"weight\":2804,\"fee\":9216,\"status\":{\"confirmed\":true,\"block_height\":615955}}";
        assertFalse(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case: The fee was what we expected: (7000 sats)");
        offerData = "bsqtrade,dfa4555ab78c657cad073e3f29c38c563d9dafc53afaa8c6af28510c734305c4,1000000,10,1,662390";
        mempoolData = "{\"txid\":\"dfa4555ab78c657cad073e3f29c38c563d9dafc53afaa8c6af28510c734305c4\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":678997}}],\"vout\":[{\"scriptpubkey_address\":\"3EfRGckBQQuk7cpU7SwatPv8kFD1vALkTU\",\"value\":7000},{\"scriptpubkey_address\":\"bc1qu6vey3e7flzg8gmhun05m43uc2vz0ay33kuu6r\",\"value\":647998}],\"size\":224,\"weight\":566,\"fee\":23999,\"status\":{\"confirmed\":true,\"block_height\":669720}}";
        assertTrue(createTxValidator(offerData).parseJsonValidateTakerFeeTx(mempoolData, btcFeeReceivers).getResult());

        log.info("========== test case: The fee matched what we expected");
        offerData = "89284,e1269aad63b3d894f5133ad658960971ef5c0fce6a13ad10544dc50fa3360588,900000,47,0,666473";
        mempoolData = "{\"txid\":\"e1269aad63b3d894f5133ad658960971ef5c0fce6a13ad10544dc50fa3360588\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":72738}},{\"vout\":0,\"prevout\":{\"value\":1600000}}],\"vout\":[{\"scriptpubkey_address\":\"17Kh5Ype9yNomqRrqu2k1mdV5c6FcKfGwQ\",\"value\":72691},{\"scriptpubkey_address\":\"bc1qdr9zcw7gf2sehxkux4fmqujm5uguhaqz7l9lca\",\"value\":629016},{\"scriptpubkey_address\":\"bc1qgqrrqv8q6l5d3t52fe28ghuhz4xqrsyxlwn03z\",\"value\":956523}],\"size\":404,\"weight\":1286,\"fee\":14508,\"status\":{\"confirmed\":true,\"block_height\":672388}}";
        assertTrue(createTxValidator(offerData).parseJsonValidateTakerFeeTx(mempoolData, btcFeeReceivers).getResult());

        log.info("========== test case for UNDERPAID: Expected fee: 7.04 BSQ, actual fee paid: 1.01 BSQ");
        offerData = "VOxRS,e99ea06aefc824fd45031447f7a0b56efb8117a09f9b8982e2c4da480a3a0e91,10000000,101,0,669129";
        mempoolData = "{\"txid\":\"e99ea06aefc824fd45031447f7a0b56efb8117a09f9b8982e2c4da480a3a0e91\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":16739}},{\"vout\":2,\"prevout\":{\"value\":113293809}}],\"vout\":[{\"scriptpubkey_address\":\"1F14nF6zoUfJkqZrFgdmK5VX5QVwEpAnKW\",\"value\":16638},{\"scriptpubkey_address\":\"bc1q80y688ev7u43vqy964yf7feqddvt2mkm8977cm\",\"value\":11500000},{\"scriptpubkey_address\":\"bc1q9whgyc2du9mrgnxz0nl0shwpw8ugrcae0j0w8p\",\"value\":101784485}],\"size\":406,\"weight\":1291,\"fee\":9425,\"status\":{\"confirmed\":true,\"block_height\":669134}}";
        assertFalse(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case for UNDERPAID: Expected fee: 1029000 sats BTC, actual fee paid: 441000 sats BTC because they used the default rate of 0.003 should have been 0.007 per BTC");
        // after 1.6.0 we introduce additional leniency to allow the default rate (which is not stored in the DAO param change list)
        // after 1.8.0 we use fee values obtained from filter => result=false meaning the user did not pay enough
        offerData = "AKA,6779b7571f21a5a1af01d675bf032b8a3c571416d05345491018cbc2d016e888,147000000,441000,1,676543";
        mempoolData = "{'txid':'6779b7571f21a5a1af01d675bf032b8a3c571416d05345491018cbc2d016e888','version':1,'locktime':0,'vin':[{'txid':'94c36c0a9c5c99844ddfe17ef05a3ebbe94b14d76ee4bed7b00c7d45e681b441','vout':0,'prevout':{'scriptpubkey_address':'bc1qt5uprdzeh9g4el0k9cttn40qzagvpca9q0q6vl','value':177920825},'sequence':4294967295}],'vout':[{'scriptpubkey_address':'19BNi5EpZhgBBWAt5ka7xWpJpX2ZWJEYyq','value':441000},{'scriptpubkey_address':'bc1qxxcl9dz6usrx4z456g6fg8n3u9327hl458d6mx','value':177008388},{'scriptpubkey_address':'bc1qdq0894p2nmg04ceyqgapln6addfl80zy7z36jd','value':467243}],'size':256,'weight':697,'fee':4194,'status':{'confirmed':true,'block_height':676543}}";
        assertFalse(createTxValidator(offerData).parseJsonValidateTakerFeeTx(mempoolData, btcFeeReceivers).getResult());

        log.info("========== test case for UNDERPAID: Expected fee: 2.12 BSQ, actual fee paid: 0.03 BSQ -- this is the example from the BSQ fee scammer Oct 2021");
        offerData = "957500,26e1a5e1f842cb7baa18bd197bd084e7f043d07720b9853e947158eb0a32677d,2000000,101,3,709426";
        mempoolData = "{\"txid\":\"26e1a5e1f842cb7baa18bd197bd084e7f043d07720b9853e947158eb0a32677d\",\"version\":1,\"locktime\":0,\"vin\":[{\"txid\":\"\",\"vout\":0,\"prevout\":{\"scriptpubkey\":\"\",\"scriptpubkey_asm\":\"\",\"scriptpubkey_type\":\"v0_p2wpkh\",\"scriptpubkey_address\":\"\",\"value\":3688},\"scriptsig\":\"\",\"scriptsig_asm\":\"\",\"witness\":[\"\",\"\"],\"is_coinbase\":false,\"sequence\":4294967295},{\"txid\":\"\",\"vout\":2,\"prevout\":{\"scriptpubkey\":\"\",\"scriptpubkey_asm\":\"\",\"scriptpubkey_type\":\"v0_p2wpkh\",\"scriptpubkey_address\":\"\",\"value\":796203},\"scriptsig\":\"\",\"scriptsig_asm\":\"\",\"witness\":[\"\",\"\"],\"is_coinbase\":false,\"sequence\":4294967295}],\"vout\":[{\"scriptpubkey\":\"\",\"scriptpubkey_asm\":\"\",\"scriptpubkey_type\":\"v0_p2wpkh\",\"scriptpubkey_address\":\"bc1qydcyfe7kp6968hywcp0uek2xvgem3nlx0x0hfy\",\"value\":3685},{\"scriptpubkey\":\"\",\"scriptpubkey_asm\":\"\",\"scriptpubkey_type\":\"v0_p2wpkh\",\"scriptpubkey_address\":\"bc1qc4amk6sd3c4gzxjgd5sdlaegt0r5juq54vnrll\",\"value\":503346},{\"scriptpubkey\":\"\",\"scriptpubkey_asm\":\"\",\"scriptpubkey_type\":\"v0_p2wpkh\",\"scriptpubkey_address\":\"bc1q66e7m8y5lzfk5smg2a80xeaqzhslgeavg9y70t\",\"value\":291187}],\"size\":403,\"weight\":958,\"fee\":1673,\"status\":{\"confirmed\":true,\"block_height\":709426,\"block_hash\":\"\",\"block_time\":1636751288}}";
        assertFalse(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case: expected fee paid using two BSQ UTXOs");
        offerData = "ZHNYCAE,a91c6f1cb62721a7943678547aa814d6f29125ed63ad076073eb5ae7f16a76e9,83000000,8796,0,717000";
        mempoolData = "{\"txid\":\"a91c6f1cb62721a7943678547aa814d6f29125ed63ad076073eb5ae7f16a76e9\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":3510}},{\"vout\":0,\"prevout\":{\"value\":6190}},{\"vout\":0,\"prevout\":{\"value\":46000000}}],\"vout\":[{\"scriptpubkey_address\":\"bc1qmqphx028eu4tzdvgccf5re52qtv6pmjanrpq29\",\"value\":904},{\"scriptpubkey_address\":\"bc1qtkvu4zeh0g0pce452335tgnswxd8ayxlktfj2s\",\"value\":30007648},{\"scriptpubkey_address\":\"bc1qdatwgzrrntp2m53tpzmax4dxu6md2c0c9vj8ut\",\"value\":15997324}],\"size\":549,\"weight\":1227,\"fee\":3824,\"status\":{\"confirmed\":true,\"block_height\":716444}}";
        assertTrue(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case: expected fee paid using three BSQ UTXOs");
        offerData = "3UTXOGOOD,c7dddc267a366fa1d87840eeb0dcd89918a886ccb9aabee80f667635a5d4e262,200000000,17888,0,733715";
        mempoolData = "{\"txid\":\"c7dddc267a366fa1d87840eeb0dcd89918a886ccb9aabee80f667635a5d4e262\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":9833}},{\"vout\":0,\"prevout\":{\"value\":1362}},{vout\":0,\"prevout\":{\"value\":17488}},{\"vout\":2,\"prevout\":{\"value\":573360131}}],\"vout\":[{\"scriptpubkey_address\":\"bc1qvwpm87kmrlgave9srxk6nfwleehll0kxetu5j0\",\"value\":10795},{\"scriptpubkey_address\":\"bc1qz5n83ppfpdznnzff4e7tjep5c6f6jce9mqnrzh\",\"value\":230004780},{\"scriptpubkey_address\":\"bc1qcfyjajhuv55fyu6g5ug664r57u9a7qg55cgt5p\",\"value\":343370849}],\"size\":699,\"weight\":1500,\"fee\":2390,\"status\":{\"confirmed\":true,\"block_height\":733715}}";
        assertTrue(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case: expected fee paid using four BSQ UTXOs");
        offerData = "4UTXOGOOD,c7dddc267a366fa1d87840eeb0dcd89918a886ccb9aabee80f667635a5d4e262,200000000,17888,0,733715";
        mempoolData = "{\"txid\":\"c7dddc267a366fa1d87840eeb0dcd89918a886ccb9aabee80f667635a5d4e262\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":4833}},{\"vout\":0,\"prevout\":{\"value\":5000}},{\"vout\":0,\"prevout\":{\"value\":1362}},{vout\":0,\"prevout\":{\"value\":17488}},{\"vout\":2,\"prevout\":{\"value\":573360131}}],\"vout\":[{\"scriptpubkey_address\":\"bc1qvwpm87kmrlgave9srxk6nfwleehll0kxetu5j0\",\"value\":10795},{\"scriptpubkey_address\":\"bc1qz5n83ppfpdznnzff4e7tjep5c6f6jce9mqnrzh\",\"value\":230004780},{\"scriptpubkey_address\":\"bc1qcfyjajhuv55fyu6g5ug664r57u9a7qg55cgt5p\",\"value\":343370849}],\"size\":699,\"weight\":1500,\"fee\":2390,\"status\":{\"confirmed\":true,\"block_height\":733715}}";
        assertTrue(createTxValidator(offerData).validateBsqFeeTx(false).getResult());

        log.info("========== test case: three BSQ UTXOs, but fee paid is too low");
        offerData = "3UTXOLOWFEE,c7dddc267a366fa1d87840eeb0dcd89918a886ccb9aabee80f667635a5d4e262,200000000,101,0,733715";
        mempoolData = "{\"txid\":\"c7dddc267a366fa1d87840eeb0dcd89918a886ccb9aabee80f667635a5d4e262\",\"version\":1,\"locktime\":0,\"vin\":[{\"vout\":0,\"prevout\":{\"value\":9833}},{\"vout\":0,\"prevout\":{\"value\":1362}},{vout\":0,\"prevout\":{\"value\":1362}},{\"vout\":2,\"prevout\":{\"value\":573360131}}],\"vout\":[{\"scriptpubkey_address\":\"bc1qvwpm87kmrlgave9srxk6nfwleehll0kxetu5j0\",\"value\":10795},{\"scriptpubkey_address\":\"bc1qz5n83ppfpdznnzff4e7tjep5c6f6jce9mqnrzh\",\"value\":230004780},{\"scriptpubkey_address\":\"bc1qcfyjajhuv55fyu6g5ug664r57u9a7qg55cgt5p\",\"value\":343370849}],\"size\":699,\"weight\":1500,\"fee\":2390,\"status\":{\"confirmed\":true,\"block_height\":733715}}";
        assertFalse(createTxValidator(offerData).validateBsqFeeTx(false).getResult());


    }

    @Test
    public void testGoodOffers() {
        Map<String, String> goodOffers = loadJsonTestData("offerTestData.json");
        Map<String, String> mempoolData = loadJsonTestData("txInfo.json");
        assertTrue(goodOffers.size() > 0);
        assertTrue(mempoolData.size() > 0);
        log.warn("TESTING GOOD OFFERS");
        testOfferSet(goodOffers, mempoolData, true);
    }

    @Test
    public void testBadOffers() {
        Map<String, String> badOffers = loadJsonTestData("badOfferTestData.json");
        Map<String, String> mempoolData = loadJsonTestData("txInfo.json");
        assertTrue(badOffers.size() > 0);
        assertTrue(mempoolData.size() > 0);
        log.warn("TESTING BAD OFFERS");
        testOfferSet(badOffers, mempoolData, false);
    }

    private void testOfferSet(Map<String, String> offers, Map<String, String> mempoolData, boolean expectedResult) {
        Set<String> knownValuesList = new HashSet<>(offers.values());
        knownValuesList.forEach(offerData -> {
            TxValidator txValidator = createTxValidator(offerData);
            log.warn("TESTING {}", txValidator.getTxId());
            if (txValidator.getIsFeeCurrencyBtc() != null && txValidator.getIsFeeCurrencyBtc()) {
                String jsonTxt = mempoolData.get(txValidator.getTxId());
                if (jsonTxt == null || jsonTxt.isEmpty()) {
                    log.warn("{} was not found in the mempool", txValidator.getTxId());
                    assertFalse(expectedResult);  // tx was not found in explorer
                } else {
                    txValidator.parseJsonValidateMakerFeeTx(jsonTxt, btcFeeReceivers);
                    assertTrue(expectedResult == txValidator.getResult());
                }
            } else {
                txValidator.validateBsqFeeTx(true);
                assertTrue(expectedResult == txValidator.getResult());
            }
        });
    }

    private Map<String, String> loadJsonTestData(String fileName) {
        String json = "";
        try {
            json = IOUtils.toString(this.getClass().getResourceAsStream(fileName), "UTF-8");
        } catch (IOException e) {
            log.error(e.toString());
        }
        Map<String, String> map = new Gson().fromJson(json, Map.class);
        return map;
    }

    // initialize the TxValidator with offerData to be validated
    // and mock the used DaoStateService
    private TxValidator createTxValidator(String offerData) {
        try {
            String[] y = offerData.split(",");
            String txId = y[1];
            long amount = Long.parseLong(y[2]);
            long feePaid = Long.parseLong(y[3]);
            boolean isCurrencyForMakerFeeBtc = Long.parseLong(y[4]) > 0;
            long feePaymentBlockHeight = Long.parseLong(y[5]);
            DaoStateService mockedDaoStateService = mock(DaoStateService.class);
            Tx mockedTx = mock(Tx.class);

            Answer<Coin> mockGetFeeRate = invocation -> {
                return mockedLookupFeeRate(invocation.getArgument(0), invocation.getArgument(1));
            };
            Answer<Coin> mockGetParamValueAsCoin = invocation -> {
                return mockedGetParamValueAsCoin(invocation.getArgument(0), invocation.getArgument(1));
            };
            Answer<List<Coin>> mockGetParamChangeList = invocation -> {
                return mockedGetParamChangeList(invocation.getArgument(0));
            };
            Answer<Optional<Tx>> mockGetBsqTx = invocation -> {
                return Optional.of(mockedTx);
            };
            Answer<Long> mockGetBurntBsq = invocation -> {
                return feePaid;
            };
            when(mockedDaoStateService.getParamValueAsCoin(Mockito.any(Param.class), Mockito.anyInt())).thenAnswer(mockGetFeeRate);
            when(mockedDaoStateService.getParamValueAsCoin(Mockito.any(Param.class), Mockito.anyString())).thenAnswer(mockGetParamValueAsCoin);
            when(mockedDaoStateService.getParamChangeList(Mockito.any())).thenAnswer(mockGetParamChangeList);
            when(mockedDaoStateService.getTx(Mockito.any())).thenAnswer(mockGetBsqTx);
            when(mockedTx.getBurntBsq()).thenAnswer(mockGetBurntBsq);

            Answer<Long> getMakerFeeBsq = invocation -> 1514L;
            Answer<Long> getTakerFeeBsq = invocation -> 10597L;
            Answer<Long> getMakerFeeBtc = invocation -> 100000L;
            Answer<Long> getTakerFeeBtc = invocation -> 700000L;
            Filter mockedFilter = mock(Filter.class);
            when(mockedFilter.getMakerFeeBsq()).thenAnswer(getMakerFeeBsq);
            when(mockedFilter.getTakerFeeBsq()).thenAnswer(getTakerFeeBsq);
            when(mockedFilter.getMakerFeeBtc()).thenAnswer(getMakerFeeBtc);
            when(mockedFilter.getTakerFeeBtc()).thenAnswer(getTakerFeeBtc);
            FilterManager filterManager = mock(FilterManager.class);
            when(filterManager.getFilter()).thenReturn(mockedFilter);
            TxValidator txValidator = new TxValidator(mockedDaoStateService, txId, Coin.valueOf(amount), isCurrencyForMakerFeeBtc, feePaymentBlockHeight, filterManager);
            return txValidator;
        } catch (RuntimeException ignore) {
            // If input format is not as expected we ignore entry
        }
        return null;
    }

    Coin mockedLookupFeeRate(Param param, int blockHeight) {
        BsqFormatter bsqFormatter = new BsqFormatter();
        LinkedHashMap<Long, String> feeMap = mockedGetFeeRateMap(param);
        for (Map.Entry<Long, String> entry : feeMap.entrySet()) {
            if (blockHeight >= entry.getKey()) {
                if (param.equals(Param.DEFAULT_MAKER_FEE_BTC) || param.equals(Param.DEFAULT_TAKER_FEE_BTC))
                    return bsqFormatter.parseToBTC(entry.getValue());
                else
                    return ParsingUtils.parseToCoin(entry.getValue(), bsqFormatter);
            }
        }
        if (param.equals(Param.DEFAULT_MAKER_FEE_BTC) || param.equals(Param.DEFAULT_TAKER_FEE_BTC))
            return bsqFormatter.parseToBTC(param.getDefaultValue());
        else
            return ParsingUtils.parseToCoin(param.getDefaultValue(), bsqFormatter);
    }

    private LinkedHashMap<Long, String> mockedGetFeeRateMap(Param param) {
        LinkedHashMap<Long, String> feeMap = new LinkedHashMap<>();
        if (param == Param.DEFAULT_MAKER_FEE_BSQ) {
            feeMap.put(754620L, "14.21");   // https://github.com/bisq-network/proposals/issues/380
            feeMap.put(750000L, "17.19");   // https://github.com/bisq-network/proposals/issues/379
            feeMap.put(721063L, "12.78");   // https://github.com/bisq-network/proposals/issues/357
            feeMap.put(706305L, "15.14");   // https://github.com/bisq-network/proposals/issues/345
            feeMap.put(697011L, "13.16");   // https://github.com/bisq-network/proposals/issues/339
            feeMap.put(682901L, "11.45");   // https://github.com/bisq-network/proposals/issues/333
            feeMap.put(677862L, "9.95");    // https://github.com/bisq-network/proposals/issues/325
            feeMap.put(674707L, "8.66");    // https://github.com/bisq-network/proposals/issues/318
            feeMap.put(670027L, "7.53");
            feeMap.put(660667L, "10.06");
            feeMap.put(655987L, "8.74");
            feeMap.put(641947L, "7.6");
            feeMap.put(632587L, "6.6");
            feeMap.put(623227L, "5.75");
            feeMap.put(599827L, "10.0");
            feeMap.put(590467L, "13.0");
            feeMap.put(585787L, "8.0");
            feeMap.put(581107L, "1.6");
        } else if (param == Param.DEFAULT_TAKER_FEE_BSQ) {
            feeMap.put(754620L, "104.24");  // https://github.com/bisq-network/proposals/issues/380
            feeMap.put(750000L, "126.02");  // https://github.com/bisq-network/proposals/issues/379
            feeMap.put(721063L, "89.44");   // https://github.com/bisq-network/proposals/issues/357
            feeMap.put(706305L, "105.97");  // https://github.com/bisq-network/proposals/issues/345
            feeMap.put(697011L, "92.15");   // https://github.com/bisq-network/proposals/issues/339
            feeMap.put(682901L, "80.13");   // https://github.com/bisq-network/proposals/issues/333
            feeMap.put(677862L, "69.68");   // https://github.com/bisq-network/proposals/issues/325
            feeMap.put(674707L, "60.59");   // https://github.com/bisq-network/proposals/issues/318
            feeMap.put(670027L, "52.68");
            feeMap.put(660667L, "70.39");
            feeMap.put(655987L, "61.21");
            feeMap.put(641947L, "53.23");
            feeMap.put(632587L, "46.30");
            feeMap.put(623227L, "40.25");
            feeMap.put(599827L, "30.00");
            feeMap.put(590467L, "38.00");
            feeMap.put(585787L, "24.00");
            feeMap.put(581107L, "4.80");
        } else if (param == Param.DEFAULT_MAKER_FEE_BTC) {
            feeMap.put(623227L, "0.0010");
            feeMap.put(585787L, "0.0020");
        } else if (param == Param.DEFAULT_TAKER_FEE_BTC) {
            feeMap.put(623227L, "0.0070");
            feeMap.put(585787L, "0.0060");
        }
        return feeMap;
    }

    public Coin mockedGetParamValueAsCoin(Param param, String paramValue) {
        BsqFormatter bsqFormatter = new BsqFormatter();
        return bsqFormatter.parseParamValueToCoin(param, paramValue);
    }

    public List<Coin> mockedGetParamChangeList(Param param) {
        BsqFormatter bsqFormatter = new BsqFormatter();
        List<Coin> retVal = new ArrayList<Coin>();
        Map<Long, String> feeMap = mockedGetFeeRateMap(param);
        for (Map.Entry<Long, String> entry : feeMap.entrySet()) {
            retVal.add(ParsingUtils.parseToCoin(entry.getValue(), bsqFormatter));
        }
        return retVal;
    }
}
