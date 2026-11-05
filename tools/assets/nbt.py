"""Reads Minecraft's NBT format. Input comes from outside, so sizes and depth are limited and files are never trusted.

Byte arrays come back as bytes; int and long arrays as tuples of signed numbers.
"""
import gzip
import io
import struct
import zlib

MAX_UNPACKED = 1024 * 1024 * 1024
MAX_DEPTH = 64


class NbtError(ValueError):
    pass


def unpack(data, limit=MAX_UNPACKED):
    """Removes gzip or zlib compression, stopping if the result would be huge."""
    if data[:2] == b"\x1f\x8b":
        with gzip.GzipFile(fileobj=io.BytesIO(data)) as stream:
            out = stream.read(limit + 1)
    elif data[:1] == b"\x78":
        decoder = zlib.decompressobj()
        out = decoder.decompress(data, limit + 1)
    else:
        return data
    if len(out) > limit:
        raise NbtError("This file unpacks to more than the 1 GB limit.")
    return out


def parse(raw):
    """Reads one uncompressed NBT document."""
    at = 0
    size = len(raw)

    def need(count):
        if count < 0 or at + count > size:
            raise NbtError("The file ends too early, or is damaged.")

    def number(fmt, width):
        nonlocal at
        need(width)
        value = struct.unpack_from(fmt, raw, at)[0]
        at += width
        return value

    def text():
        nonlocal at
        length = number(">H", 2)
        need(length)
        value = raw[at:at + length].decode("utf-8", "replace")
        at += length
        return value

    def array(fmt_char, width):
        nonlocal at
        count = number(">i", 4)
        need(count * width)
        value = struct.unpack_from(f">{count}{fmt_char}", raw, at)
        at += count * width
        return value

    def body(kind, depth):
        nonlocal at
        if depth > MAX_DEPTH:
            raise NbtError("The file is nested too deeply.")
        if kind == 1:
            return number(">b", 1)
        if kind == 2:
            return number(">h", 2)
        if kind == 3:
            return number(">i", 4)
        if kind == 4:
            return number(">q", 8)
        if kind == 5:
            return number(">f", 4)
        if kind == 6:
            return number(">d", 8)
        if kind == 7:
            count = number(">i", 4)
            need(count)
            value = bytes(raw[at:at + count])
            at += count
            return value
        if kind == 8:
            return text()
        if kind == 9:
            inner = number(">b", 1)
            count = number(">i", 4)
            if count > 0 and inner == 0:
                raise NbtError("The file is damaged.")
            if count > size:
                raise NbtError("The file is damaged.")
            return [body(inner, depth + 1) for _ in range(max(count, 0))]
        if kind == 10:
            out = {}
            while True:
                tag = number(">b", 1)
                if tag == 0:
                    return out
                key = text()
                out[key] = body(tag, depth + 1)
        if kind == 11:
            return array("i", 4)
        if kind == 12:
            return array("q", 8)
        raise NbtError("The file isn't in Minecraft's format.")

    root = number(">b", 1)
    if root != 10:
        raise NbtError("The file isn't in Minecraft's format.")
    text()
    return body(root, 0)


def read(data):
    return parse(unpack(data))
