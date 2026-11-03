#!/usr/bin/env python3
"""Strict, order-independent public Android aapt2 badging identity parsing.

Only identity fields are parsed; this module cannot sign, install, or modify APKs.
"""
from __future__ import annotations
import re

class BadgingError(ValueError):
    pass

# aapt2 sometimes inserts label= and other fields between name= and targetPackage=.
# An attribute is recognized only at a whitespace boundary with a single-quoted value.
_ATTRIBUTE = re.compile(r"(?<!\S)([A-Za-z][A-Za-z0-9_.:-]*)='([^']*)'")


def _fields(row: str, identity_keys: tuple[str, ...]) -> dict[str, str]:
    fields: dict[str, str] = {}
    for key, value in _ATTRIBUTE.findall(row):
        if key in identity_keys:
            if key in fields:
                raise BadgingError('duplicate aapt2 identity field: ' + key)
            fields[key] = value
    return fields


def parse_badging(raw: str) -> tuple[tuple[str | None, str | None], tuple[str | None, str | None] | None]:
    """Return ((package, versionCode), (runner, targetPackage)|None).

    Fail closed on missing/duplicate package rows or ambiguous instrumentation rows.
    Caller must compare all required values with trusted exact expectations.
    """
    packages = [line[len('package:'):].strip() for line in raw.splitlines() if line.startswith('package:')]
    instruments = [line[len('instrumentation:'):].strip() for line in raw.splitlines() if line.startswith('instrumentation:')]
    if len(packages) != 1:
        raise BadgingError(f'aapt2 package metadata row count is {len(packages)}, expected 1')
    if len(instruments) > 1:
        raise BadgingError(f'aapt2 instrumentation metadata row count is {len(instruments)}, maximum 1')
    package = _fields(packages[0], ('name', 'versionCode'))
    if not package.get('name'):
        raise BadgingError('aapt2 package name missing')
    result_pkg = (package['name'], package.get('versionCode'))
    if not instruments:
        return result_pkg, None
    inst = _fields(instruments[0], ('name', 'targetPackage'))
    return result_pkg, (inst.get('name'), inst.get('targetPackage'))

# `dump badging` does not reliably report instrumentation declarations.  The
# compiled manifest tree is authoritative; do not infer a runner from the
# existence of a test package, a manifest label, or the signed certificate.
_XML_ELEMENT = re.compile(r"^(?P<space>[ \t]*)E:[ \t]+(?P<name>[^\s(]+)(?:[ \t(]|$)")
_XML_ATTRIBUTE = re.compile(
    r"^[ \t]*A:[ \t]+(?P<name>[^\s(=]+)(?:\((?P<id>0x[0-9a-fA-F]{8})\))?=(?P<value>.*)$"
)
_XML_IDENTITY_IDS = {'name': '0x01010003', 'targetPackage': '0x01010021'}
_XML_LITERAL = re.compile(r'^"([^"\r\n]*)"(?:[ \t]+\(Raw: "[^"\r\n]*"\))?[ \t]*$')


def parse_instrumentation_xmltree(raw: str) -> tuple[str, str] | None:
    """Read exactly one top-level <instrumentation> from aapt2 dump xmltree.

    Return None only when the compiled manifest contains no instrumentation
    element. A caller requiring an instrumented test APK must then reject it.
    No source/merged-manifest file can substitute for bytes in the signed APK.
    """
    identity: dict[str, str] | None = None
    element_stack: list[tuple[int, str]] = []
    root_count = 0
    count = 0
    for line in raw.splitlines():
        elem = _XML_ELEMENT.match(line)
        if elem:
            depth = len(elem.group('space').expandtabs(8))
            while element_stack and element_stack[-1][0] >= depth:
                element_stack.pop()
            name = elem.group('name')
            if element_stack and element_stack[-1][1] == 'instrumentation':
                raise BadgingError('nested element inside compiled instrumentation declaration')
            if name == 'manifest':
                if element_stack:
                    raise BadgingError('nested compiled manifest root')
                root_count += 1
            elif name == 'instrumentation':
                if [entry[1] for entry in element_stack] != ['manifest']:
                    raise BadgingError('instrumentation is not a direct child of manifest')
                count += 1
                if count > 1:
                    raise BadgingError('multiple compiled instrumentation declarations')
                identity = {}
            element_stack.append((depth, name))
            continue
        if not element_stack or element_stack[-1][1] != 'instrumentation':
            continue
        a = _XML_ATTRIBUTE.match(line)
        if not a:
            continue
        # Ignore unrelated attributes such as label/functionalTest. Reject
        # resource-ID disagreement for the two Android framework identities.
        attribute = a.group('name')
        short_name = attribute.rsplit(':', 1)[-1]
        resource_id = (a.group('id') or '').lower()
        is_android = (resource_id != '' or
                      attribute.startswith('android:') or
                      attribute.startswith('http://schemas.android.com/apk/res/android:'))
        if short_name not in _XML_IDENTITY_IDS or not is_android:
            continue
        if resource_id and resource_id != _XML_IDENTITY_IDS[short_name]:
            raise BadgingError('unexpected Android resource ID for instrumentation ' + short_name)
        if short_name in identity:
            raise BadgingError('duplicate compiled instrumentation ' + short_name)
        val = _XML_LITERAL.fullmatch(a.group('value'))
        if not val or not val.group(1):
            raise BadgingError('nonliteral or empty compiled instrumentation ' + short_name)
        identity[short_name] = val.group(1)
    if root_count != 1:
        raise BadgingError('compiled Android manifest root missing or duplicated')
    if count == 0:
        return None
    assert identity is not None
    if set(identity) != {'name', 'targetPackage'}:
        raise BadgingError('compiled instrumentation missing runner or targetPackage')
    return identity['name'], identity['targetPackage']
