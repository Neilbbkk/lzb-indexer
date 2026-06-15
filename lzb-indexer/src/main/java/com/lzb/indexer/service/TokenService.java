package com.lzb.indexer.service;

import com.lzb.indexer.config.ChainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final long SEPOLIA_CHAIN_ID = 11155111L;
    private static final BigInteger DEFAULT_PRIORITY_FEE = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(100_000L);

    private final ChainProperties chainProperties;
    private final Map<String, Web3j> web3jMap = new HashMap<>();
    private final Map<String, String> contractMap = new HashMap<>();
    private Credentials credentials;
    private String defaultChain;

    public TokenService(ChainProperties chainProperties) {
        this.chainProperties = chainProperties;
    }

    @PostConstruct
    public void init() {
        for (ChainProperties.ChainConfig cfg : chainProperties.getChains()) {
            Web3j w = Web3j.build(new HttpService(cfg.getRpcUrl()));
            web3jMap.put(cfg.getName(), w);
            contractMap.put(cfg.getName(), cfg.getContractAddress());

            if (cfg.getPrivateKey() != null && !cfg.getPrivateKey().isEmpty()) {
                this.credentials = Credentials.create(cfg.getPrivateKey());
                this.defaultChain = cfg.getName();
                log.info("Wallet loaded for chain {}: {}", cfg.getName(), credentials.getAddress());
            }
        }
    }

    private Web3j web3j() { return web3jMap.get(defaultChain); }
    private String contract() { return contractMap.get(defaultChain); }

    public BigInteger getBalance(String address) throws Exception {
        Function fn = new Function("balanceOf",
                Collections.singletonList(new Address(address)),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        return (BigInteger) callRaw(fn).get(0).getValue();
    }

    public String getTokenName() throws Exception {
        Function fn = new Function("name",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Utf8String>() {}));
        return callRaw(fn).get(0).getValue().toString();
    }

    public BigInteger getTotalSupply() throws Exception {
        Function fn = new Function("totalSupply",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        return (BigInteger) callRaw(fn).get(0).getValue();
    }

    public String transfer(String to, BigInteger amount) throws Exception {
        if (credentials == null) throw new RuntimeException("Private key not configured");

        Function fn = new Function("transfer",
                Arrays.asList(new Address(to), new Uint256(amount)),
                Collections.singletonList(new TypeReference<org.web3j.abi.datatypes.Bool>() {}));
        String encodedFunction = FunctionEncoder.encode(fn);

        Web3j w = web3j();
        BigInteger nonce = w.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.PENDING).send()
                .getTransactionCount();

        BigInteger priorityFee;
        try {
            priorityFee = w.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas();
        } catch (Exception e) {
            priorityFee = DEFAULT_PRIORITY_FEE;
        }

        BigInteger baseFee = w.ethGetBlockByNumber(
                DefaultBlockParameterName.LATEST, false).send()
                .getBlock().getBaseFeePerGas();
        BigInteger gasPrice = baseFee.add(priorityFee);

        RawTransaction rawTx = RawTransaction.createTransaction(
                nonce, gasPrice, GAS_LIMIT, contract(), BigInteger.ZERO, encodedFunction);
        byte[] signed = TransactionEncoder.signMessage(rawTx, SEPOLIA_CHAIN_ID, credentials);
        EthSendTransaction resp = w.ethSendRawTransaction(Numeric.toHexString(signed)).send();

        if (resp.hasError()) throw new RuntimeException("Transfer failed: " + resp.getError().getMessage());
        log.info("Transfer sent: tx={}", resp.getTransactionHash());
        return resp.getTransactionHash();
    }

    private List<Type> callRaw(Function fn) throws Exception {
        String encoded = FunctionEncoder.encode(fn);
        EthCall resp = web3j().ethCall(
                Transaction.createEthCallTransaction(
                        credentials.getAddress(), contract(), encoded),
                DefaultBlockParameterName.LATEST).send();
        if (resp.hasError()) throw new RuntimeException("RPC error: " + resp.getError().getMessage());
        String raw = resp.getResult();
        if (raw == null || raw.equals("0x")) throw new RuntimeException("Empty RPC response");
        List<Type> result = FunctionReturnDecoder.decode(raw, fn.getOutputParameters());
        if (result == null || result.isEmpty()) throw new RuntimeException("Failed to decode response");
        return result;
    }
}