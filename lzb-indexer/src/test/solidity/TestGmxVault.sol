// SPDX-License-Identifier: MIT
pragma solidity ^0.8.13;

contract TestGmxVault {

    event emitEventLog(
        address indexed msgSender,
        string eventName,
        EventLogData eventData
    );

    event emitEventLog2(
        address indexed msgSender,
        EventLogData eventData
    );

    struct EventLogData {
        AddressItem[]   addressItems;
        UintItem[]      uintItems;
        IntItem[]       intItems;
        BoolItem[]      boolItems;
        Bytes32Item[]   bytes32Items;
        BytesItem[]     bytesItems;
        StringItem[]    stringItems;
    }

    struct AddressItem   { bytes32 key; address value; }
    struct UintItem      { bytes32 key; uint256 value; }
    struct IntItem       { bytes32 key; int256 value; }
    struct BoolItem      { bytes32 key; bool value; }
    struct Bytes32Item   { bytes32 key; bytes32 value; }
    struct BytesItem     { bytes32 key; bytes value; }
    struct StringItem    { bytes32 key; string value; }

    function _k(string memory s) internal pure returns (bytes32) {
        return bytes32(bytes(s));
    }

    function emitIncrease(
        bytes32 ok, address acc, address coll, address idx,
        uint256 cd, uint256 sd, bool isL, uint256 pr, uint256 fee
    ) external {
        AddressItem[] memory a = new AddressItem[](3);
        a[0] = AddressItem(_k("account"), acc);
        a[1] = AddressItem(_k("collateralToken"), coll);
        a[2] = AddressItem(_k("indexToken"), idx);

        UintItem[] memory u = new UintItem[](4);
        u[0] = UintItem(_k("sizeInUsd"), sd);
        u[1] = UintItem(_k("sizeInTokens"), sd > 0 ? 1 : 0);
        u[2] = UintItem(_k("executionPrice"), pr);
        u[3] = UintItem(_k("positionFeeAmount"), fee);

        BoolItem[] memory b = new BoolItem[](1);
        b[0] = BoolItem(_k("isLong"), isL);

        Bytes32Item[] memory b32 = new Bytes32Item[](1);
        b32[0] = Bytes32Item(_k("orderKey"), ok);

        emit emitEventLog(msg.sender, "PositionIncrease", EventLogData(
            a, u, new IntItem[](0), b, b32, new BytesItem[](0), new StringItem[](0)
        ));
    }

    function emitDecrease(
        bytes32 ok, address acc, address coll, address idx,
        uint256 cd, uint256 sd, bool isL, address rec, uint256 pr, uint256 fee
    ) external {
        AddressItem[] memory a = new AddressItem[](3);
        a[0] = AddressItem(_k("account"), acc);
        a[1] = AddressItem(_k("collateralToken"), coll);
        a[2] = AddressItem(_k("indexToken"), idx);

        UintItem[] memory u = new UintItem[](4);
        u[0] = UintItem(_k("sizeInUsd"), sd);
        u[1] = UintItem(_k("sizeInTokens"), sd > 0 ? 1 : 0);
        u[2] = UintItem(_k("executionPrice"), pr);
        u[3] = UintItem(_k("positionFeeAmount"), fee);

        BoolItem[] memory b = new BoolItem[](1);
        b[0] = BoolItem(_k("isLong"), isL);

        Bytes32Item[] memory b32 = new Bytes32Item[](1);
        b32[0] = Bytes32Item(_k("orderKey"), ok);

        emit emitEventLog(msg.sender, "PositionDecrease", EventLogData(
            a, u, new IntItem[](0), b, b32, new BytesItem[](0), new StringItem[](0)
        ));
    }

    function emitLiquidate(
        bytes32 ok, address acc, address coll, address idx,
        bool isL, uint256 sz, uint256 cl, uint256 rs, int256 pnl, uint256 mp
    ) external {
        AddressItem[] memory a = new AddressItem[](3);
        a[0] = AddressItem(_k("account"), acc);
        a[1] = AddressItem(_k("collateralToken"), coll);
        a[2] = AddressItem(_k("indexToken"), idx);

        UintItem[] memory u = new UintItem[](3);
        u[0] = UintItem(_k("sizeInUsd"), sz);
        u[1] = UintItem(_k("collateralAmount"), cl);
        u[2] = UintItem(_k("executionPrice"), mp);

        BoolItem[] memory b = new BoolItem[](2);
        b[0] = BoolItem(_k("isLong"), isL);
        b[1] = BoolItem(_k("isLiquidation"), true);

        Bytes32Item[] memory b32 = new Bytes32Item[](1);
        b32[0] = Bytes32Item(_k("orderKey"), ok);

        emit emitEventLog(msg.sender, "PositionDecrease", EventLogData(
            a, u, new IntItem[](0), b, b32, new BytesItem[](0), new StringItem[](0)
        ));
    }
}