package io.bitsquare.btc.provider.squ;

import io.bitsquare.common.util.Tuple2;
import io.bitsquare.http.HttpClient;
import io.bitsquare.http.HttpException;
import io.bitsquare.messages.btc.provider.HttpClientProvider;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.bitcoinj.core.Utils.HEX;

public class BsqUtxoFeedProvider extends HttpClientProvider {
    private static final Logger log = LoggerFactory.getLogger(BsqUtxoFeedProvider.class);

    public BsqUtxoFeedProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl, false);
    }

    public Tuple2<Map<String, Long>, BsqUtxoFeedData> getBsqUtxo() throws IOException, HttpException {
      /*  String json = httpClient.requestWithGET("getBsqUtxo", "User-Agent", "bisq/" + Version.VERSION + ", uid:" + httpClient.getUid());
        LinkedTreeMap<String, Object> linkedTreeMap = new Gson().fromJson(json, LinkedTreeMap.class);
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("getBsqUtxoTs", ((Double) linkedTreeMap.get("getBsqUtxoTs")).longValue());

        LinkedTreeMap<String, Double> dataMap = (LinkedTreeMap<String, Double>) linkedTreeMap.get("data");
       // BsqUtxoData squUtxoData = new BsqUtxoData(dataMap.get("txFee").longValue(), dataMap.get("createOfferFee").longValue(), dataMap.get("takeOfferFee").longValue());
       */

        // mock
        Map<String, Long> tsMap = new HashMap<>();
        tsMap.put("getBsqUtxoTs", 1L);

        Set<UTXO> utxoSet = new HashSet<>();

        /*
   >>> PENDING:
Sends 0.00 BTC and receives 0.0001 BTC, total value 0.0001 BTC.
  ef07b4ce136c1230f9b4a8b9a7914b2f3e1ff1c8da83c50f4190796fd0f69514: Seen by 1 peer. Pending/unconfirmed.
  time locked until block 259 (estimated to be reached at Sun Dec 11 07:35:34 CET 2016)
     in   PUSHDATA(71)[304402205159d8b87b72bdd988c172806bcd63712c002bdbe94a84759016c702d339730102201101f78cf11c2ad63e2e54fcb7b9afbd99190269163178b18bbe84daf780535501] PUSHDATA(33)[02bb475a2333c036b1ed4afb4747d0b0464858e35c784c3c16fb96ff7423aeaca7]
          outpoint:3148d764241e00fe46c6b4d7ed60543608a422ca6dc760aad590990355c42e1d:1
     out  DUP HASH160 PUSHDATA(20)[b9466f6ee06ddf0709d99dcee5b46a5693a17f9c] EQUALVERIFY CHECKSIG 0.0453858 BTC (aw scriptPubKey: 76a914b9466f6ee06ddf0709d99dcee5b46a5693a17f9c88ac) 
     out  DUP HASH160 PUSHDATA(20)[bf20c68f946157aa3e68c610f220b0dadae80a23] EQUALVERIFY CHECKSIG 0.0001 BTC (aw scriptPubKey: 76a914bf20c68f946157aa3e68c610f220b0dadae80a2388ac) 

>>> UNSPENT:
Sends 0.00 BTC and receives 0.00001 BTC, total value 0.00001 BTC.
  ae71bba68ceac54af639f4b8d86ed1a720a5c9cf23285f1b8ce707d9ce848a04: Appeared in best chain at height 333, depth 1.
  time locked until block 332 (estimated to be reached at Sun Dec 11 19:45:34 CET 2016)
     in   PUSHDATA(71)[304402207b42682e83a21cfa7c42b9cb8c077061e5a43de3b972296f1240526060d09de5022019611503aaeff6a9dd6576f7016e124bfb0ff316ec200cc7bb97899c8148f65801] PUSHDATA(33)[03cea6bb8e9a1b10a5adeaadac820acea691af18a65d1deb98f478231f256570a9]
          outpoint:86526aae12247453fcf02f71e5616437d93de7ffbc82f7631225b6bba988d351:0
     out  DUP HASH160 PUSHDATA(20)[cc907567ff7e61e023f5e9e28e333d4149481e4b] EQUALVERIFY CHECKSIG 0.014768 BTC (aw scriptPubKey: 76a914cc907567ff7e61e023f5e9e28e333d4149481e4b88ac) 
     out  DUP HASH160 PUSHDATA(20)[027aea6622e5a3f4c4c48e2935544b92008176a1] EQUALVERIFY CHECKSIG 0.00001 BTC (aw scriptPubKey: 76a914027aea6622e5a3f4c4c48e2935544b92008176a188ac) 
         */
        UTXO utxo1 = new UTXO(Sha256Hash.of(Utils.HEX.decode("a054a0066b8df03a82a543cacf7e4a0516a3910c70c1d9baac58a1c12d9ef6a7")),
                0,
                Coin.valueOf(222000),
                336,
                false,
                new Script(HEX.decode("76a91409acb7a09afade332fde6d741e59fe251bae332088ac")),
                "mgQ7HPV9WvQvXLSYGuAvLgiTMkQd55iBCe");
        utxoSet.add(utxo1);

        UTXO utxo2 = new UTXO(Sha256Hash.of(Utils.HEX.decode("a054a0066b8df03a82a543cacf7e4a0516a3910c70c1d9baac58a1c12d9ef6a7")),
                1,
                Coin.valueOf(444000),
                336,
                false,
                new Script(HEX.decode("76a914e867afbc7c0840299d3409b105e56263e40a275688ac")),
                "n2hoGpZku3iiw1x7xfFqVeW75akYPaYevU");
        utxoSet.add(utxo2);

        UTXO utxo3 = new UTXO(Sha256Hash.of(Utils.HEX.decode("10085081b3c7eb3d15bc45eab9f8c4bd17a043f92928ea321f2705370bd44865")),
                0,
                Coin.valueOf(700000),
                443437,
                false,
                new Script(HEX.decode("76a914d95933afd27f70537bdd4eab383e8f6f91d72be788ac")),
                "1LpEVV576FBUvEbRfb5qPkngXoNfW1EgfM");
        utxoSet.add(utxo3);

        UTXO utxo4 = new UTXO(Sha256Hash.of(Utils.HEX.decode("10085081b3c7eb3d15bc45eab9f8c4bd17a043f92928ea321f2705370bd44865")),
                1,
                Coin.valueOf(300000),
                443437,
                false,
                new Script(HEX.decode("76a9143fb608a123ef3c624dcdf5bfcd10a8c320958c3d88ac")),
                "16ose1XjoftsppsyKWUeQSmDPrUq2bAPTE");
        utxoSet.add(utxo4);

        //

        BsqUtxoFeedData bsqUtxoFeedData = new BsqUtxoFeedData(utxoSet);

        return new Tuple2<>(tsMap, bsqUtxoFeedData);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
