#!/usr/bin/env python3
"""Xây mô hình n-gram tiếng Việt từ dump Wikipedia.

Nguồn: https://dumps.wikimedia.org/viwiki/ — văn bản Wikipedia CC BY-SA 4.0.
Đầu ra là SỐ LIỆU THỐNG KÊ (tần suất n-gram), không phải văn bản gốc.

Dùng:
    python3 tools/build_model.py tools/corpus/viwiki-p1.bz2 app/src/main/res/raw/vi_model.tsv

Hai lượt đọc stream để giới hạn RAM:
  lượt 1 — unigram + từ đứng đầu câu (BOS) + tách tập giữ riêng eval
  lượt 2 — bigram/trigram chỉ trên từ thuộc vocab đủ phổ biến
"""
import bz2
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from random import Random

MIN_UNI = 8          # unigram freq tối thiểu để vào vocab
MAX_VOCAB = 60000    # vocab cap theo freq
MIN_BI = 4           # bigram freq tối thiểu
MIN_TRI = 3          # trigram freq tối thiểu
TOP_BI = 6           # bigram tối đa mỗi prev
TOP_TRI = 4          # trigram tối đa mỗi cặp
TOP_BOS = 400        # số từ mở câu giữ lại
EVAL_EVERY = 997     # cứ ~1/997 câu giữ riêng cho eval (không đếm vào model)
EVAL_MAX = 30000

NS = "{http://www.mediawiki.org/xml/export-0.11/}"

# Chữ cái tiếng Việt hợp lệ (a-z + đ + nguyên âm dấu phụ + dấu thanh)
VI_LETTERS = frozenset(
    "abcdefghijklmnopqrstuvwxyzđ"
    "ăâêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩị"
    "óòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ"
)

def is_vi_word(w: str) -> bool:
    return 0 < len(w) <= 20 and all(c in VI_LETTERS for c in w)

# --- Luật âm tiết VN (port từ TelexRoundTripTest.isVietnameseSyllable) ---
_ROWS = ["aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ", "eéèẻẽẹ", "êếềểễệ", "iíìỉĩị",
         "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ", "uúùủũụ", "ưứừửữự", "yýỳỷỹỵ"]
_IDX = {}
for _row in _ROWS:
    for _i, _ch in enumerate(_row):
        _IDX[_ch] = (_row[0], _i)
_IDX['đ'] = ('đ', 0)

def _deaccent(c):
    b = _IDX.get(c, (c, 0))[0]
    return {'đ': 'd', 'ă': 'a', 'â': 'a', 'ê': 'e', 'ô': 'o', 'ơ': 'o', 'ư': 'u'}.get(b, b)

_ONSETS = ["ngh", "qu", "gi", "gh", "ng", "nh", "ch", "kh", "ph", "th", "tr",
           "b", "c", "d", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "x", ""]
_NUCLEI = {"a", "e", "i", "o", "u", "y", "ai", "ao", "au", "ay", "eo", "eu",
           "ia", "ie", "iu", "oa", "oe", "oi", "oo", "ua", "ue", "ui", "uo",
           "uy", "uu", "ya", "ye", "ieu", "yeu", "uya", "uye", "uyu", "uoi",
           "uou", "oai", "oao", "oay", "oeo", "uay"}
_CODAS = {"", "c", "ch", "m", "n", "ng", "nh", "p", "t"}

def is_vi_syllable(w: str) -> bool:
    """Một âm tiết VN hợp lệ (lọc English/Latin/markup lẫn trong corpus)."""
    if not w:
        return False
    tones = 0
    for i, c in enumerate(w):
        if c not in _IDX and not ('a' <= c <= 'z'):
            return False
        b, t = _IDX.get(c, (c, 0))
        if t > 0:
            tones += 1
        if b == 'đ' and i != 0:
            return False
    if tones > 1:
        return False
    plain = ''.join(_deaccent(c) for c in w)
    for on in _ONSETS:
        if plain.startswith(on):
            rh = plain[len(on):]
            for ln in range(len(rh), 0, -1):
                if rh[:ln] in _NUCLEI and rh[ln:] in _CODAS:
                    return True
    return False

# Token rõ ràng tiếng Anh/web trùng hình âm tiết VN (the~thẻ, long~lông…)
# — giữ các dạng không dấu thật của từ VN (to, do, in, an, it, me, ten…).
EN_STOP = set("""of the he we she and for you your with that this have has had are
was were been they them their there these those what when where which who whom
will would could should from into over under again between through during before
after above below down off then once here why how all both each few more other
some such not only own same than too very just now also well even still back
much many most our out day get got give gave go went gone see saw seen take
took make made know knew come came look use used work world life hand part
place week case home year good first last little right great big high
small large next early young important public bad able sure free full real best
better low late hard major happy whole black white dark light easy strong true
clear deep wide close open short past fine dead poor cold english
web www html http https php asp jsp jpg jpeg png gif svg css xml rss ftp url
uri org net com edu gov info biz site sites page pages link links file files
data text image images video videos audio media index search edit post posts
user users admin login logout email mail online offline server download upload
software internet website webserver browser script code content category
categories template infobox stub list article articles wikipedia wiki commons
thumb left right center align class style div span table font color size
width height src alt href title ref en de fr ru zh ja ko es pt vi
at on is if or us no so up by my""".split())

# Từ mở câu kiểu chat xếp trước corpus BOS (Wikipedia thiên văn phong báo).
BOS_SEED = ["xin", "chào", "dạ", "tôi", "anh", "em", "vâng", "mình", "bạn",
            "được", "không", "cảm", "ơn", "hôm", "nay", "sáng", "chiều",
            "tối", "rồi", "ừ", "đi", "làm", "ăn", "ngủ", "chơi", "về",
            "nhé", "ạ", "oke", "ok", "thưa"]

def keep_token(w: str) -> bool:
    return (is_vi_syllable(w) and w not in EN_STOP
            and not any(c in 'fjwz' for c in w))

REF_RE = re.compile(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>", re.S | re.I)
TAG_RE = re.compile(r"<[^>]+>")
LINK_RE = re.compile(r"\[\[(?:[^]|]*)\|([^]|]*)\]\]|\[\[([^]|]*)\]\]")
EXT_RE = re.compile(r"\[[a-z]+:[^\s\]]*(?:\s+([^\]]*))?\]", re.I)
SENT_RE = re.compile(r"[.!?…\n]+")

def wiki_to_text(t: str):
    """Bỏ markup wiki, trả về iterator dòng/câu text."""
    t = REF_RE.sub(" ", t)
    depth = 0  # xoá {{template}} có lồng
    out = []
    i = 0
    while i < len(t):
        if t.startswith("{{", i):
            depth += 1; i += 2
        elif t.startswith("}}", i) and depth:
            depth -= 1; i += 2
        elif depth == 0:
            out.append(t[i]); i += 1
        else:
            i += 1
    t = "".join(out)
    t = LINK_RE.sub(lambda m: m.group(1) or m.group(2), t)
    t = EXT_RE.sub(lambda m: m.group(1) or " ", t)
    t = TAG_RE.sub(" ", t)
    t = re.sub(r"'{2,}", "", t)
    for line in t.split("\n"):
        line = line.strip()
        if not line or line[0] in "*#:|{};!=":
            line = re.sub(r"^[*#:|;\s]+", "", line)  # vẫn giữ chữ sau marker
        if line.startswith("==") or line.startswith("|"):
            continue
        if line:
            yield line

def sentences(path):
    """Stream các câu: iterator list-of-words (đã lowercase, lọc chữ VN)."""
    words_buf = []
    for line in iter_article_text(path):
        pos = 0
        for m in SENT_RE.finditer(line):
            seg = line[pos:m.start()]
            pos = m.end()
            words_buf.extend(tok for tok in seg.split() if tok)
            if words_buf:
                sent = [w.lower() for w in words_buf]
                words_buf = []
                sent = [w for w in sent if is_vi_word(w)]
                if len(sent) >= 2:
                    yield sent
        words_buf.extend(line[pos:].split())

def iter_article_text(path):
    with bz2.open(path, "rt", encoding="utf8", errors="ignore") as f:
        # iterparse trên stream đã giải nén
        for _ev, elem in ET.iterparse(f):
            if elem.tag == NS + "text":
                t = elem.text or ""
                yield t
                elem.clear()
            elif elem.tag == NS + "page":
                elem.clear()

def main():
    src, dst = sys.argv[1], sys.argv[2]
    rng = Random(42)
    eval_sents = []

    # --- lượt 1: unigram + BOS ---
    uni = Counter()
    bos = Counter()
    npages_sent = 0
    for sent in sentences(src):
        npages_sent += 1
        if npages_sent % EVAL_EVERY == 0 and len(eval_sents) < EVAL_MAX:
            eval_sents.append(" ".join(sent))
            continue
        uni.update(sent)
        bos[sent[0]] += 1
    vocab = {w for w, c in uni.most_common() if c >= MIN_UNI and keep_token(w)}
    vocab = set(sorted(vocab, key=lambda w: -uni[w])[:MAX_VOCAB])
    print(f"lượt 1: {npages_sent} câu, {len(uni)} unigram -> vocab {len(vocab)}", file=sys.stderr)

    # --- lượt 2: bigram + trigram trên vocab ---
    bi = Counter()
    tri = Counter()
    for sent in sentences(src):
        s = [w for w in sent if w in vocab]
        for i in range(len(s) - 1):
            bi[(s[i], s[i + 1])] += 1
        for i in range(len(s) - 2):
            if s[i] in vocab and uni[s[i]] >= 200:  # trigram chỉ cho đầu từ phổ
                tri[(s[i], s[i + 1], s[i + 2])] += 1
    bi = Counter({k: c for k, c in bi.items() if c >= MIN_BI})
    tri = Counter({k: c for k, c in tri.items() if c >= MIN_TRI})
    print(f"lượt 2: {len(bi)} bigram, {len(tri)} trigram", file=sys.stderr)

    # top-K theo prev
    by_prev = {}
    for (p, n), c in bi.most_common():
        slot = by_prev.setdefault(p, [])
        if len(slot) < TOP_BI:
            slot.append((n, c))
    by_pair = {}
    for (p1, p2, n), c in tri.most_common():
        slot = by_pair.setdefault((p1, p2), [])
        if len(slot) < TOP_TRI:
            slot.append((n, c))

    with open(dst, "w", encoding="utf8") as f:
        f.write("# vi-model v1 | nguồn: Wikipedia tiếng Việt (CC BY-SA 4.0) | thống kê n-gram\n")
        for w in sorted(vocab, key=lambda w: -uni[w]):
            f.write(f"u\t{w}\t{uni[w]}\n")
        seen = set()
        for w in BOS_SEED:
            if w in vocab:
                f.write(f"s\t{w}\t0\n"); seen.add(w)
        for w, c in bos.most_common(TOP_BOS):
            if w in vocab and w not in seen:
                f.write(f"s\t{w}\t{c}\n")
        for p, lst in sorted(by_prev.items()):
            for n, c in lst:
                f.write(f"b\t{p}\t{n}\t{c}\n")
        for (p1, p2), lst in sorted(by_pair.items()):
            for n, c in lst:
                f.write(f"t\t{p1}\t{p2}\t{n}\t{c}\n")

    with open("tools/eval_corpus.txt", "w", encoding="utf8") as f:
        f.write("\n".join(eval_sents) + "\n")
    print(f"ghi {dst} + tools/eval_corpus.txt ({len(eval_sents)} câu eval)", file=sys.stderr)

if __name__ == "__main__":
    main()
