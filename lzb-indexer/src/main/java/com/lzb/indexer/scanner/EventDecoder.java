package com.lzb.indexer.scanner;

import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.entity.GmxPositionHistory;
import com.lzb.indexer.domain.entity.SwapEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Decodes on-chain event logs into domain entities:
 * - ERC20 Transfer
 * - Uniswap V2 Swap
 * - GMX V2 position events (via the custom EventEmitter ABI)
 *
 * GMX V2 emits all position events through the EventEmitter contract using
 * emitEventLog / emitEventLog2. The actual event name is identified by the
 * eventNameHash in topic[1], and the payload is a custom ABI-encoded
 * EventUtils.EventLogData struct, which must be parsed manually.
 */
@Component
public class EventDecoder implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EventDecoder.class);
    /** 策略路由：key=protocol，value=对应 EventHandler */
    private final Map<String, EventHandler> handlerMap;

    /** Spring 注入所有 EventHandler Bean，加上自身组成路由表 */
    public EventDecoder(List<EventHandler> handlers) {
        this.handlerMap = new java.util.HashMap<>();
        for (EventHandler h : handlers) {
            handlerMap.put(h.getProtocol(), h);
        }
        handlerMap.put("GMX_VAULT", this);
    }

    /** 按协议名获取对应的事件处理器 */
    public EventHandler getHandler(String protocol) {
        EventHandler h = handlerMap.get(protocol);
        if (h == null) {
            log.warn("No EventHandler for protocol: {}", protocol);
        }
        return h;
    }

    // ============ EventHandler 接口方法 (GMX 代理) ============

    @Override
    public String getProtocol() { return "GMX_VAULT"; }

    @Override
    public java.util.List<String> getEventHashes() {
        return java.util.Arrays.asList(EMIT_EVENT_LOG_HASH, EMIT_EVENT_LOG1_HASH, EMIT_EVENT_LOG2_HASH);
    }
// ======================== ERC20 Transfer ========================

    private static final Event TRANSFER_EVENT = new Event(
            "Transfer",
            Arrays.asList(
                    new TypeReference<Address>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>(false) {}
            ));
    private static final String TRANSFER_EVENT_HASH = EventEncoder.encode(TRANSFER_EVENT);

    /** 闂備礁鍚嬮崕鎶藉床閼艰翰浜?Transfer 濠电偛鐡ㄧ划宀勵敄閸曨偀鏋庨柕蹇嬪灮妞规娊鏌熼鍡楀閳ь剚濞婇弻娑樷攽閸℃瑥顣虹紓浣诡殔濞差參寮澶婇唶婵犲﹤鎳撶换?BlockScanner 闂佽崵濮崇粈浣规櫠娴犲鍋?eth filter闂?*/
    public static String getTransferEventHash() {
        return TRANSFER_EVENT_HASH;
    }

    public boolean isTransferEvent(Log logEntry) {
        return logEntry.getTopics() != null
                && logEntry.getTopics().size() == 3
                && TRANSFER_EVENT_HASH.equals(logEntry.getTopics().get(0));
    }

    public TokenTransfer decode(Log logEntry, String chainName) {
        if (logEntry.getTopics() == null || logEntry.getTopics().size() < 3
                || !TRANSFER_EVENT_HASH.equals(logEntry.getTopics().get(0))) {
            return null;
        }
        try {
            String from = (String) FunctionReturnDecoder.decodeIndexedValue(
                    logEntry.getTopics().get(1),
                    new TypeReference<Address>() {}).getValue();
            String to = (String) FunctionReturnDecoder.decodeIndexedValue(
                    logEntry.getTopics().get(2),
                    new TypeReference<Address>() {}).getValue();

            @SuppressWarnings("unchecked")
            List<Type> decoded = FunctionReturnDecoder.decode(
                    logEntry.getData(),
                    (List<TypeReference<Type>>)(List<?>) Collections.singletonList(
                            new TypeReference<Uint256>() {}));
            BigInteger value = (BigInteger) decoded.get(0).getValue();

            return new TokenTransfer(
                    logEntry.getTransactionHash(),
                    logEntry.getBlockNumber().longValue(),
                    logEntry.getLogIndex().intValue(),
                    from.toLowerCase(),
                    to.toLowerCase(),
                    value,
                    chainName);
        } catch (Exception e) {
            log.warn("Failed to decode Transfer event: tx={}", logEntry.getTransactionHash(), e);
            return null;
        }
    }

    // ======================== GMX V2 Event Decoding ========================

    /** EventLog (0x7e3b...) / EventLog1 (0x137a...) topic0 hashes, verified against Arbitrum mainnet. */
    private static final String EMIT_EVENT_LOG_HASH   = "0x7e3bde2ba7aca4a8499608ca57f3b0c1c1c93ace63ffd3741a9fab204146fc9a";
    private static final String EMIT_EVENT_LOG1_HASH  = "0x137a44067c8961cd7e1d876f4754a5a3a75989b4552f1843fc69c3b372def160";
    /** EventLog2 topic0: eventNameHash + topic1 + topic2 are indexed. */
    private static final String EMIT_EVENT_LOG2_HASH = "0x468a25a7ba624ceea6e540ad6f49171b52495b648417ae91bca21676d8a24dc5";

    /** keccak256 of the GMX event name string, matched against topic[1]. */
    /** keccak256("PositionIncrease") */
    private static final String POSITION_INCREASE_HASH = "0xf94196ccb31f81a3e67df18f2a62cbfb50009c80a7d3c728a3f542e3abc5cb63";
    /** keccak256("PositionDecrease") */
    private static final String POSITION_DECREASE_HASH = "0x07d51b51b408d7c62dcc47cc558da5ce6a6e0fd129a427ebce150f52b0e5171a";

    public boolean isGmxV2Event(Log logEntry) {
        return logEntry.getTopics() != null && logEntry.getTopics().size() >= 2
                && (EMIT_EVENT_LOG_HASH.equals(logEntry.getTopics().get(0))
                 || EMIT_EVENT_LOG1_HASH.equals(logEntry.getTopics().get(0))
                 || EMIT_EVENT_LOG2_HASH.equals(logEntry.getTopics().get(0)));
    }

    public boolean isIncreasePositionEvent(Log logEntry) {
        return isGmxV2Event(logEntry)
                && POSITION_INCREASE_HASH.equals(logEntry.getTopics().get(1));
    }

    public boolean isDecreasePositionEvent(Log logEntry) {
        return isGmxV2Event(logEntry)
                && POSITION_DECREASE_HASH.equals(logEntry.getTopics().get(1));
    }

    /** 婵犵數鍋為幐鎼佸箠閹扮増鍋╅悹鍥у棘濞戙垹鐒垫い鎺嗗亾闂囧鎮楅敐鍛暢缂佹劖鈥攅creasePosition 濠电偛鐡ㄧ划宀勵敄閸曨偀鏋庨柕蹇ョ磿閳?isLiquidation flag 濠?true 闂備礁鎼崯鍐测枖濞戙垹鍨傞柣锝呭閸嬫挾鎲撮崟顓犲彎闁荤姵鍔楅崰搴ㄥΥ閹烘宸濇い鏍ㄧ矋濮?*/
    public boolean isLiquidatePositionEvent(Log logEntry) {
        return false;
    }

    /** 闂佽崵鍠愰悷杈╁緤閸ф鍋夋繝濠傚暔閳ь剚甯″畷銊╊敍濠婂嫭鐦撳┑鐐茬摠缁矂顢栭崟顐熸瀻闁靛繈鍊栭弲顒勬煕椤愩倕鏋戦柣锝庡灦閺岋繝宕奸锛勭泿闂佹眹鍊曠€氫即骞冨畷鍥ㄦ殰妞ゆ柨澧介ˇ顕€鏌℃径鍡樻珕闁哄被鍔岀叅?null闂?*/
    public GmxPositionHistory decodeLiquidatePosition(Log logEntry, String chainName) {
        log.debug("LiquidatePosition event detected but not yet supported, tx={}", logEntry.getTransactionHash());
        return null;
    }

    // ======================== GMX V2 Position Decoding ========================

    public GmxPositionHistory decodeIncreasePosition(Log logEntry, String chainName) {
        if (!isIncreasePositionEvent(logEntry)) return null;
        return decodePosition(logEntry, "INCREASE", chainName, false);
    }

    public GmxPositionHistory decodeDecreasePosition(Log logEntry, String chainName) {
        if (!isDecreasePositionEvent(logEntry)) return null;
        return decodePosition(logEntry, "DECREASE", chainName, true);
    }

    /**
     * Decode a position event into a history record using the custom EventLogData layout.
     *
     * @param negate true to negate sizeDelta/collateralDelta (DECREASE events).
     */
    private GmxPositionHistory decodePosition(Log logEntry, String eventType, String chainName,
                                               boolean negate) {
        try {
            Map<String, String> addr = new LinkedHashMap<>();
            Map<String, BigInteger> uints = new LinkedHashMap<>();
            Map<String, Boolean> bools = new LinkedHashMap<>();
            Map<String, String> b32s = new LinkedHashMap<>();
            parseEventLogData(hex(logEntry.getData()), addr, uints, bools, b32s);

            // 闂佽崵濮甸崝褔姊介崟顖氭槬婵炴垯鍨归幑鍫曟煛婢跺顕滅紒鎻掝煼閺屻劌鈽夊▎鎴犲彎缂備線纭搁崣鍐ㄧ暦濡ゅ懎閱囨繝濠傛噽閻?data 闂佽崵鍠愰悷杈╁緤妤ｅ啯鍊靛ù鐘差儐閺咁剟鎮橀悙宸綗濞存粌銈搁幃褰掑箛閳轰礁濮庨梺鍓茬厛閸ㄥ爼骞?topic[2] 闂備胶顭堢换鎴犲垝瀹€鈧懞?
            String account = getAddr(addr, "account");
            if (account.isEmpty() && logEntry.getTopics().size() > 2) {
                String t2 = logEntry.getTopics().get(2);
                if (t2 != null && t2.length() >= 42) {
                    account = t2.substring(t2.length() - 40);
                }
            }
            // 闂備胶顢婂▔娑㈡倶濮橆厾绠鹃柛灞剧〒椤╃兘鏌ㄥ┑鍡樺櫧妞ゅ繑鐓￠弻銊モ槈濞嗘垹鍙濈紓浣诡殔椤﹂潧鐣烽妷銉ф殕闁逞屽墯閺呰泛螖閸涱厼鐝樺銈嗗坊閸嬫挾鎲搁悧鍫㈠弨闁硅櫕顨婇幃鍓т沪閻愵剚顓瑰┑鐐村灦閹稿摜绮旈幘顔肩畺?key 闂?
            String collateralToken = getAddr(addr, "collateralToken");
            if (collateralToken.isEmpty()) collateralToken = getAddr(addr, "initialCollateralToken");
            // 闂備礁婀遍…鍫澝洪敃鍌氭辈闁绘柨鎽滈々鐑芥煥濠靛棙鍣芥い?market闂備焦瀵х粙鎺楁儗椤旂偓顐界€瑰嫭澹嬮弸搴ㄦ煃閵夈儳锛嶆慨锝忕畵閹綊宕堕妸锔绢槬濡炪倧绠掑▔鏇犲垝閺傛鍚嬮柛娑卞弨椤斿姊洪悡搴ｏ紞闁哄拋鍋勯埢?key 闂?
            String market = getAddr(addr, "indexToken");
            if (market.isEmpty()) market = getAddr(addr, "market");
            if (market.isEmpty()) market = getAddr(addr, "longToken");
            if (market.isEmpty()) market = getAddr(addr, "shortToken");
            String positionKey = b32s.getOrDefault("orderKey", "0x");
            BigInteger sizeInUsd = uints.getOrDefault("sizeInUsd", BigInteger.ZERO);
            BigInteger collateralAmount = uints.getOrDefault("collateralAmount",
                    uints.getOrDefault("initialCollateralDeltaAmount", BigInteger.ZERO));
            BigInteger price = uints.getOrDefault("executionPrice",
                    uints.getOrDefault("price", BigInteger.ZERO));
            BigInteger fee = uints.getOrDefault("positionFeeAmount", BigInteger.ZERO);
            boolean isLong = bools.getOrDefault("isLong", false);

            // DECREASE + isLiquidation flag => LIQUIDATE event type
            boolean isLiquidation = bools.getOrDefault("isLiquidation", false);
            String resolvedEventType = eventType;
            if ("DECREASE".equals(eventType) && isLiquidation) {
                resolvedEventType = "LIQUIDATE";
            }

            if (log.isDebugEnabled()) {
                log.debug("Position decode: addr={} uints={} bools={} b32s={} liquidation={}",
                        addr.keySet(), uints.keySet(), bools.keySet(), b32s.keySet(), isLiquidation);
            }

            return new GmxPositionHistory(
                    resolvedEventType, logEntry.getTransactionHash(),
                    logEntry.getBlockNumber().longValue(),
                    logEntry.getLogIndex().intValue(),
                    positionKey,
                    account.toLowerCase(),
                    collateralToken.toLowerCase(),
                    market.toLowerCase(),
                    negate ? collateralAmount.negate() : collateralAmount,
                    negate ? sizeInUsd.negate() : sizeInUsd,
                    isLong, price, fee, chainName);
        } catch (Exception e) {
            log.warn("Failed to decode V2 {}: tx={}", eventType,
                    logEntry.getTransactionHash(), e);
            return null;
        }
    }

    // ======================== EventLogData 闂佽崵鍠愰悷杈╁緤妤ｅ啯鍊?========================

    /**
     * 闂佽崵鍠愰悷杈╁緤妤ｅ啯鍊?emitEventLog/emitEventLog2 闂?data 闂佽瀛╃粙鎺椼€冮崱娑辨晩?
     *
     * emitEventLog:  data = msgSender(32B) + eventName(闂備礁鎲￠弻锝夊礉瀹ュ鐒? + EventLogData(闂備礁鎲￠弻锝夊礉瀹ュ鐒?
     *   EventLogData 闂佽崵濮嶉崘銊π╁銈庡亝閸旀牜绮?hex 闂備胶顭堥鍛崲閹哄秶鏄?128
     *
     * emitEventLog2: data = msgSender(32B) + EventLogData(闂備礁鎲￠弻锝夊礉瀹ュ鐒?
     *   eventName 闂?topic[2], EventLogData 闂佽崵濮嶉崘銊π╁銈庡亝閸旀牜绮?hex 闂備胶顭堥鍛崲閹哄秶鏄?64
     */
    private static void parseEventLogData(String hex,
            Map<String, String> addr, Map<String, BigInteger> uints,
            Map<String, Boolean> bools, Map<String, String> b32s) {
        if (hex == null || hex.length() < 256) return;

        // EventLogData = { addrItems, uintItems, intItems, boolItems, bytes32Items, bytesItems, stringItems }
        // 婵犳鍣徊鐣屾崲濮椻偓婵?64 闂佽瀛╃粙鎺椼€冩径瀣╃箚? 闂備胶顭堥鍛崲閹哄秶鏄傞梻?32B) + 闂傚倸鍊甸崑鎾绘煕椤垵鏋涙い?32B), 闂備浇妗ㄩ懗鑸垫櫠濡も偓閻ｅ灚绗熼埀顒€鐣烽悜钘壩╅柕澶涚畱閳ь剛鏁诲?
        int edOffChar = bytesToBigInt(hex, 128).intValue() * 2;
        if (edOffChar < 64 || hex.length() < edOffChar + 448) return;

        int[] relOff = new int[7];
        for (int i = 0; i < 7; i++) {
            long v = bytesToBigInt(hex, edOffChar + i * 64).longValue();
            if (v < 0 || v > 1000000) return;
            relOff[i] = (int) v;
        }

        if (log.isDebugEnabled()) {
            log.debug("EventLogData edOffChar={} relOff={}", edOffChar, Arrays.toString(relOff));
        }

        // relOff indices: addr[0] uint[1] int[2] bool[3] bytes32[4]
        parseAddrKV(hex, edOffChar + relOff[0] * 2, addr);
        parseUintKV(hex, edOffChar + relOff[1] * 2, uints);
        parseBoolKV(hex, edOffChar + relOff[3] * 2, bools);
        parseKV32(hex, edOffChar + relOff[4] * 2, b32s);
    }

    // ---- AddressItems ----
    private static void parseAddrKV(String hex, int structCharOff, Map<String, String> result) {
        if (structCharOff <= 0 || hex.length() < structCharOff + 64) return;
        int arrOff = bytesToBigInt(hex, structCharOff).intValue();
        int arrStart = structCharOff + arrOff * 2;
        if (arrStart < structCharOff || hex.length() < arrStart + 64) return;
        int n = safeInt(bytesToBigInt(hex, arrStart));
        if (n <= 0 || n > 1000) return;
        int cursor = arrStart + 64;
        for (int i = 0; i < n; i++) {
            if (hex.length() < cursor + 64) break;
            int itemOff = bytesToBigInt(hex, cursor).intValue();
            int itemStart = arrStart + itemOff * 2;
            if (hex.length() < itemStart + 128) break;
            // key 闂備礁鎲￠悷顖炲垂閻㈢绀傛慨妞诲亾鐎?offset 闂佽崵濮撮幖顐︽偪閸ヮ灐褰掑幢濞戞瑦娅栭梺鍓插亖閸庢彃袙閹扮増鍊垫繛鎴炵懐濞堟ɑ銇勯幋婊呭妽缂佸顦遍幏鐘侯槾缂佲偓閸℃稒鐓ユ繛鎴灻〃娆戠磼閳ュ啿鏆ｇ€规洩缍侀、娑樷攽閸℃绠ｉ梻浣告惈鐎氱兘宕归崘瑁佹椽宕稿Δ鈧粻鎶芥煏婢跺牆鍔氶柡浣哥埣閺屻倖娼忛妸锔绘缂備浇椴搁悷鈺呭箚瀹€鍕垫晣闁绘劕鐏氶幉鑽ょ磽娴ｈ娈ｇ紓鍌涙皑閹蹭即宕卞Ο鍦畾?"market"闂?
            String key = readItemKey(hex, itemStart);
            // value 闂?itemStart 闂備礁鎲￠懝楣冩煀閿濆拋鐒?32 闂佽瀛╃粙鎺椼€冩径瀣╃箚妞ゆ挾鍠庣欢?
            BigInteger rawVal = bytesToBigInt(hex, itemStart + 64);
            String val;
            if (rawVal.compareTo(BigInteger.valueOf(10000)) < 0 && rawVal.signum() > 0) {
                int valOff = (itemStart / 2 + rawVal.intValue()) * 2;
                if (hex.length() >= valOff + 64) {
                    val = "0x" + hex.substring(valOff + 24, valOff + 64);
                } else {
                    val = "0x" + hex.substring(itemStart + 64 + 24, itemStart + 128);
                }
            } else {
                val = "0x" + hex.substring(itemStart + 64 + 24, itemStart + 128);
            }
            if (key != null && !key.isEmpty()) {
                result.put(key, val);
            }
            cursor += 64;
        }
    }

    // ---- UintItems ----
    private static void parseUintKV(String hex, int structCharOff, Map<String, BigInteger> result) {
        if (structCharOff <= 0 || hex.length() < structCharOff + 64) return;
        int arrOff = bytesToBigInt(hex, structCharOff).intValue();
        int arrStart = structCharOff + arrOff * 2;
        if (arrStart < structCharOff || hex.length() < arrStart + 64) return;
        int n = safeInt(bytesToBigInt(hex, arrStart));
        if (n <= 0 || n > 1000) return;
        int cursor = arrStart + 64;
        for (int i = 0; i < n; i++) {
            if (hex.length() < cursor + 64) break;
            int itemOff = bytesToBigInt(hex, cursor).intValue();
            int itemStart = arrStart + itemOff * 2;
            if (hex.length() < itemStart + 192) break;
            String key = readItemKey(hex, itemStart);
            // 闂傚倷鐒﹂崕瀹犮亹閻愮數绠旈柛宀€鍋涢弸渚€鏌ｅΔ鈧悧鍡欑矈閿曞倹鐓ユ繛鎴炵懐閸斿〖em 闂?4 婵犵鈧啿绾ч柡瀣吹濡叉劕顬婂绁?+ 0x40 闂備礁鎼粔鏉懨洪埡鍜佹晩?+ 闂佽楠稿﹢閬嶅磻閵堝拋鐎舵い鏍仜绾惧湱鐥銏╂缂佲偓閸℃稒鐓ユ繛鎴烆焾鐎氫即鏌ｉ妶鍛伃婵﹣绮欏畷銊╊敇濞戞ü澹曢梺缁樻尭鐎涒晠鐓鍌滅＜?3 婵?
            BigInteger marker = bytesToBigInt(hex, itemStart + 64);
            BigInteger val;
            if (marker.compareTo(BigInteger.valueOf(10000)) < 0 && marker.signum() > 0) {
                val = bytesToBigInt(hex, itemStart + 128);
            } else {
                val = marker;
            }
            if (key != null && !key.isEmpty()) {
                result.put(key, val);
            }
            cursor += 64;
        }
    }

    // ---- BoolItems ----
    private static void parseBoolKV(String hex, int structCharOff, Map<String, Boolean> result) {
        if (structCharOff <= 0 || hex.length() < structCharOff + 64) return;
        int arrOff = bytesToBigInt(hex, structCharOff).intValue();
        int arrStart = structCharOff + arrOff * 2;
        if (arrStart < structCharOff || hex.length() < arrStart + 64) return;
        int n = safeInt(bytesToBigInt(hex, arrStart));
        if (n <= 0 || n > 1000) return;
        int cursor = arrStart + 64;
        for (int i = 0; i < n; i++) {
            if (hex.length() < cursor + 64) break;
            int itemOff = bytesToBigInt(hex, cursor).intValue();
            int itemStart = arrStart + itemOff * 2;
            if (hex.length() < itemStart + 192) break;
            String key = readItemKey(hex, itemStart);
            // 闂傚倷鐒﹂崕瀹犮亹閻愮數绠旈柛宀€鍋涢弸渚€鏌ｅΔ鈧悧鍡欑矈閿曞倹鐓ユ繛鎴炵懐閸斿〖em 闂?4 婵犵鈧啿绾ч柡瀣吹濡叉劕顭冲▽绌檒 闂備胶顭堥敃锕傚储瑜嶉敃銏ゎ敂閸℃瑧鐣?3 婵?
            BigInteger boolMarker = bytesToBigInt(hex, itemStart + 64);
            boolean val;
            if (boolMarker.compareTo(BigInteger.valueOf(10000)) < 0 && boolMarker.signum() > 0) {
                val = !"0000000000000000000000000000000000000000000000000000000000000000"
                        .equals(hex.substring(itemStart + 128, itemStart + 192));
            } else {
                val = !"0000000000000000000000000000000000000000000000000000000000000000"
                        .equals(hex.substring(itemStart + 64, itemStart + 128));
            }
            if (key != null && !key.isEmpty()) {
                result.put(key, val);
            }
            cursor += 64;
        }
    }

    // ---- Bytes32Items ----
    private static void parseKV32(String hex, int structCharOff, Map<String, String> result) {
        if (structCharOff <= 0 || hex.length() < structCharOff + 64) return;
        int arrOff = bytesToBigInt(hex, structCharOff).intValue();
        int arrStart = structCharOff + arrOff * 2;
        if (arrStart < structCharOff || hex.length() < arrStart + 64) return;
        int n = safeInt(bytesToBigInt(hex, arrStart));
        if (n <= 0 || n > 1000) return;
        int cursor = arrStart + 64;
        for (int i = 0; i < n; i++) {
            if (hex.length() < cursor + 64) break;
            int itemOff = bytesToBigInt(hex, cursor).intValue();
            int itemStart = arrStart + itemOff * 2;
            if (hex.length() < itemStart + 192) break;
            String key = readItemKey(hex, itemStart);
            // 闂傚倷鐒﹂崕瀹犮亹閻愮數绠旈柛宀€鍋涢弸渚€鏌ｅΔ鈧悧鍡欑矈閿曞倹鐓ユ繛鎴炵懐閸斿〖em 闂?4 婵犵鈧啿绾ч柡瀣吹濡叉劕顭冲▔宄礶s32 闂備胶顭堥敃锕傚储瑜嶉敃銏ゎ敂閸℃瑧鐣?3 婵?
            BigInteger b32Marker = bytesToBigInt(hex, itemStart + 64);
            String val;
            if (b32Marker.compareTo(BigInteger.valueOf(10000)) < 0 && b32Marker.signum() > 0) {
                val = "0x" + hex.substring(itemStart + 128, itemStart + 192);
            } else {
                val = "0x" + hex.substring(itemStart + 64, itemStart + 128);
            }
            if (key != null && !key.isEmpty()) {
                result.put(key, val);
            }
            cursor += 64;
        }
    }

    // ======================== 闂佸搫顦悧鍡涘箠鎼淬垺鍙忔い蹇撶墕濡﹢鎮峰▎蹇擃伀闁?========================

    private static String hex(String s) {
        return s.startsWith("0x") ? s.substring(2) : s;
    }

    private static BigInteger bytesToBigInt(String hex, int charOff) {
        if (hex.length() < charOff + 64) return BigInteger.ZERO;
        return new BigInteger(hex.substring(charOff, charOff + 64), 16);
    }

    private static int safeInt(BigInteger bi) {
        long v = bi.longValue();
        return (v < 0 || v > Integer.MAX_VALUE) ? -1 : (int) v;
    }

    // 闂備胶鍎甸弲娑㈡偤閵娧勬殰閻庢稒顭囬々?32 闂佽瀛╃粙鎺椼€冩径瀣╃箚?slot 濠电偞鍨堕幖鈺呭矗閳ь剚銇勯弴姘祮鐎规洩缍佸鍊燁槻闁轰礁鐖奸弻銈嗘綇閵婏妇鍙嗛梺鐑╁閸涱垳鐣堕梺鎸庢閸庡銆呴锔界叆婵炴垶顭囨晶銏ゆ煟?key 濠?"market"闂?account"闂備焦瀵х粙鎴βㄩ埀顒傜磼鏉堛劎绠栫紒瀣槸椤撳ジ宕奸姀鈹惧亾閳?offset 闂佽崵濮撮幖顐︽偪閸ヮ灐?
    private static String readInlineString(String hex, int charOff) {
        if (charOff < 0 || hex.length() < charOff + 64) return "";
        byte[] b = hexToBytes(hex.substring(charOff, charOff + 64));
        int end = 0;
        while (end < b.length && b[end] != 0) end++;
        return new String(b, 0, end, StandardCharsets.UTF_8);
    }

    private static String readString(String hex, int charOff) {
        if (charOff < 0 || hex.length() < charOff + 64) return "";
        int len = safeInt(bytesToBigInt(hex, charOff));
        if (len <= 0 || hex.length() < charOff + 64 + len * 2) return "";
        byte[] b = hexToBytes(hex.substring(charOff + 64, charOff + 64 + len * 2));
        return new String(b, StandardCharsets.UTF_8);
    }


    /**
     * ?? item key????????? slot0???<128 ? slot3+4 ??>=128 ? readString
     */
    private static String readItemKey(String hex, int itemStart) {
        BigInteger firstSlot = bytesToBigInt(hex, itemStart);
        if (firstSlot.compareTo(BigInteger.valueOf(10000)) >= 0 || firstSlot.signum() <= 0) {
            return readInlineString(hex, itemStart);
        }
        int offset = firstSlot.intValue();
        if (offset < 128) {
            int keyLen = bytesToBigInt(hex, itemStart + 192).intValue();
            if (keyLen > 0 && keyLen <= 64 && hex.length() >= itemStart + 256 + keyLen * 2) {
                byte[] kb = hexToBytes(hex.substring(itemStart + 256, itemStart + 256 + keyLen * 2));
                return new String(kb, StandardCharsets.UTF_8);
            }
            return "";
        }
        int keyOff = itemStart + offset * 2;
        return readString(hex, keyOff);
    }
    private static byte[] hexToBytes(String h) {
        int n = h.length();
        byte[] b = new byte[n / 2];
        for (int i = 0; i < n; i += 2)
            b[i / 2] = (byte) ((Character.digit(h.charAt(i), 16) << 4)
                             + Character.digit(h.charAt(i + 1), 16));
        return b;
    }

    private static String getAddr(Map<String, String> m, String k) {
        String v = m.get(k);
        return v != null ? v : "";
    }


    // ======================== Uniswap V2 Swap ========================

    /** Uniswap V2 Swap topic0 = keccak256("Swap(address,uint256,uint256,uint256,uint256,address)") */
    private static final Event SWAP_EVENT = new Event("Swap",
            Arrays.asList(
                    new TypeReference<Address>(true) {},   // sender (indexed)
                    new TypeReference<Uint256>() {},         // amount0In
                    new TypeReference<Uint256>() {},         // amount1In
                    new TypeReference<Uint256>() {},         // amount0Out
                    new TypeReference<Uint256>() {},         // amount1Out
                    new TypeReference<Address>(true) {}      // to (indexed)
            ));
    private static final String SWAP_EVENT_HASH = EventEncoder.encode(SWAP_EVENT);

    /** Swap topic0 hash used by eth_getLogs filter. */
    public static String getSwapEventHash() {
        return SWAP_EVENT_HASH;
    }

    /**
     * Decode a Uniswap V2 Swap log.
     * event Swap(address indexed sender, uint256 amount0In, uint256 amount1In,
     *            uint256 amount0Out, uint256 amount1Out, address indexed to);
     * topics[0] = event hash, topics[1] = sender, topics[2] = to
     * data = amount0In + amount1In + amount0Out + amount1Out (each 32 bytes).
     */
    public SwapEvent decodeSwap(Log logEntry, String chainName) {
        if (logEntry.getTopics().size() < 3) return null;
        if (!SWAP_EVENT_HASH.equalsIgnoreCase(logEntry.getTopics().get(0))) return null;

        try {
            String sender = "0x" + logEntry.getTopics().get(1).substring(26);
            String to     = "0x" + logEntry.getTopics().get(2).substring(26);

            List<Type> decoded = FunctionReturnDecoder.decode(
                    logEntry.getData(), SWAP_EVENT.getNonIndexedParameters());

            BigInteger amount0In  = (BigInteger) decoded.get(0).getValue();
            BigInteger amount1In  = (BigInteger) decoded.get(1).getValue();
            BigInteger amount0Out = (BigInteger) decoded.get(2).getValue();
            BigInteger amount1Out = (BigInteger) decoded.get(3).getValue();

            return new SwapEvent(
                    logEntry.getTransactionHash(),
                    logEntry.getBlockNumber().longValue(),
                    logEntry.getLogIndex().intValue(),
                    sender, to,
                    amount0In, amount1In, amount0Out, amount1Out,
                    chainName);
        } catch (Exception e) {
            log.warn("Swap decode failed tx={}: {}", logEntry.getTransactionHash(), e.getMessage());
            return null;
        }
    }
}

