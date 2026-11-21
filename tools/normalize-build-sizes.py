#!/usr/bin/env python3
"""Normalize file sizes for jar packaging on FUSE-mounted storage.

Some files written by javac on /sdcard report st_size == 0 even though their
bytes are present. `jar` trusts st_size and then fails with
"attempt to write past end of STORED entry". Rewriting only the mismatched
files through a temp file fixes the metadata without touching content.
"""
import os
import shutil
import sys

root = sys.argv[1] if len(sys.argv) > 1 else "build/classes"
fixed = 0
for base, _dirs, files in os.walk(root):
    for name in files:
        path = os.path.join(base, name)
        try:
            size = os.stat(path).st_size
            with open(path, "rb") as f:
                data = f.read()
        except OSError:
            continue
        if size == len(data):
            continue
        tmp = path + ".size-normalize"
        with open(tmp, "wb") as f:
            f.write(data)
        shutil.copystat(path, tmp)
        os.replace(tmp, path)
        fixed += 1
if fixed:
    print(f"  normalized {fixed} file size metadata entries")