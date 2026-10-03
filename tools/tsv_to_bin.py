#!/usr/bin/env python3
"""Chuyển vi_model (TSV hoặc .gz) sang định dạng nhị phân phẳng để app
mmap/đọc một lần — không parse string, không cấp phát map lồng.

Layout (little-endian):
  u32 magic "HKM1" | u16 ver=1 | u16 reserved
  u32 nWords | u32 nBos | u32 nBi | u32 nTri
  words:   nWords × (u16 byteLen + utf8 + i32 freq)
  bos:     nBos  × u32 wordIdx
  bigrams: nBi   × (u32 prevIdx, u32 nextIdx, u32 cnt)   — sort theo (p,n)
  trigram: nTri  × (u32 p2, u32 p1, u32 next, u32 cnt)   — sort theo (p2,p1,n)

Dùng:
  python3 tools/tsv_to_bin.py app/src/main/res/raw/vi_model.gz \
      app/src/main/res/raw/vi_model.bin
"""
import gzip
import struct
import sys

def open_maybe_gz(path):
    return gzip.open(path, "rt", encoding="utf8") if path.endswith(".gz") \
        else open(path, "r", encoding="utf8")

def main():
    src, dst = sys.argv[1], sys.argv[2]
    uni = []          # [(word, freq)] — thứ tự = index trong file
    bos = []
    bi = []           # (prevIdx, nextIdx, cnt)
    tri = []          # (p2Idx, p1Idx, nextIdx, cnt)
    wid = {}

    def idx(w):
        return wid.setdefault(w, len(wid))

    # Đọc TSV hai lượt: unigrams trước để có index, rồi bigram/trigram.
    uni_map = {}
    with open_maybe_gz(src) as f:
        rows = [l.rstrip("\n").split("\t") for l in f if l.strip() and l[0] != "#"]
    for f in rows:
        if f[0] == "u" and len(f) == 3:
            uni_map[f[1]] = int(f[2])
    for w, fq in uni_map.items():
        wid[w] = len(wid)
        uni.append((w, fq))
    for f in rows:
        if f[0] == "s" and len(f) >= 2 and f[1] in wid:
            bos.append(wid[f[1]])
        elif f[0] == "b" and len(f) == 4:
            if f[1] in wid and f[2] in wid:
                bi.append((wid[f[1]], wid[f[2]], int(f[3])))
        elif f[0] == "t" and len(f) == 5:
            if f[1] in wid and f[2] in wid and f[3] in wid:
                tri.append((wid[f[1]], wid[f[2]], wid[f[3]], int(f[4])))

    bi.sort()
    tri.sort()

    with open(dst, "wb") as f:
        f.write(struct.pack("<IHH4I", 0x314D4B48, 1, 0,
                            len(uni), len(bos), len(bi), len(tri)))
        for w, fq in uni:
            b = w.encode("utf8")
            f.write(struct.pack("<H", len(b)))
            f.write(b)
            f.write(struct.pack("<i", fq))
        for i in bos:
            f.write(struct.pack("<I", i))
        for p, n, c in bi:
            f.write(struct.pack("<III", p, n, c))
        for p2, p1, n, c in tri:
            f.write(struct.pack("<IIII", p2, p1, n, c))

    print(f"{dst}: {len(uni)} từ, {len(bos)} bos, {len(bi)} bi, {len(tri)} tri")

if __name__ == "__main__":
    main()
