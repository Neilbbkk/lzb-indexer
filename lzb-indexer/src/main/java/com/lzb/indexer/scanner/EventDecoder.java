package com.lzb.indexer.scanner;

import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.entity.GmxPositionHistory;
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
 * ???????? ERC20 Transfer ? GMX V2 ???
 *
 * GMX V2 ?? EventEmitter ???? emitEventLog/emitEventLog2 ???
 * ???????? topic[1] ? eventNameHash ???
 * ???? EventUtils.EventLogData???????ABI ????
 */
@Component
public class EventDecoder {

    private static final Logger log = LoggerFactory.getLogger(EventDecoder.class);

    // ======================== ERC20 Transfer ========================

    private static final Event TRANSFER_EVENT = new Event(
            "Transfer",
            Arrays.asList(
                    new TypeReference<Address>(true) {},
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>(false) {}
            ));
    private static final String TRANSFER_EVENT_HASH = EventEncoder.encode(TRANSFER_EVENT);

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

    // ======================== GMX V2 ========================

    /** emitEventLog(bytes32 indexed, ...) */
    private static final String EMIT_EVENT_LOG_HASH  = "0x137a44067c8961cd7e1d876f4754a5a3a75989b4552f1843fc69c3b372def160";
    private static final String EMIT_EVENT_LOG2_HASH = "0x468a25a7ba624ceea6e540ad6f49171b52495b648417ae91bca21676d8a24dc5";

    /** eventNameHash = keccak256(eventName) */
    private static final String POSITION_INCREASE_HASH = "0xf94196ccb31f81a3e67df18f2a62cbfb50009c80a7d3c728a3f542e3abc5cb63";
    private static final String POSITION_DECREASE_HASH = "0x07d51b51b408d7c62dcc47cc558da5ce6a6e0fd129a427ebce150f52b0e5171a";

    public boolean isGmxV2Event(Log logEntry) {
        return logEntry.getTopics() != null && logEntry.getTopics().size() >= 2
                && (EMIT_EVENT_LOG_HASH.equals(logEntry.getTopics().get(0))
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

    /** V2 ?????? DecreasePosition ? isLiquidation flag ?? */
    public boolean isLiquidatePositionEvent(Log logEntry) {
        return false;
    }

    // ======================== ?? ========================

    public GmxPositionHistory decodeIncreasePosition(Log logEntry, String chainName) {
        if (!isIncreasePositionEvent(logEntry)) return null;
        boolean isLog2 = EMIT_EVENT_LOG2_HASH.equals(logEntry.getTopics().get(0));
        return decodePosition(logEntry, "INCREASE", chainName, isLog2, false);
    }

    public GmxPositionHistory decodeDecreasePosition(Log logEntry, String chainName) {
        if (!isDecreasePositionEvent(logEntry)) return null;
        boolean isLog2 = EMIT_EVENT_LOG2_HASH.equals(logEntry.getTopics().get(0));
        return decodePosition(logEntry, "DECREASE", chainName, isLog2, true);
    }

    public GmxPositionHistory decodeLiquidatePosition(Log logEntry, String chainName) {
        return null;
    }

    private GmxPositionHistory decodePosition(Log logEntry, String eventType, String chainName,
                                               boolean isLog2, boolean negate) {
        try {
            Map<String, String> addr = new LinkedHashMap<>();
            Map<String, BigInteger> uints = new LinkedHashMap<>();
            Map<String, Boolean> bools = new LinkedHashMap<>();
            Map<String, String> b32s = new LinkedHashMap<>();
            parseEventLogData(hex(logEntry.getData()), isLog2, addr, uints, bools, b32s);

            String account = getAddr(addr, "account");
            if (account.isEmpty() && logEntry.getTopics().size() > 2) {
                String t2 = logEntry.getTopics().get(2);
                if (t2 != null && t2.length() >= 42) {
                    account = t2.substring(t2.length() - 40);
                }
            }
            // GMX V2 ????? key ???? "collateralToken" / "initialCollateralToken" ?
            String collateralToken = getAddr(addr, "collateralToken");
            if (collateralToken.isEmpty()) collateralToken = getAddr(addr, "initialCollateralToken");
            // indexToken / market
            String market = getAddr(addr, "indexToken");
            if (market.isEmpty()) market = getAddr(addr, "market");
            // ???????????? key ?
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

            // ????
            boolean isLiquidation = bools.getOrDefault("isLiquidation", false);
            String resolvedEventType = eventType;
            if ("DECREASE".equals(eventType) && isLiquidation) {
                resolvedEventType = "LIQUIDATE";
            }

            // log only when needed
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

    // ======================== hash getters ========================

    public static String getTransferEventHash() { return TRANSFER_EVENT_HASH; }
    public static String getEmitEventLogHash() { return EMIT_EVENT_LOG_HASH; }
    public static String getEmitEventLog2Hash() { return EMIT_EVENT_LOG2_HASH; }
    public static String getPositionIncreaseHash() { return POSITION_INCREASE_HASH; }
    public static String getPositionDecreaseHash() { return POSITION_DECREASE_HASH; }

    // ======================== EventLogData ?? ========================

    /**
     * ?? emitEventLog/emitEventLog2 ? data ???
     *
     * emitEventLog  data = msgSender(32B) + eventName(??) + EventLogData(??)
     *   eventData ??? hex ?? 128
     *
     * emitEventLog2 data = msgSender(32B) + EventLogData(??)
     *   eventName ? topic[2], eventData ??? hex ?? 64
     */
    static void parseEventLogData(String hex, boolean isLog2,
                                   Map<String, String> addr,
                                   Map<String, BigInteger> uints,
                                   Map<String, Boolean> bools,
                                   Map<String, String> b32s) {
        if (hex == null || hex.length() < 128) return;

        // DUMP data header for debugging
        // log.info("DATA HEADER...");

        int edOffChar = bytesToBigInt(hex, isLog2 ? 64 : 128).intValue() * 2;
        if (edOffChar < 64 || hex.length() < edOffChar + 448) return;

        int[] relOff = new int[7];
        for (int i = 0; i < 7; i++) {
            long v = bytesToBigInt(hex, edOffChar + i * 64).longValue();
            if (v < 0 || v > 1000000) return;
            relOff[i] = (int) v;
        }

        // log at debug level
        if (log.isDebugEnabled()) {
            log.debug("EventLogData edOffChar={} isLog2={} relOff={}", edOffChar, isLog2, Arrays.toString(relOff));
        }

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
            // key???offset?????????offset???
            BigInteger firstSlot = bytesToBigInt(hex, itemStart);
            String key;
            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {
                // first slot = offset -> jump to key data
                int keyOff = itemStart + firstSlot.intValue() * 2;
                key = readString(hex, keyOff);
            } else {
                // first slot IS the key data (short key, < 32 bytes, inline)
                key = readInlineString(hex, itemStart);
            }
            // value ??? 32 ???
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
            if (hex.length() < itemStart + 128) break;
            BigInteger firstSlot = bytesToBigInt(hex, itemStart);
            String key;
            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {
                int keyOff = itemStart + firstSlot.intValue() * 2;
                key = readString(hex, keyOff);
            } else {
                key = readInlineString(hex, itemStart);
            }
            BigInteger val = bytesToBigInt(hex, itemStart + 64);
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
            if (hex.length() < itemStart + 128) break;
            BigInteger firstSlot = bytesToBigInt(hex, itemStart);
            String key;
            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {
                int keyOff = itemStart + firstSlot.intValue() * 2;
                key = readString(hex, keyOff);
            } else {
                key = readInlineString(hex, itemStart);
            }
            boolean val = !"0000000000000000000000000000000000000000000000000000000000000000"
                    .equals(hex.substring(itemStart + 64, itemStart + 128));
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
            if (hex.length() < itemStart + 128) break;
            BigInteger firstSlot = bytesToBigInt(hex, itemStart);
            String key;
            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {
                int keyOff = itemStart + firstSlot.intValue() * 2;
                key = readString(hex, keyOff);
            } else {
                key = readInlineString(hex, itemStart);
            }
            String val = "0x" + hex.substring(itemStart + 64, itemStart + 128);
            if (key != null && !key.isEmpty()) {
                result.put(key, val);
            }
            cursor += 64;
        }
    }

    // ======================== ???? ========================

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

    // ????32????????????????offset?
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
}
