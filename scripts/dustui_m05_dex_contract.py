#!/usr/bin/env python3
"""Fail-closed check that the signed M05 APK pair defines AndroidX Trace.

Checks DEX *class definitions*, not potentially misleading string references.
Does not build, install, resign, mutate or qualify a release/device.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import struct
import sys
import zipfile

TRACE_DESCRIPTOR = b"Landroidx/tracing/Trace;"
DEX_MEMBER = re.compile(r"classes(?:[2-9][0-9]*)?\.dex\Z")
MAX_DEX = 128 * 1024 * 1024


class DexContractError(ValueError):
    pass


def _u32(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise DexContractError("DEX table reference out of bounds")
    return struct.unpack_from("<I", data, offset)[0]


def _table(data: bytes, size_offset: int, start_offset: int, stride: int) -> tuple[int, int]:
    size, off = _u32(data, size_offset), _u32(data, start_offset)
    if size and (off < 0x70 or off > len(data) or size > (len(data) - off) // stride):
        raise DexContractError("invalid DEX table range")
    return size, off


def _uleb(data: bytes, pos: int) -> tuple[int, int]:
    value = 0
    for i in range(5):
        if pos >= len(data):
            raise DexContractError("truncated DEX string length")
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << (i * 7)
        if byte < 128:
            return value, pos
    raise DexContractError("invalid DEX string length")


def defines_trace(dex: bytes) -> bool:
    if (len(dex) < 0x70 or dex[:4] != b"dex\n" or dex[4:7].isdigit() is False
            or dex[7] != 0 or _u32(dex, 0x20) != len(dex)
            or _u32(dex, 0x24) != 0x70 or _u32(dex, 0x28) != 0x12345678):
        raise DexContractError("unexpected or malformed DEX header")
    string_count, strings = _table(dex, 0x38, 0x3c, 4)
    type_count, types = _table(dex, 0x40, 0x44, 4)
    class_count, classes = _table(dex, 0x60, 0x64, 32)
    for i in range(class_count):
        cls_type = _u32(dex, classes + i * 32)
        if cls_type >= type_count:
            raise DexContractError("invalid DEX class type index")
        str_idx = _u32(dex, types + 4 * cls_type)
        if str_idx >= string_count:
            raise DexContractError("invalid DEX descriptor index")
        off = _u32(dex, strings + 4 * str_idx)
        _utf16_len, start = _uleb(dex, off)
        end = start + len(TRACE_DESCRIPTOR)
        if end < len(dex) and dex[start:end] == TRACE_DESCRIPTOR and dex[end] == 0:
            return True
    return False


def apk_defines_trace(apk: Path) -> bool:
    try:
        with zipfile.ZipFile(apk) as archive:
            members = [i for i in archive.infolist() if DEX_MEMBER.fullmatch(i.filename)]
            if not members or len({i.filename for i in members}) != len(members):
                raise DexContractError("APK has missing or duplicate DEX members")
            found = False
            for member in members:
                if member.file_size > MAX_DEX:
                    raise DexContractError("DEX exceeds permitted bounded inspection size")
                if defines_trace(archive.read(member)):
                    found = True
            return found
    except (zipfile.BadZipFile, RuntimeError) as exc:
        raise DexContractError("could not inspect signed APK ZIP/DEX") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--test", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = {"schema": "dustui.m05.tracing-dex-check.v1",
                  "release_defines_trace": apk_defines_trace(args.app),
                  "instrumentation_defines_trace": apk_defines_trace(args.test)}
        if not (result["release_defines_trace"] or result["instrumentation_defines_trace"]):
            raise DexContractError("androidx.tracing.Trace is absent from both signed APK class definitions")
        result["status"] = "HOSTED_TRACING_CLASS_PRESENT_NOT_DEVICE_QUALIFIED"
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError) as exc:
        print("DUSTUI_M05_TRACING_DEX_REFUSED: " + str(exc)[:240], file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
