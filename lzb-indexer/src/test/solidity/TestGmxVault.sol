// SPDX-License-Identifier: MIT
pragma solidity ^0.8.13;

/// GMX 事件模拟合约，用于 Anvil 集成测试。
/// 提供三个对外函数，每次调用 emit 对应的事件，参数由调用方传入。
contract TestGmxVault {
    event IncreasePosition(
        bytes32 key,
        address account,
        address collateralToken,
        address indexToken,
        uint256 collateralDelta,
        uint256 sizeDelta,
        bool isLong,
        uint256 price,
        uint256 fee
    );

    event DecreasePosition(
        bytes32 key,
        address account,
        address collateralToken,
        address indexToken,
        uint256 collateralDelta,
        uint256 sizeDelta,
        bool isLong,
        address receiver,
        uint256 price,
        uint256 fee
    );

    event LiquidatePosition(
        bytes32 key,
        address account,
        address collateralToken,
        address indexToken,
        bool isLong,
        uint256 size,
        uint256 collateral,
        uint256 reserveAmount,
        int256 realisedPnl,
        uint256 markPrice
    );

    function emitIncrease(
        bytes32 key,
        address account,
        address collateralToken,
        address indexToken,
        uint256 collateralDelta,
        uint256 sizeDelta,
        bool isLong,
        uint256 price,
        uint256 fee
    ) external {
        emit IncreasePosition(key, account, collateralToken, indexToken,
            collateralDelta, sizeDelta, isLong, price, fee);
    }

    function emitDecrease(
        bytes32 key,
        address account,
        address collateralToken,
        address indexToken,
        uint256 collateralDelta,
        uint256 sizeDelta,
        bool isLong,
        address receiver,
        uint256 price,
        uint256 fee
    ) external {
        emit DecreasePosition(key, account, collateralToken, indexToken,
            collateralDelta, sizeDelta, isLong, receiver, price, fee);
    }

    function emitLiquidate(
        bytes32 key,
        address account,
        address collateralToken,
        address indexToken,
        bool isLong,
        uint256 size,
        uint256 collateral,
        uint256 reserveAmount,
        int256 realisedPnl,
        uint256 markPrice
    ) external {
        emit LiquidatePosition(key, account, collateralToken, indexToken,
            isLong, size, collateral, reserveAmount, realisedPnl, markPrice);
    }
}