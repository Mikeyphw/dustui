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
