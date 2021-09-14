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

package bisq.desktop.components.paymentmethods.data;

import bisq.desktop.util.GUIUtil;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
   Japan's National Banking Association assigns 4 digit codes to all
   Financial Institutions, so we use that as the primary "Bank ID",
   add the English names for the top ~30 major international banks,
   and remove local farmers agricultural cooperative associations
   to keep the list to a reasonable size. Please update annually.

   Source: Zengin Net list of Financial Institutions
   Last Updated: July 16, 2019
   URL: https://www.zengin-net.jp/company/member/
   PDF: https://www.zengin-net.jp/company/pdf/member1.pdf
   PDF: https://www.zengin-net.jp/company/pdf/member2.pdf

   Source: Bank of Japan list of Financial Institutions
   Last Updated: July 16, 2019
   URL: https://www5.boj.or.jp/bojnet/codenew/mokujinew.htm
   File: code1_20190716.xlsx
   Excel sheet: 金融機関等コード一覧
*/

public class JapanBankData {
    /*
       Returns the main list of ~500 banks in Japan with bank codes,
       but since 90%+ of people will be using one of ~30 major banks,
       we hard-code those at the top for easier pull-down selection,
       and add their English names in parenthesis for foreigners.
    */
    public static List<String> prettyPrintBankList() // {{{
    {
        List<String> prettyList = new ArrayList<>();

        // add mega banks at the top
        for (Map.Entry<String, String> bank : megaBanksEnglish.entrySet()) {
            String bankId = bank.getKey();
            String bankNameEn = bank.getValue();
            String bankNameJa = majorBanksJapanese.get(bankId);
            if (bankNameJa == null) bankNameJa = minorBanksJapanese.get(bankId);
            prettyList.add(prettyPrintMajorBank(bankId, bankNameJa, bankNameEn));
        }

        // append the major banks next
        for (Map.Entry<String, String> bank : majorBanksJapanese.entrySet()) {
            String bankId = bank.getKey();
            String bankNameJa = bank.getValue();
            // avoid duplicates
            if (megaBanksEnglish.get(bankId) != null) continue;
            prettyList.add(prettyPrintBank(bankId, bankNameJa));
        }

        // append the minor local banks last
        for (Map.Entry<String, String> bank : minorBanksJapanese.entrySet()) {
            String bankId = bank.getKey();
            String bankNameJa = bank.getValue();
            prettyList.add(prettyPrintBank(bankId, bankNameJa));
        }

        return prettyList;
    } // }}}

    // Pretty print major banks like this: (0001) みずほ (Mizuho Bank)
    private static String prettyPrintMajorBank(String bankId, String bankNameJa, String bankNameEn) // {{{
    {
        return ID_OPEN + bankId + ID_CLOSE + SPACE +
                JA_OPEN + bankNameJa + JA_CLOSE + SPACE +
                EN_OPEN + bankNameEn + EN_CLOSE;
    } // }}}

    // Pretty print other banks like this: (9524) みずほ証券
    private static String prettyPrintBank(String bankId, String bankName) // {{{
    {
        return ID_OPEN + bankId + ID_CLOSE + SPACE +
                JA_OPEN + bankName + JA_CLOSE;
    } // }}}

    // top 30 mega banks with english
    private static final Map<String, String> megaBanksEnglish = ImmutableMap.<String, String>builder()
            // {{{ japan post office
            .put("9900", "Japan Post Bank Yucho")
            // }}}
            // {{{ japan mega-banks
            .put("0001", "Mizuho Bank")
            .put("0005", "Mitsubishi UFJ Bank (MUFG)")
            .put("0009", "Sumitomo Mitsui Banking Corporation (SMBC)")
            .put("0010", "Resona Bank")
            // }}}
            // {{{ major online banks
            .put("0033", "Japan Net Bank")
            .put("0034", "Seven Bank (7-11)")
            .put("0035", "Sony Bank")
            .put("0036", "Rakuten Bank")
            .put("0038", "SBI Sumishin Net Bank")
            .put("0039", "Jibun Bank")
            .put("0040", "Aeon Bank")
            .put("0042", "Lawson Bank")
            // }}}
            // {{{ major trust banks, etc.
            .put("0150", "Suruga Bank")
            .put("0288", "Mitsubishi UFJ Trust Bank")
            .put("0289", "Mizuho Trust Bank")
            .put("0294", "Sumitomo Trust Bank")
            .put("0300", "SMBC Trust Bank (PRESTIA)")
            .put("0304", "Nomura Trust Bank")
            .put("0307", "Orix Trust Bank")
            .put("0310", "GMO Aozora Net Bank")
            .put("0321", "Japan Securities Trust Bank")
            .put("0397", "Shinsei Bank")
            .put("0398", "Aozora Bank")
            .put("0402", "JP Morgan Chase Bank")
            .put("0442", "BNY Mellon")
            .put("0458", "DBS Bank")
            .put("0472", "SBJ Shinhan Bank Japan")
            // }}}
            .build();

    // major ~200 banks
    private static final Map<String, String> majorBanksJapanese = ImmutableMap.<String, String>builder()
            // {{{  ゆうちょ銀行 (9900)
            .put("9900", "ゆうちょ銀行")
            // }}}
            // {{{  都市銀行 (0001 ~ 0029)
            .put("0001", "みずほ銀行")
            .put("0005", "三菱ＵＦＪ銀行")
            .put("0009", "三井住友銀行")
            .put("0010", "りそな銀行")
            .put("0017", "埼玉りそな銀行")
            // }}}
            // {{{  ネット専業銀行等 (0030 ~ 0049)
            .put("0033", "ジャパンネット銀行")
            .put("0034", "セブン銀行")
            .put("0035", "ソニー銀行")
            .put("0036", "楽天銀行")
            .put("0038", "住信ＳＢＩネット銀行")
            .put("0039", "じぶん銀行")
            .put("0040", "イオン銀行")
            .put("0041", "大和ネクスト銀行")
            .put("0042", "ローソン銀行")
            // }}}
            // {{{ 協会 (0050 ~ 0099)
            .put("0051", "全銀協")
            .put("0052", "横浜銀行協会")
            .put("0053", "釧路銀行協会")
            .put("0054", "札幌銀行協会")
            .put("0056", "函館銀行協会")
            .put("0057", "青森銀行協会")
            .put("0058", "秋田銀行協会")
            .put("0059", "宮城銀行協会")
            .put("0060", "福島銀行協会")
            .put("0061", "群馬銀行協会")
            .put("0062", "新潟銀行協会")
            .put("0063", "石川銀行協会")
            .put("0064", "山梨銀行協会")
            .put("0065", "長野銀行協会")
            .put("0066", "静岡銀行協会")
            .put("0067", "名古屋銀行協会")
            .put("0068", "京都銀行協会")
            .put("0069", "大阪銀行協会")
            .put("0070", "神戸銀行協会")
            .put("0071", "岡山銀行協会")
            .put("0072", "広島銀行協会")
            .put("0073", "島根銀行協会")
            .put("0074", "山口銀行協会")
            .put("0075", "香川銀行協会")
            .put("0076", "愛媛銀行協会")
            .put("0077", "高知銀行協会")
            .put("0078", "北九州銀行協会")
            .put("0079", "福岡銀行協会")
            .put("0080", "大分銀行協会")
            .put("0081", "長崎銀行協会")
            .put("0082", "熊本銀行協会")
            .put("0083", "鹿児島銀行協会")
            .put("0084", "沖縄銀行協会")
            .put("0090", "全銀ネット")
            .put("0095", "ＣＬＳＢＡＮＫ")
            // }}}
            // {{{  地方銀行 (0116 ~ 0190)
            .put("0116", "北海道銀行")
            .put("0117", "青森銀行")
            .put("0118", "みちのく銀行")
            .put("0119", "秋田銀行")
            .put("0120", "北都銀行")
            .put("0121", "荘内銀行")
            .put("0122", "山形銀行")
            .put("0123", "岩手銀行")
            .put("0124", "東北銀行")
            .put("0125", "七十七銀行")
            .put("0126", "東邦銀行")
            .put("0128", "群馬銀行")
            .put("0129", "足利銀行")
            .put("0130", "常陽銀行")
            .put("0131", "筑波銀行")
            .put("0133", "武蔵野銀行")
            .put("0134", "千葉銀行")
            .put("0135", "千葉興業銀行")
            .put("0137", "きらぼし銀行")
            .put("0138", "横浜銀行")
            .put("0140", "第四銀行")
            .put("0141", "北越銀行")
            .put("0142", "山梨中央銀行")
            .put("0143", "八十二銀行")
            .put("0144", "北陸銀行")
            .put("0145", "富山銀行")
            .put("0146", "北國銀行")
            .put("0147", "福井銀行")
            .put("0149", "静岡銀行")
            .put("0150", "スルガ銀行")
            .put("0151", "清水銀行")
            .put("0152", "大垣共立銀行")
            .put("0153", "十六銀行")
            .put("0154", "三重銀行")
            .put("0155", "百五銀行")
            .put("0157", "滋賀銀行")
            .put("0158", "京都銀行")
            .put("0159", "関西みらい銀行")
            .put("0161", "池田泉州銀行")
            .put("0162", "南都銀行")
            .put("0163", "紀陽銀行")
            .put("0164", "但馬銀行")
            .put("0166", "鳥取銀行")
            .put("0167", "山陰合同銀行")
            .put("0168", "中国銀行")
            .put("0169", "広島銀行")
            .put("0170", "山口銀行")
            .put("0172", "阿波銀行")
            .put("0173", "百十四銀行")
            .put("0174", "伊予銀行")
            .put("0175", "四国銀行")
            .put("0177", "福岡銀行")
            .put("0178", "筑邦銀行")
            .put("0179", "佐賀銀行")
            .put("0180", "十八銀行")
            .put("0181", "親和銀行")
            .put("0182", "肥後銀行")
            .put("0183", "大分銀行")
            .put("0184", "宮崎銀行")
            .put("0185", "鹿児島銀行")
            .put("0187", "琉球銀行")
            .put("0188", "沖縄銀行")
            .put("0190", "西日本シティ銀行")
            .put("0191", "北九州銀行")
            // }}}
            // {{{  信託銀行 (0288 ~ 0326)
            .put("0288", "三菱ＵＦＪ信託銀行")
            .put("0289", "みずほ信託銀行")
            .put("0294", "三井住友信託銀行")
            .put("0295", "ＢＮＹＭ信託")
            .put("0297", "日本マスタートラスト信託銀行")
            .put("0299", "ステート信託")
            .put("0300", "ＳＭＢＣ信託銀行 プレスティア")
            .put("0304", "野村信託銀行")
            .put("0307", "オリックス銀行")
            .put("0310", "ＧＭＯあおぞらネット銀行")
            .put("0311", "農中信託")
            .put("0320", "新生信託")
            .put("0321", "日証金信託")
            .put("0324", "日本トラスティサービス信託銀行")
            .put("0325", "資産管理サービス信託銀行")
            // }}}
            // {{{  旧長期信用銀行 (0397 ~ 0398)
            .put("0397", "新生銀行")
            .put("0398", "あおぞら銀行")
            // }}}
            // {{{ foreign banks (0400 ~ 0497)
            .put("0401", "シティバンク、エヌ・エイ 銀行")
            .put("0402", "ＪＰモルガン・チェース銀行")
            .put("0403", "アメリカ銀行")
            .put("0411", "香港上海銀行")
            .put("0413", "スタンチヤート")
            .put("0414", "バークレイズ")
            .put("0421", "アグリコル")
            .put("0423", "ハナ")
            .put("0424", "印度")
            .put("0425", "兆豐國際商銀")
            .put("0426", "バンコツク")
            .put("0429", "バンクネガラ")
            .put("0430", "ドイツ銀行")
            .put("0432", "ブラジル")
            .put("0438", "ユーオバシーズ")
            .put("0439", "ユービーエス")
            .put("0442", "ＢＮＹメロン")
            .put("0443", "ビー・エヌ・ピー・パリバ銀行")
            .put("0444", "チヤイニーズ")
            .put("0445", "ソシエテ")
            .put("0456", "ユバフ")
            .put("0458", "ＤＢＳ")
            .put("0459", "パキスタン")
            .put("0460", "クレデイスイス")
            .put("0461", "コメルツ銀行")
            .put("0463", "ウニクレデイト")
            .put("0468", "インドステイト")
            .put("0471", "カナダロイヤル")
            .put("0472", "ＳＢＪ銀行")
            .put("0477", "ウリイ")
            .put("0482", "アイエヌジー")
            .put("0484", "ナツトオース")
            .put("0485", "アンズバンク")
            .put("0487", "コモンウエルス")
            .put("0489", "バンクチヤイナ")
            .put("0495", "ステストリート")
            .put("0498", "中小企業")
            // }}}
            // {{{  第二地方銀行 (0501 ~ 0597)
            .put("0501", "北洋銀行")
            .put("0508", "きらやか銀行")
            .put("0509", "北日本銀行")
            .put("0512", "仙台銀行")
            .put("0513", "福島銀行")
            .put("0514", "大東銀行")
            .put("0516", "東和銀行")
            .put("0517", "栃木銀行")
            .put("0522", "京葉銀行")
            .put("0525", "東日本銀行")
            .put("0526", "東京スター銀行")
            .put("0530", "神奈川銀行")
            .put("0532", "大光銀行")
            .put("0533", "長野銀行")
            .put("0534", "富山第一銀行")
            .put("0537", "福邦銀行")
            .put("0538", "静岡中央銀行")
            .put("0542", "愛知銀行")
            .put("0543", "名古屋銀行")
            .put("0544", "中京銀行")
            .put("0546", "第三銀行")
            .put("0555", "大正銀行")
            .put("0562", "みなと銀行")
            .put("0565", "島根銀行")
            .put("0566", "トマト銀行")
            .put("0569", "もみじ銀行")
            .put("0570", "西京銀行")
            .put("0572", "徳島銀行")
            .put("0573", "香川銀行")
            .put("0576", "愛媛銀行")
            .put("0578", "高知銀行")
            .put("0582", "福岡中央銀行")
            .put("0583", "佐賀共栄銀行")
            .put("0585", "長崎銀行")
            .put("0587", "熊本銀行")
            .put("0590", "豊和銀行")
            .put("0591", "宮崎太陽銀行")
            .put("0594", "南日本銀行")
            .put("0596", "沖縄海邦銀行")
            // }}}
            // {{{ more foreign banks (0600 ~ 0999)
            .put("0603", "韓国産業")
            .put("0607", "彰化商業")
            .put("0608", "ウエルズフアゴ")
            .put("0611", "第一商業")
            .put("0612", "台湾")
            .put("0615", "交通")
            .put("0616", "メトロポリタン")
            .put("0617", "フイリピン")
            .put("0619", "中国工商")
            .put("0621", "中國信託商業")
            .put("0623", "インテーザ")
            .put("0624", "國民")
            .put("0625", "中国建設")
            .put("0626", "イタウウニ")
            .put("0627", "ＢＢＶＡ")
            .put("0630", "中国農業")
            .put("0631", "台新")
            .put("0632", "玉山")
            .put("0633", "台湾企銀")
            .put("0808", "ドイツ証券")
            .put("0813", "ソシエテ証券")
            .put("0821", "ビーピー証券")
            .put("0822", "バークレイ証券")
            .put("0831", "アグリコル証券")
            .put("0832", "ジエイピー証券")
            .put("0842", "ゴルドマン証券")
            .put("0845", "ナツトウエ証券")
            .put("0900", "日本相互証券")
            .put("0905", "東京金融取引所")
            .put("0909", "日本クリア機構")
            .put("0910", "ほふりクリア")
            .put("0964", "しんきん証券")
            .put("0966", "ＨＳＢＣ証券")
            .put("0968", "セント東短証券")
            .put("0971", "ＵＢＳ証券")
            .put("0972", "メリル日本証券")
            // }}}
            .build();

    // minor ~280 lesser known banks
    private static final Map<String, String> minorBanksJapanese = ImmutableMap.<String, String>builder()
            // {{{  信用金庫 (1001 ~ 1996)
            .put("1000", "信金中央金庫")
            .put("1001", "北海道信金")
            .put("1003", "室蘭信金")
            .put("1004", "空知信金")
            .put("1006", "苫小牧信金")
            .put("1008", "北門信金")
            .put("1009", "伊達信金")
            .put("1010", "北空知信金")
            .put("1011", "日高信金")
            .put("1013", "渡島信金")
            .put("1014", "道南うみ街信金")
            .put("1020", "旭川信金")
            .put("1021", "稚内信金")
            .put("1022", "留萌信金")
            .put("1024", "北星信金")
            .put("1026", "帯広信金")
            .put("1027", "釧路信金")
            .put("1028", "大地みらい信金")
            .put("1030", "北見信金")
            .put("1031", "網走信金")
            .put("1033", "遠軽信金")
            .put("1104", "東奥信金")
            .put("1105", "青い森信金")
            .put("1120", "秋田信金")
            .put("1123", "羽後信金")
            .put("1140", "山形信金")
            .put("1141", "米沢信金")
            .put("1142", "鶴岡信金")
            .put("1143", "新庄信金")
            .put("1150", "盛岡信金")
            .put("1152", "宮古信金")
            .put("1153", "一関信金")
            .put("1154", "北上信金")
            .put("1155", "花巻信金")
            .put("1156", "水沢信金")
            .put("1170", "杜の都信金")
            .put("1171", "宮城第一信金")
            .put("1172", "石巻信金")
            .put("1174", "仙南信金")
            .put("1181", "会津信金")
            .put("1182", "郡山信金")
            .put("1184", "白河信金")
            .put("1185", "須賀川信金")
            .put("1186", "ひまわり信金")
            .put("1188", "あぶくま信金")
            .put("1189", "二本松信金")
            .put("1190", "福島信金")
            .put("1203", "高崎信金")
            .put("1204", "桐生信金")
            .put("1206", "アイオー信金")
            .put("1208", "利根郡信金")
            .put("1209", "館林信金")
            .put("1210", "北群馬信金")
            .put("1211", "しののめ信金")
            .put("1221", "足利小山信金")
            .put("1222", "栃木信金")
            .put("1223", "鹿沼相互信金")
            .put("1224", "佐野信金")
            .put("1225", "大田原信金")
            .put("1227", "烏山信金")
            .put("1240", "水戸信金")
            .put("1242", "結城信金")
            .put("1250", "埼玉県信金")
            .put("1251", "川口信金")
            .put("1252", "青木信金")
            .put("1253", "飯能信金")
            .put("1260", "千葉信金")
            .put("1261", "銚子信金")
            .put("1262", "東京ベイ信金")
            .put("1264", "館山信金")
            .put("1267", "佐原信金")
            .put("1280", "横浜信金")
            .put("1281", "かながわ信金")
            .put("1282", "湘南信金")
            .put("1283", "川崎信金")
            .put("1286", "平塚信金")
            .put("1288", "さがみ信金")
            .put("1289", "中栄信金")
            .put("1290", "中南信金")
            .put("1303", "朝日信金")
            .put("1305", "興産信金")
            .put("1310", "さわやか信金")
            .put("1311", "東京シテイ信金")
            .put("1319", "芝信金")
            .put("1320", "東京東信金")
            .put("1321", "東栄信金")
            .put("1323", "亀有信金")
            .put("1326", "小松川信金")
            .put("1327", "足立成和信金")
            .put("1333", "東京三協信金")
            .put("1336", "西京信金")
            .put("1341", "西武信金")
            .put("1344", "城南信金")
            .put("1345", "東京）昭和信金")
            .put("1346", "目黒信金")
            .put("1348", "世田谷信金")
            .put("1349", "東京信金")
            .put("1351", "城北信金")
            .put("1352", "滝野川信金")
            .put("1356", "巣鴨信金")
            .put("1358", "青梅信金")
            .put("1360", "多摩信金")
            .put("1370", "新潟信金")
            .put("1371", "長岡信金")
            .put("1373", "三条信金")
            .put("1374", "新発田信金")
            .put("1375", "柏崎信金")
            .put("1376", "上越信金")
            .put("1377", "新井信金")
            .put("1379", "村上信金")
            .put("1380", "加茂信金")
            .put("1385", "甲府信金")
            .put("1386", "山梨信金")
            .put("1390", "長野信金")
            .put("1391", "松本信金")
            .put("1392", "上田信金")
            .put("1393", "諏訪信金")
            .put("1394", "飯田信金")
            .put("1396", "アルプス信金")
            .put("1401", "富山信金")
            .put("1402", "高岡信金")
            .put("1405", "にいかわ信金")
            .put("1406", "氷見伏木信金")
            .put("1412", "砺波信金")
            .put("1413", "石動信金")
            .put("1440", "金沢信金")
            .put("1442", "のと共栄信金")
            .put("1444", "北陸信金")
            .put("1445", "鶴来信金")
            .put("1448", "興能信金")
            .put("1470", "福井信金")
            .put("1471", "敦賀信金")
            .put("1473", "小浜信金")
            .put("1475", "越前信金")
            .put("1501", "しず焼津信金")
            .put("1502", "静清信金")
            .put("1503", "浜松磐田信金")
            .put("1505", "沼津信金")
            .put("1506", "三島信金")
            .put("1507", "富士宮信金")
            .put("1513", "島田掛川信金")
            .put("1515", "静岡）富士信金")
            .put("1517", "遠州信金")
            .put("1530", "岐阜信金")
            .put("1531", "大垣西濃信金")
            .put("1532", "高山信金")
            .put("1533", "東濃信金")
            .put("1534", "関信金")
            .put("1538", "八幡信金")
            .put("1550", "愛知信金")
            .put("1551", "豊橋信金")
            .put("1552", "岡崎信金")
            .put("1553", "いちい信金")
            .put("1554", "瀬戸信金")
            .put("1555", "半田信金")
            .put("1556", "知多信金")
            .put("1557", "豊川信金")
            .put("1559", "豊田信金")
            .put("1560", "碧海信金")
            .put("1561", "西尾信金")
            .put("1562", "蒲郡信金")
            .put("1563", "尾西信金")
            .put("1565", "中日信金")
            .put("1566", "東春信金")
            .put("1580", "津信金")
            .put("1581", "北伊勢上野信金")
            .put("1583", "桑名三重信金")
            .put("1585", "紀北信金")
            .put("1602", "滋賀中央信金")
            .put("1603", "長浜信金")
            .put("1604", "湖東信金")
            .put("1610", "京都信金")
            .put("1611", "京都中央信金")
            .put("1620", "京都北都信金")
            .put("1630", "大阪信金")
            .put("1633", "大阪厚生信金")
            .put("1635", "大阪シテイ信金")
            .put("1636", "大阪商工信金")
            .put("1643", "永和信金")
            .put("1645", "北おおさか信金")
            .put("1656", "枚方信金")
            .put("1666", "奈良信金")
            .put("1667", "大和信金")
            .put("1668", "奈良中央信金")
            .put("1671", "新宮信金")
            .put("1674", "きのくに信金")
            .put("1680", "神戸信金")
            .put("1685", "姫路信金")
            .put("1686", "播州信金")
            .put("1687", "兵庫信金")
            .put("1688", "尼崎信金")
            .put("1689", "日新信金")
            .put("1691", "淡路信金")
            .put("1692", "但馬信金")
            .put("1694", "西兵庫信金")
            .put("1695", "中兵庫信金")
            .put("1696", "但陽信金")
            .put("1701", "鳥取信金")
            .put("1702", "米子信金")
            .put("1703", "倉吉信金")
            .put("1710", "しまね信金")
            .put("1711", "日本海信金")
            .put("1712", "島根中央信金")
            .put("1732", "おかやま信金")
            .put("1734", "水島信金")
            .put("1735", "津山信金")
            .put("1738", "玉島信金")
            .put("1740", "備北信金")
            .put("1741", "吉備信金")
            .put("1742", "日生信金")
            .put("1743", "備前信金")
            .put("1750", "広島信金")
            .put("1752", "呉信金")
            .put("1756", "しまなみ信金")
            .put("1758", "広島みどり信金")
            .put("1780", "萩山口信金")
            .put("1781", "西中国信金")
            .put("1789", "東山口信金")
            .put("1801", "徳島信金")
            .put("1803", "阿南信金")
            .put("1830", "高松信金")
            .put("1833", "観音寺信金")
            .put("1860", "愛媛信金")
            .put("1862", "宇和島信金")
            .put("1864", "東予信金")
            .put("1866", "川之江信金")
            .put("1880", "幡多信金")
            .put("1881", "高知信金")
            .put("1901", "福岡信金")
            .put("1903", "福岡ひびき信金")
            .put("1908", "大牟田柳川信金")
            .put("1909", "筑後信金")
            .put("1910", "飯塚信金")
            .put("1917", "大川信金")
            .put("1920", "遠賀信金")
            .put("1930", "唐津信金")
            .put("1931", "佐賀信金")
            .put("1933", "九州ひぜん信金")
            .put("1942", "たちばな信金")
            .put("1951", "熊本信金")
            .put("1952", "熊本第一信金")
            .put("1954", "熊本中央信金")
            .put("1960", "大分信金")
            .put("1962", "大分みらい信金")
            .put("1980", "宮崎都城信金")
            .put("1985", "高鍋信金")
            .put("1990", "鹿児島信金")
            .put("1991", "鹿児島相互信金")
            .put("1993", "奄美大島信金")
            .put("1996", "コザ信金")
            // }}}
            // {{{  信用組合 (2011 ~ 2895)
            .put("2004", "商工組合中央金庫")
            .put("2010", "全国信用協同組合連合会")
            .put("2213", "整理回収機構")
            // }}}
            // {{{  労働金庫 (2951 ~ 2997)
            .put("2950", "労働金庫連合会")
            // }}}
            // {{{  農林中央金庫 (3000)
            .put("3000", "農林中央金庫")
            // }}}
            // {{{  信用農業協同組合連合会 (3001 ~ 3046)
            .put("3001", "北海道信用農業協同組合連合会")
            .put("3003", "岩手県信用農業協同組合連合会")
            .put("3008", "茨城県信用農業協同組合連合会")
            .put("3011", "埼玉県信用農業協同組合連合会")
            .put("3013", "東京都信用農業協同組合連合会")
            .put("3014", "神奈川県信用農業協同組合連合会")
            .put("3015", "山梨県信用農業協同組合連合会")
            .put("3016", "長野県信用農業協同組合連合会")
            .put("3017", "新潟県信用農業協同組合連合会")
            .put("3019", "石川県信用農業協同組合連合会")
            .put("3020", "岐阜県信用農業協同組合連合会")
            .put("3021", "静岡県信用農業協同組合連合会")
            .put("3022", "愛知県信用農業協同組合連合会")
            .put("3023", "三重県信用農業協同組合連合会")
            .put("3024", "福井県信用農業協同組合連合会")
            .put("3025", "滋賀県信用農業協同組合連合会")
            .put("3026", "京都府信用農業協同組合連合会")
            .put("3027", "大阪府信用農業協同組合連合会")
            .put("3028", "兵庫県信用農業協同組合連合会")
            .put("3030", "和歌山県信用農業協同組合連合会")
            .put("3031", "鳥取県信用農業協同組合連合会")
            .put("3034", "広島県信用農業協同組合連合会")
            .put("3035", "山口県信用農業協同組合連合会")
            .put("3036", "徳島県信用農業協同組合連合会")
            .put("3037", "香川県信用農業協同組合連合会")
            .put("3038", "愛媛県信用農業協同組合連合会")
            .put("3039", "高知県信用農業協同組合連合会")
            .put("3040", "福岡県信用農業協同組合連合会")
            .put("3041", "佐賀県信用農業協同組合連合会")
            .put("3044", "大分県信用農業協同組合連合会")
            .put("3045", "宮崎県信用農業協同組合連合会")
            .put("3046", "鹿児島県信用農業協同組合連合会")
            // }}}
            // {{{ "JA Bank" agricultural cooperative associations (3056 ~ 9375)
            // REMOVED: the farmers should use a real bank if they want to sell bitcoin
            // }}}
            // {{{  信用漁業協同組合連合会 (9450 ~ 9496)
            .put("9450", "北海道信用漁業協同組合連合会")
            .put("9451", "青森県信用漁業協同組合連合会")
            .put("9452", "岩手県信用漁業協同組合連合会")
            .put("9453", "宮城県漁業協同組合")
            .put("9456", "福島県信用漁業協同組合連合会")
            .put("9457", "茨城県信用漁業協同組合連合会")
            .put("9461", "千葉県信用漁業協同組合連合会")
            .put("9462", "東京都信用漁業協同組合連合会")
            .put("9466", "新潟県信用漁業協同組合連合会")
            .put("9467", "富山県信用漁業協同組合連合会")
            .put("9468", "石川県信用漁業協同組合連合会")
            .put("9470", "静岡県信用漁業協同組合連合会")
            .put("9471", "愛知県信用漁業協同組合連合会")
            .put("9472", "三重県信用漁業協同組合連合会")
            .put("9473", "福井県信用漁業協同組合連合会")
            .put("9475", "京都府信用漁業協同組合連合会")
            .put("9477", "なぎさ信用漁業協同組合連合会")
            .put("9480", "鳥取県信用漁業協同組合連合会")
            .put("9481", "ＪＦしまね漁業協同組合")
            .put("9483", "広島県信用漁業協同組合連合会")
            .put("9484", "山口県漁業協同組合")
            .put("9485", "徳島県信用漁業協同組合連合会")
            .put("9486", "香川県信用漁業協同組合連合会")
            .put("9487", "愛媛県信用漁業協同組合連合会")
            .put("9488", "高知県信用漁業協同組合連合会")
            .put("9489", "福岡県信用漁業協同組合連合会")
            .put("9490", "佐賀県信用漁業協同組合連合会")
            .put("9491", "長崎県信用漁業協同組合連合会")
            .put("9493", "大分県漁業協同組合")
            .put("9494", "宮崎県信用漁業協同組合連合会")
            .put("9495", "鹿児島県信用漁業協同組合連合会")
            .put("9496", "沖縄県信用漁業協同組合連合会")
            // }}}
            // {{{ securities firms
            .put("9500", "東京短資")
            .put("9501", "セントラル短資")
            .put("9507", "上田八木短資")
            .put("9510", "日本証券金融")
            .put("9520", "野村証券")
            .put("9521", "日興証券")
            .put("9523", "大和証券")
            .put("9524", "みずほ証券")
            .put("9528", "岡三証券")
            .put("9530", "岩井コスモ証券")
            .put("9532", "三菱ＵＦＪ証券")
            .put("9534", "丸三証券")
            .put("9535", "東洋証券")
            .put("9537", "水戸証券")
            .put("9539", "東海東京証券")
            .put("9542", "むさし証券")
            .put("9545", "いちよし証券")
            .put("9573", "極東証券")
            .put("9574", "立花証券")
            .put("9579", "光世証券")
            .put("9584", "ちばぎん証券")
            .put("9589", "シテイ証券")
            .put("9594", "ＣＳ証券")
            .put("9595", "スタンレー証券")
            .put("9930", "日本政策投資")
            .put("9932", "政策金融公庫")
            .put("9933", "国際協力")
            .put("9945", "預金保険機構")
            // }}}
            .build();

    private final static String ID_OPEN = "";
    private final static String ID_CLOSE = "";
    private final static String JA_OPEN = "";
    private final static String JA_CLOSE = "";
    private final static String EN_OPEN = "";
    private final static String EN_CLOSE = "";
    public final static String SPACE = " ";

    // don't localize these strings into all languages,
    // all we want is either Japanese or English here.
    public static String getString(String id) {
        boolean ja = GUIUtil.getUserLanguage().equals("ja");

        switch (id) {
            case "bank":
                if (ja) return "銀行名 ・金融機関名";
                return "Bank or Financial Institution";
            case "bank.select":
                if (ja) return "金融機関 ・銀行検索 (名称入力検索)";
                return "Search for Bank or Financial Institution";
            case "bank.code":
                if (ja) return "銀行コード";
                return "Zengin Bank Code";
            case "bank.name":
                if (ja) return "金融機関名 ・銀行名";
                return "Financial Institution / Bank Name";

            case "branch":
                if (ja) return "支店名";
                return "Bank Branch";
            case "branch.code":
                if (ja) return "支店コード";
                return "Zengin Branch Code";
            case "branch.code.validation.error":
                if (ja) return "入力は3桁の支店コードでなければなりません";
                return "Input must be a 3 digit branch code";
            case "branch.name":
                if (ja) return "支店名";
                return "Bank Branch Name";

            case "account":
                if (ja) return "銀行口座";
                return "Bank Account";

            case "account.type":
                if (ja) return "口座科目";
                return "Bank Account Type";
            case "account.type.select":
                if (ja) return "口座科目";
                return "Select Account Type";
            // displayed while creating account
            case "account.type.futsu":
                if (ja) return "普通";
                return "FUTSUU (ordinary) account";
            case "account.type.touza":
                if (ja) return "当座";
                return "TOUZA (checking) account";
            case "account.type.chochiku":
                if (ja) return "貯金";
                return "CHOCHIKU (special) account";
            // used when saving account info
            case "account.type.futsu.ja":
                return "普通";
            case "account.type.touza.ja":
                return "当座";
            case "account.type.chochiku.ja":
                return "貯金";

            case "account.number":
                if (ja) return "口座番号";
                return "Bank Account Number";
            case "account.number.validation.error":
                if (ja) return "入力は4〜8桁の口座番号でなければなりません";
                return "Input must be 4 ~ 8 digit account number";
            case "account.name":
                if (ja) return "口座名義";
                return "Bank Account Name";

            // for japanese-only input fields
            case "japanese.validation.error":
                if (ja) return "入力は漢字、ひらがな、またはカタカナでなければなりません";
                return "Input must be Kanji, Hiragana, or Katakana";
            case "japanese.validation.regex":
                // epic regex to only match Japanese input
                return "[" + // match any of these characters:
                        // "Ａ-ｚ" + // full-width alphabet
                        // "０-９" + // full-width numerals
                        "一-龯" + // common Japanese kanji (0x4e00 ~ 0x9faf)
                        "々" + // kanji iteration mark (0x3005)
                        "〇" + // kanji number zero (0x3007)
                        "ぁ-ゞ" + // hiragana (0x3041 ~ 0x309e)
                        "ァ-・" + // full-width katakana (0x30a1 ~ 0x30fb)
                        "ｧ-ﾝﾞﾟ" + // half-width katakana
                        "ヽヾ゛゜ー" + // 0x30fd, 0x30fe, 0x309b, 0x309c, 0x30fc
                        "　" + // full-width space
                        " " + // half-width space
                        "]+"; // for any length
        }

        return "null";
    }
}
