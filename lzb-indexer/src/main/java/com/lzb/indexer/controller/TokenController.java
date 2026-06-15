package com.lzb.indexer.controller;

import com.lzb.indexer.service.TokenService;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/token")
public class TokenController {

    private final TokenService tokenService;
    private static final String WALLET = "0x3642287697C85eEB038C04aA00Da55b059B00593";

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/info")
    public Map<String, Object> getInfo() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", tokenService.getTokenName());
        m.put("totalSupply", tokenService.getTotalSupply());
        return m;
    }

    @GetMapping("/balance/{address}")
    public Map<String, Object> getBalance(@PathVariable String address) throws Exception {
        BigInteger bal = tokenService.getBalance(address);
        Map<String, Object> m = new HashMap<>();
        m.put("address", address);
        m.put("balance", bal);
        m.put("balanceFormatted", bal.divide(BigInteger.TEN.pow(18)));
        return m;
    }

    @GetMapping("/my-balance")
    public Map<String, Object> getMyBalance() throws Exception {
        return getBalance(WALLET);
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(
            @RequestParam String to,
            @RequestParam(defaultValue = "100") BigInteger amount) throws Exception {
        BigInteger amountWei = amount.multiply(BigInteger.TEN.pow(18));
        String txHash = tokenService.transfer(to, amountWei);
        Map<String, Object> m = new HashMap<>();
        m.put("txHash", txHash);
        m.put("to", to);
        m.put("amount", amount);
        m.put("status", "sent");
        return m;
    }
}