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

}
