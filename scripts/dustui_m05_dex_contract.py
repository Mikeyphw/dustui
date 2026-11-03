#!/usr/bin/env python3
"""Fail-closed signed M05 runner and cross-APK class-definition verification.

AndroidX Test initializes Tracing and Kotlin stdlib before any test method.
DEX class *definitions*, not references, must exist in at least one signed APK.
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
# Stable minimal bootstrap checks, not a claim of complete runtime linkage.
REQUIRED_BOOTSTRAP_CLASSES = {
    "androidx.tracing.Trace": TRACE_DESCRIPTOR,
    "kotlin.LazyKt": b"Lkotlin/LazyKt;",
    "kotlin.Lazy": b"Lkotlin/Lazy;",
    "kotlin.jvm.internal.Intrinsics": b"Lkotlin/jvm/internal/Intrinsics;",
    "kotlin.jvm.functions.Function0": b"Lkotlin/jvm/functions/Function0;",
}
# These are concrete public class names used by the four approved M05 tests.
# Test-only R8 rules keep *all* members, including Companion, INSTANCE and
# Kotlin default-argument constructors; the signed APK gate confirms that
# the corresponding classes have not been removed or renamed.
REQUIRED_RELEASE_APP_ABI_CLASSES = {
    # The previously obfuscated JSONObject implementation (Lfq0;) was missing
    # optString(String) when invoked from the separately minified test APK.
    "org.json.JSONObject": b"Lorg/json/JSONObject;",
    "com.mikeyphw.dustui.go.GoRuntimeProcess": b"Lcom/mikeyphw/dustui/go/GoRuntimeProcess;",
    "com.mikeyphw.dustui.go.GoRuntimeProcess$Companion": b"Lcom/mikeyphw/dustui/go/GoRuntimeProcess$Companion;",
    "com.mikeyphw.dustui.go.GoRuntimePathContract": b"Lcom/mikeyphw/dustui/go/GoRuntimePathContract;",
    "com.mikeyphw.dustui.go.GoChildProcessPolicy": b"Lcom/mikeyphw/dustui/go/GoChildProcessPolicy;",
    "com.mikeyphw.dustui.go.GoRuntimeRequest": b"Lcom/mikeyphw/dustui/go/GoRuntimeRequest;",
    "com.mikeyphw.dustui.ui.DustRouteSelection": b"Lcom/mikeyphw/dustui/ui/DustRouteSelection;",
    "com.mikeyphw.dustui.ui.DustSemanticRoute": b"Lcom/mikeyphw/dustui/ui/DustSemanticRoute;",
    "com.mikeyphw.dustui.ui.DustSemanticNavigation": b"Lcom/mikeyphw/dustui/ui/DustSemanticNavigation;",
    "com.mikeyphw.dustui.ui.DustAdaptiveLayoutPolicy": b"Lcom/mikeyphw/dustui/ui/DustAdaptiveLayoutPolicy;",
}

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


def defined_descriptors(dex: bytes, required: set[bytes]) -> set[bytes]:
    """Find exact DEX class_def entries, not type_id/string-only references."""
    if (len(dex) < 0x70 or dex[:4] != b"dex\n" or not dex[4:7].isdigit()
            or dex[7] != 0 or _u32(dex, 0x20) != len(dex)
            or _u32(dex, 0x24) != 0x70 or _u32(dex, 0x28) != 0x12345678):
        raise DexContractError("unexpected or malformed DEX header")
    string_count, strings = _table(dex, 0x38, 0x3c, 4)
    type_count, types = _table(dex, 0x40, 0x44, 4)
    class_count, classes = _table(dex, 0x60, 0x64, 32)
    found: set[bytes] = set()
    for i in range(class_count):
        cls_type = _u32(dex, classes + i * 32)
        if cls_type >= type_count:
            raise DexContractError("invalid DEX class type index")
        str_idx = _u32(dex, types + 4 * cls_type)
        if str_idx >= string_count:
            raise DexContractError("invalid DEX descriptor index")
        off = _u32(dex, strings + 4 * str_idx)
        _utf16_len, at = _uleb(dex, off)
        for descriptor in required - found:
            end = at + len(descriptor)
            if end < len(dex) and dex[at:end] == descriptor and dex[end] == 0:
                found.add(descriptor)
                break
    return found


def defines_trace(dex: bytes) -> bool:
    """Retain historical public helper used by the previous M05 contract."""
    return TRACE_DESCRIPTOR in defined_descriptors(dex, {TRACE_DESCRIPTOR})


def apk_defined_descriptors(apk: Path, required: set[bytes]) -> set[bytes]:
    try:
        with zipfile.ZipFile(apk) as archive:
            members = [i for i in archive.infolist() if DEX_MEMBER.fullmatch(i.filename)]
            if not members or len({i.filename for i in members}) != len(members):
                raise DexContractError("APK has missing or duplicate DEX members")
            found: set[bytes] = set()
            for member in members:
                if member.file_size > MAX_DEX:
                    raise DexContractError("DEX exceeds permitted bounded inspection size")
                found |= defined_descriptors(archive.read(member), required)
            return found
    except (zipfile.BadZipFile, RuntimeError) as exc:
        raise DexContractError("could not inspect signed APK ZIP/DEX") from exc


def apk_defines_trace(apk: Path) -> bool:
    return TRACE_DESCRIPTOR in apk_defined_descriptors(apk, {TRACE_DESCRIPTOR})



def _dex_type_descriptor(data: bytes, type_index: int, string_count: int, strings: int,
                         type_count: int, types: int) -> bytes:
    if type_index >= type_count:
        raise DexContractError("invalid DEX method type index")
    string_index = _u32(data, types + 4 * type_index)
    if string_index >= string_count:
        raise DexContractError("invalid DEX method string index")
    string_off = _u32(data, strings + 4 * string_index)
    _, at = _uleb(data, string_off)
    end = data.find(b"\0", at)
    if end < 0:
        raise DexContractError("unterminated DEX method string")
    return data[at:end]


def dex_defines_json_optstring(dex: bytes) -> bool:
    """Check a declared PUBLIC virtual optString(String):String in class_data.

    DEX method_ids alone can describe unresolved references; class_data's
    encoded method list establishes a real method definition in this APK.
    """
    json_desc = b"Lorg/json/JSONObject;"
    if json_desc not in defined_descriptors(dex, {json_desc}):
        return False
    string_count, strings = _table(dex, 0x38, 0x3c, 4)
    type_count, types = _table(dex, 0x40, 0x44, 4)
    proto_count, protos = _table(dex, 0x48, 0x4c, 12)
    method_count, methods = _table(dex, 0x58, 0x5c, 8)
    class_count, classes = _table(dex, 0x60, 0x64, 32)

    def get_type(index: int) -> bytes:
        return _dex_type_descriptor(dex, index, string_count, strings, type_count, types)

    for i in range(class_count):
        entry = classes + i * 32
        cls_type = _u32(dex, entry)
        if get_type(cls_type) != json_desc:
            continue
        class_data_off = _u32(dex, entry + 24)
        if class_data_off == 0:
            return False
        at = class_data_off
        static_count, at = _uleb(dex, at)
        instance_count, at = _uleb(dex, at)
        direct_count, at = _uleb(dex, at)
        virtual_count, at = _uleb(dex, at)
        for _ in range(static_count + instance_count):
            _, at = _uleb(dex, at)  # field_idx_diff
            _, at = _uleb(dex, at)  # access_flags
        for group_index, group_size in enumerate((direct_count, virtual_count)):
            method_idx = 0
            for _ in range(group_size):
                delta, at = _uleb(dex, at)
                method_idx += delta
                flags, at = _uleb(dex, at)
                _, at = _uleb(dex, at)  # code_off
                if method_idx >= method_count:
                    raise DexContractError("invalid DEX declared method index")
                if group_index == 0 or not (flags & 1):
                    continue
                idx = methods + method_idx * 8
                declaring_type = int.from_bytes(dex[idx:idx + 2], "little")
                proto_idx = int.from_bytes(dex[idx + 2:idx + 4], "little")
                method_name_idx = _u32(dex, idx + 4)
                if declaring_type != cls_type or proto_idx >= proto_count or method_name_idx >= string_count:
                    raise DexContractError("invalid DEX declared method reference")
                method_name_off = _u32(dex, strings + method_name_idx * 4)
                _, name_at = _uleb(dex, method_name_off)
                if dex[name_at:name_at + 10] != b"optString\0":
                    continue
                proto = protos + proto_idx * 12
                ret_idx = _u32(dex, proto + 4)
                args_off = _u32(dex, proto + 8)
                if get_type(ret_idx) != b"Ljava/lang/String;" or args_off == 0:
                    continue
                argc = _u32(dex, args_off)
                if argc != 1 or args_off + 6 > len(dex):
                    continue
                param_idx = int.from_bytes(dex[args_off + 4:args_off + 6], "little")
                if get_type(param_idx) == b"Ljava/lang/String;":
                    return True
        return False
    return False


def apk_defines_json_optstring(apk: Path) -> bool:
    try:
        with zipfile.ZipFile(apk) as archive:
            members = [member for member in archive.infolist()
                       if DEX_MEMBER.fullmatch(member.filename)]
            if not members or len({member.filename for member in members}) != len(members):
                raise DexContractError("APK has missing or duplicate DEX members")
            found = False
            for member in members:
                if member.file_size > MAX_DEX:
                    raise DexContractError("DEX exceeds permitted bounded inspection size")
                if dex_defines_json_optstring(archive.read(member)):
                    found = True
            return found
    except (zipfile.BadZipFile, RuntimeError) as exc:
        raise DexContractError("could not inspect signed APK ZIP/DEX") from exc

def verify_pair(app: Path, test: Path) -> dict:
    bootstrap_required = set(REQUIRED_BOOTSTRAP_CLASSES.values())
    abi_required = set(REQUIRED_RELEASE_APP_ABI_CLASSES.values())
    release = apk_defined_descriptors(app, bootstrap_required | abi_required)
    instrumentation = apk_defined_descriptors(test, bootstrap_required)
    missing_bootstrap = [name for name, descriptor in REQUIRED_BOOTSTRAP_CLASSES.items()
                         if descriptor not in release | instrumentation]
    if missing_bootstrap:
        raise DexContractError('runner bootstrap class definitions missing from signed APK pair: '
                               + ', '.join(missing_bootstrap))
    # The test APK references the production application's classes by name.
    # They must be defined by the signed release, not merely available in the
    # separately signed test APK (nor present as DEX string/type references).
    missing_abi = [name for name, descriptor in REQUIRED_RELEASE_APP_ABI_CLASSES.items()
                   if descriptor not in release]
    if missing_abi:
        raise DexContractError('M05 test-accessed release app ABI class definitions missing: '
                               + ', '.join(missing_abi))
    if not apk_defines_json_optstring(app):
        raise DexContractError("signed release lacks public org.json.JSONObject.optString(String):String definition")
    return {
        "schema": "dustui.m05.runner-and-app-abi-dex-check.v4",
        "release_json_optstring_defined": True,
        "release_defines_trace": TRACE_DESCRIPTOR in release,
        "instrumentation_defines_trace": TRACE_DESCRIPTOR in instrumentation,
        "required_class_locations": {
            name: {"release": desc in release, "instrumentation": desc in instrumentation}
            for name, desc in REQUIRED_BOOTSTRAP_CLASSES.items()
        },
        "release_app_abi_defined": {
            name: desc in release for name, desc in REQUIRED_RELEASE_APP_ABI_CLASSES.items()
        },
        "status": "HOSTED_RUNNER_AND_APP_ABI_CLASSES_PRESENT_NOT_DEVICE_QUALIFIED",
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--test", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        result = verify_pair(args.app, args.test)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError) as exc:
        print("DUSTUI_M05_RUNNER_BOOTSTRAP_DEX_REFUSED: " + str(exc)[:240], file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
