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
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final ChainProperties chainProperties;
    private final Map<String, Web3j> web3jMap = new HashMap<>();
    private final Map<String, String> contractMap = new HashMap<>();
    private String defaultChain;
    private String defaultWallet;

    public TokenService(ChainProperties chainProperties) {
        this.chainProperties = chainProperties;
    }

    @PostConstruct
    public void init() {
        for (ChainProperties.ChainConfig cfg : chainProperties.getChains()) {
            Web3j w = Web3j.build(new HttpService(cfg.getRpcUrl()));
            web3jMap.put(cfg.getName(), w);
            contractMap.put(cfg.getName(), cfg.getContractAddress());
            if (defaultChain == null && cfg.getRpcUrl() != null && !cfg.getRpcUrl().isEmpty()) {
                this.defaultChain = cfg.getName();
                this.defaultWallet = cfg.getWalletAddress();
                log.info("Read-only TokenService bound to chain {}", cfg.getName());
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

    private List<Type> callRaw(Function fn) throws Exception {
        String encoded = FunctionEncoder.encode(fn);
        String from = (defaultWallet != null && !defaultWallet.isEmpty())
                ? defaultWallet
                : Address.DEFAULT;
        EthCall resp = web3j().ethCall(
                Transaction.createEthCallTransaction(
                        from, contract(), encoded),
                DefaultBlockParameterName.LATEST).send();
        if (resp.hasError()) throw new RuntimeException("RPC error: " + resp.getError().getMessage());
        String raw = resp.getResult();
        if (raw == null || raw.equals("0x")) throw new RuntimeException("Empty RPC response");
        List<Type> result = FunctionReturnDecoder.decode(raw, fn.getOutputParameters());
        if (result == null || result.isEmpty()) throw new RuntimeException("Failed to decode response");
        return result;
    }
}
