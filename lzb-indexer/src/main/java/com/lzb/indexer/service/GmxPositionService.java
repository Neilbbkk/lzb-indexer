package com.lzb.indexer.service;

import com.lzb.indexer.domain.entity.GmxPosition;
import com.lzb.indexer.domain.entity.GmxPositionHistory;
import com.lzb.indexer.domain.repository.GmxPositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * GMX ?????????
 *
 * ?????? Increase / Decrease / Liquidate ??????? GmxPosition ???
 * ????????? position_key ?????? -> ?????????? -> ????
 *
 * ????????????????BlockScanner?????? >= ????????????????
 */
@Service
public class GmxPositionService {

    private static final Logger log = LoggerFactory.getLogger(GmxPositionService.class);

    private final GmxPositionRepository positionRepo;

    public GmxPositionService(GmxPositionRepository positionRepo) {
        this.positionRepo = positionRepo;
    }

    /**
     * ??????????????????
     * ?????EventDecoder ????????????????
     * ?????? position_key ????????????????????
     *         ?????????
     */
    @Transactional
    public void apply(GmxPositionHistory event) {
        switch (event.getEventType()) {
            case "INCREASE":
                applyIncrease(event);
                break;
            case "DECREASE":
                applyDecrease(event);
                break;
            case "LIQUIDATE":
                applyLiquidate(event);
                break;
            default:
                log.warn("Unknown GMX event type: {}", event.getEventType());
        }
    }

    /** ????? */
    private void applyIncrease(GmxPositionHistory e) {
        Optional<GmxPosition> existing = positionRepo
                .findByChainNameAndPositionKey(e.getChainName(), e.getPositionKey());

        if (existing.isPresent()) {
            GmxPosition pos = existing.get();
            pos.applyIncrease(e.getSizeDelta(), e.getCollateralDelta(),
                    e.getPrice(), e.getFee(), e.getBlockNumber());
            pos.touch(e.getBlockNumber(), e.getTxHash());
            positionRepo.save(pos);
            log.debug("Position increased: key={}, size={}", e.getPositionKey(), pos.getSize());
        } else {
            GmxPosition pos = GmxPosition.open(
                    e.getPositionKey(), e.getAccount(),
                    e.getCollateralToken(), e.getIndexToken(),
                    e.getIsLong(), e.getSizeDelta(), e.getCollateralDelta(),
                    e.getPrice(), e.getFee(), e.getBlockNumber(),
                    e.getTxHash(), e.getChainName());
            positionRepo.save(pos);
            log.info("Position opened: key={}, account={}, size={}, isLong={}",
                    e.getPositionKey(), e.getAccount(), pos.getSize(), e.getIsLong());
        }
    }

    /**
     * ??????
     * EventDecoder ?? sizeDelta/collateralDelta ????????????
     * ??????????????
     */
    private void applyDecrease(GmxPositionHistory e) {
        // EventDecoder ?? decrease ? delta ????
        BigInteger negSize = e.getSizeDelta();
        BigInteger negCollateral = e.getCollateralDelta();

        Optional<GmxPosition> existing = positionRepo
                .findByChainNameAndPositionKey(e.getChainName(), e.getPositionKey());

        if (!existing.isPresent()) {
            GmxPosition pos = GmxPosition.open(
                    e.getPositionKey(), e.getAccount(),
                    e.getCollateralToken(), e.getIndexToken(),
                    e.getIsLong(), negSize, negCollateral,
                    e.getPrice(), e.getFee(), e.getBlockNumber(),
                    e.getTxHash(), e.getChainName());
            if (pos.getSize().signum() <= 0) {
                pos.markClosed(e.getBlockNumber());
            }
            positionRepo.save(pos);
            log.warn("Decrease without prior Increase (mid-scan): key={}, account={}",
                    e.getPositionKey(), e.getAccount());
            return;
        }

        GmxPosition pos = existing.get();
        pos.applyIncrease(negSize, negCollateral,
                e.getPrice(), e.getFee(), e.getBlockNumber());
        pos.touch(e.getBlockNumber(), e.getTxHash());

        if (pos.getSize().signum() <= 0) {
            pos.markClosed(e.getBlockNumber());
            log.info("Position closed: key={}, account={}", e.getPositionKey(), e.getAccount());
        } else {
            log.debug("Position decreased: key={}, size={}", e.getPositionKey(), pos.getSize());
        }
        positionRepo.save(pos);
    }

    /** ???????????? LIQUIDATED */
    private void applyLiquidate(GmxPositionHistory e) {
        Optional<GmxPosition> existing = positionRepo
                .findByChainNameAndPositionKey(e.getChainName(), e.getPositionKey());

        if (existing.isPresent()) {
            GmxPosition pos = existing.get();
            pos.applyIncrease(
                    pos.getSize().negate(),
                    pos.getCollateral().negate(),
                    e.getPrice(), e.getFee(), e.getBlockNumber());
            pos.markLiquidated(e.getBlockNumber());
            pos.touch(e.getBlockNumber(), e.getTxHash());
            positionRepo.save(pos);
            log.info("Position liquidated: key={}, account={}, price={}",
                    e.getPositionKey(), e.getAccount(), e.getPrice());
        } else {
            GmxPosition pos = GmxPosition.open(
                    e.getPositionKey(), e.getAccount(),
                    e.getCollateralToken(), e.getIndexToken(),
                    e.getIsLong(), BigInteger.ZERO, BigInteger.ZERO,
                    e.getPrice(), e.getFee(), e.getBlockNumber(),
                    e.getTxHash(), e.getChainName());
            pos.markLiquidated(e.getBlockNumber());
            positionRepo.save(pos);
            log.warn("Liquidate without prior Increase (mid-scan): key={}, account={}",
                    e.getPositionKey(), e.getAccount());
        }
    }

    // ======================== ???? ========================

    @Transactional(readOnly = true)
    public List<GmxPosition> getPositionsByAccount(String chainName, String account) {
        return positionRepo.findByChainNameAndAccountOrderByLastUpdateBlockDesc(
                chainName, account.toLowerCase());
    }

    @Transactional(readOnly = true)
    public List<GmxPosition> getPositionsByStatus(String chainName, GmxPosition.Status status) {
        return positionRepo.findByChainNameAndStatusOrderByLastUpdateBlockDesc(chainName, status);
    }

    @Transactional(readOnly = true)
    public long countByChain(String chainName) {
        return positionRepo.countByChainName(chainName);
    }
}
