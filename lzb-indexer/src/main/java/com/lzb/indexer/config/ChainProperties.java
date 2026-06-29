package com.lzb.indexer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
public class ChainProperties {

    private ScannerProperties scanner = new ScannerProperties();
    private List<ChainConfig> chains = new ArrayList<>();

    public ScannerProperties getScanner() { return scanner; }
    public void setScanner(ScannerProperties v) { this.scanner = v; }
    public List<ChainConfig> getChains() { return chains; }
    public void setChains(List<ChainConfig> v) { this.chains = v; }

    public static class ScannerProperties {
        private boolean enabled = true;
        private int fixedRateMs = 5000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public int getFixedRateMs() { return fixedRateMs; }
        public void setFixedRateMs(int v) { this.fixedRateMs = v; }
    }

    public static class ChainConfig {
        private String name;
        private String protocol = "ERC20";        // ERC20 | GMX | GMX_VAULT | GMX_LIQUIDATOR
        private String rpcUrl;
        private String contractAddress;
        private String walletAddress;
        private String privateKey;
        private long startBlock = 0;
        private int pageSize = 10000;
        private int reorgDepth = 12;

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String v) { this.protocol = v; }
        public String getRpcUrl() { return rpcUrl; }
        public void setRpcUrl(String v) { this.rpcUrl = v; }
        public String getContractAddress() { return contractAddress; }
        public void setContractAddress(String v) { this.contractAddress = v; }
        public String getWalletAddress() { return walletAddress; }
        public void setWalletAddress(String v) { this.walletAddress = v; }
        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String v) { this.privateKey = v; }
        public long getStartBlock() { return startBlock; }
        public void setStartBlock(long v) { this.startBlock = v; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int v) { this.pageSize = v; }
        public int getReorgDepth() { return reorgDepth; }
        public void setReorgDepth(int v) { this.reorgDepth = v; }

        /** 是否为 GMX 系列协议 */
        public boolean isGmx() {
            return protocol != null && protocol.startsWith("GMX");
        }
    }
}