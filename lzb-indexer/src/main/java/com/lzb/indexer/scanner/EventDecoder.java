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

    /** Transfer event topic0 hash used by BlockScanner's eth_getLogs filter. */
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
