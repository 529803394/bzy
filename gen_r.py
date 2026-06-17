#!/usr/bin/env python3
import sys, os
from collections import defaultdict

r_txt = sys.argv[1]
out_dir = sys.argv[2]

resources = defaultdict(list)
with open(r_txt) as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < 4:
            continue
        typ, res_type, name, val = parts[0], parts[1], parts[2], parts[3]
        resources[res_type].append((typ, name, val))

pkg_dir = os.path.join(out_dir, "com", "example", "helloworld")
os.makedirs(pkg_dir, exist_ok=True)
out = os.path.join(pkg_dir, "R.java")

with open(out, "w") as f:
    f.write("package com.example.helloworld;\n\n")
    f.write("public final class R {\n")
    for res_type in sorted(resources.keys()):
        f.write("    public static final class " + res_type + " {\n")
        for typ, name, val in resources[res_type]:
            f.write("        public static final " + typ + " " + name + " = " + val + ";\n")
        f.write("    }\n")
    f.write("}\n")

print("Generated:", out)
