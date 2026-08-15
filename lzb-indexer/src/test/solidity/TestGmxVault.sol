// SPDX-License-Identifier: MIT
pragma solidity ^0.8.13;

// 与主网 GMX V2 EventEmitter 保持字节级一致（不依赖 Solidity 标准结构体编码）：
// 主网 EventLogData 使用自定义布局，item = [内联 key, 0x40, value]，
// 数组偏移以 length 字为基准。这里手工拼接 data，再用 log3 发射。
contract TestGmxVault {

    bytes32 constant EVENT_LOG1_SIG = 0x137a44067c8961cd7e1d876f4754a5a3a75989b4552f1843fc69c3b372def160;

    // ---- 自定义编码：单条 item = [内联 key(32B), 0x40(32B), value(32B)] ----

    function _item(bytes32 key, bytes32 value) internal pure returns (bytes memory) {
        return abi.encodePacked(key, uint256(0x40), value);
    }

    function _itemsArray(bytes32[] memory keys, bytes32[] memory values) internal pure returns (bytes memory) {
        uint256 n = keys.length;
        bytes memory out = abi.encodePacked(uint256(n));
        // 偏移以数组 length 字位置为基准（与主网一致）
        for (uint256 i = 0; i < n; i++) {
            out = bytes.concat(out, abi.encodePacked(uint256(32 + 32 * n + 96 * i)));
        }
        for (uint256 i = 0; i < n; i++) {
            out = bytes.concat(out, _item(keys[i], values[i]));
        }
        return out;
    }

    function _itemsStruct(bytes32[] memory keys, bytes32[] memory values) internal pure returns (bytes memory) {
        bytes memory arr = _itemsArray(keys, values);
        // struct = [items 偏移(0x40), arrayItems 偏移] + items 数组 + 空 arrayItems
        return bytes.concat(
            abi.encodePacked(uint256(0x40), uint256(64 + arr.length)),
            arr,
            abi.encodePacked(uint256(0))
        );
    }

    function _emptyItems() internal pure returns (bytes memory) {
        return _itemsStruct(new bytes32[](0), new bytes32[](0));
    }

    function _addrItems(address acc, address coll, address idx) internal pure returns (bytes memory) {
        bytes32[] memory keys = new bytes32[](3);
        bytes32[] memory vals = new bytes32[](3);
        keys[0] = bytes32("account");          vals[0] = bytes32(uint256(uint160(acc)));
        keys[1] = bytes32("collateralToken");  vals[1] = bytes32(uint256(uint160(coll)));
        keys[2] = bytes32("indexToken");       vals[2] = bytes32(uint256(uint160(idx)));
        return _itemsStruct(keys, vals);
    }

    function _uintItems(uint256 v0, uint256 v1, uint256 v2, uint256 v3) internal pure returns (bytes memory) {
        bytes32[] memory keys = new bytes32[](4);
        bytes32[] memory vals = new bytes32[](4);
        keys[0] = bytes32("sizeInUsd");          vals[0] = bytes32(v0);
        keys[1] = bytes32("sizeInTokens");       vals[1] = bytes32(uint256(v0 > 0 ? 1 : 0));
        keys[2] = bytes32("executionPrice");     vals[2] = bytes32(v2);
        keys[3] = bytes32("positionFeeAmount");  vals[3] = bytes32(v3);
        return _itemsStruct(keys, vals);
    }

    function _uintItemsLiquidate(uint256 sz, uint256 cl, uint256 mp) internal pure returns (bytes memory) {
        bytes32[] memory keys = new bytes32[](3);
        bytes32[] memory vals = new bytes32[](3);
        keys[0] = bytes32("sizeInUsd");       vals[0] = bytes32(sz);
        keys[1] = bytes32("collateralAmount"); vals[1] = bytes32(cl);
        keys[2] = bytes32("executionPrice");  vals[2] = bytes32(mp);
        return _itemsStruct(keys, vals);
    }

    function _boolItems(bool isL) internal pure returns (bytes memory) {
        bytes32[] memory keys = new bytes32[](1);
        bytes32[] memory vals = new bytes32[](1);
        keys[0] = bytes32("isLong"); vals[0] = bytes32(uint256(isL ? 1 : 0));
        return _itemsStruct(keys, vals);
    }

    function _boolItemsLiquidate(bool isL) internal pure returns (bytes memory) {
        bytes32[] memory keys = new bytes32[](2);
        bytes32[] memory vals = new bytes32[](2);
        keys[0] = bytes32("isLong");         vals[0] = bytes32(uint256(isL ? 1 : 0));
        keys[1] = bytes32("isLiquidation");  vals[1] = bytes32(uint256(1));
        return _itemsStruct(keys, vals);
    }

    function _b32Items(bytes32 ok) internal pure returns (bytes memory) {
        bytes32[] memory keys = new bytes32[](1);
        bytes32[] memory vals = new bytes32[](1);
        keys[0] = bytes32("orderKey"); vals[0] = ok;
        return _itemsStruct(keys, vals);
    }

    function _eventData(bytes memory a, bytes memory u, bytes memory b, bytes memory k)
        internal pure returns (bytes memory)
    {
        bytes[] memory structs = new bytes[](7);
        structs[0] = a;
        structs[1] = u;
        structs[2] = _emptyItems(); // intItems
        structs[3] = b;
        structs[4] = k;
        structs[5] = _emptyItems(); // bytesItems
        structs[6] = _emptyItems(); // stringItems

        uint256[] memory offs = new uint256[](7);
        uint256 off = 224; // 7 个偏移字
        for (uint256 i = 0; i < 7; i++) {
            offs[i] = off;
            off += structs[i].length;
        }
        bytes memory out = new bytes(0);
        for (uint256 i = 0; i < 7; i++) {
            out = bytes.concat(out, abi.encodePacked(offs[i]));
        }
        for (uint256 i = 0; i < 7; i++) {
            out = bytes.concat(out, structs[i]);
        }
        return out;
    }

    function _emit(string memory eventName, bytes32 topic1, bytes memory eventData) internal {
        bytes32 nameHash = keccak256(bytes(eventName));
        bytes memory data = bytes.concat(
            abi.encodePacked(uint256(uint160(msg.sender))),
            abi.encodePacked(uint256(0x60), uint256(0xa0)),
            abi.encodePacked(uint256(bytes(eventName).length)),
            bytes32(bytes(eventName)),
            eventData
        );
        assembly {
            log3(add(data, 0x20), mload(data), EVENT_LOG1_SIG, nameHash, topic1)
        }
    }

    function emitIncrease(
        bytes32 ok, address acc, address coll, address idx,
        uint256 cd, uint256 sd, bool isL, uint256 pr, uint256 fee
    ) external {
        _emitIncrease(ok, acc, coll, idx, sd, isL, pr, fee);
    }

    function _emitIncrease(
        bytes32 ok, address acc, address coll, address idx,
        uint256 sd, bool isL, uint256 pr, uint256 fee
    ) internal {
        bytes memory a = _addrItems(acc, coll, idx);
        bytes memory u = _uintItems(sd, sd, pr, fee);
        bytes memory b = _boolItems(isL);
        bytes memory k = _b32Items(ok);
        _emit(
            "PositionIncrease",
            bytes32(uint256(uint160(acc))),
            _eventData(a, u, b, k)
        );
    }

    function emitDecrease(
        bytes32 ok, address acc, address coll, address idx,
        uint256 cd, uint256 sd, bool isL, address rec, uint256 pr, uint256 fee
    ) external {
        _emitDecrease(ok, acc, coll, idx, sd, isL, pr, fee);
    }

    function _emitDecrease(
        bytes32 ok, address acc, address coll, address idx,
        uint256 sd, bool isL, uint256 pr, uint256 fee
    ) internal {
        bytes memory a = _addrItems(acc, coll, idx);
        bytes memory u = _uintItems(sd, sd, pr, fee);
        bytes memory b = _boolItems(isL);
        bytes memory k = _b32Items(ok);
        _emit(
            "PositionDecrease",
            bytes32(uint256(uint160(acc))),
            _eventData(a, u, b, k)
        );
    }

    function emitLiquidate(
        bytes32 ok, address acc, address coll, address idx,
        bool isL, uint256 sz, uint256 cl, uint256 rs, int256 pnl, uint256 mp
    ) external {
        _emitLiquidate(ok, acc, coll, idx, isL, sz, cl, mp);
    }

    function _emitLiquidate(
        bytes32 ok, address acc, address coll, address idx,
        bool isL, uint256 sz, uint256 cl, uint256 mp
    ) internal {
        bytes memory a = _addrItems(acc, coll, idx);
        bytes memory u = _uintItemsLiquidate(sz, cl, mp);
        bytes memory b = _boolItemsLiquidate(isL);
        bytes memory k = _b32Items(ok);
        _emit(
            "PositionDecrease",
            bytes32(uint256(uint160(acc))),
            _eventData(a, u, b, k)
        );
    }
}
