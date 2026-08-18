package com.lzb.indexer.service;

import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.GmxPositionHistoryRepository;
import com.lzb.indexer.domain.repository.SwapEventRepository;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanEventWriterTest {

    @Mock private TokenTransferRepository transferRepo;
    @Mock private SwapEventRepository swapEventRepo;
    @Mock private GmxPositionHistoryRepository gmxHistoryRepo;

    private TokenTransfer transfer(int i) {
        return new TokenTransfer("0x" + String.format("%064x", i), 1L, i,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                BigInteger.ONE, "chain");
    }

    @Test
    void saveTransfersChunksAt500() {
        when(transferRepo.existsByTxHashAndLogIndexAndChainName(any(), any(), any())).thenReturn(false);
        List<TokenTransfer> events = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            events.add(transfer(i));
        }

        ScanEventWriter writer = new ScanEventWriter(transferRepo, swapEventRepo, gmxHistoryRepo);
        writer.saveTransfers(events);

        verify(transferRepo, times(2)).saveAll(anyList());
    }

    @Test
    void saveTransfersSkipsDuplicates() {
        when(transferRepo.existsByTxHashAndLogIndexAndChainName(any(), any(), any())).thenReturn(true);
        List<TokenTransfer> events = new ArrayList<>();
        events.add(transfer(1));

        ScanEventWriter writer = new ScanEventWriter(transferRepo, swapEventRepo, gmxHistoryRepo);
        writer.saveTransfers(events);

        verify(transferRepo, never()).saveAll(anyList());
    }
}
