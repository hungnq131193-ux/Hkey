#!/usr/bin/env python3
"""Làm sạch vi_dict.txt: bỏ mục không phải âm tiết VN (tiếng Anh, viết tắt
rác, markup), giữ một whitelist từ vay/ngữ công nghệ hay gõ.

Dùng:  python3 tools/clean_dict.py
"""
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build_model import is_vi_syllable  # cùng luật với Kotlin ViSyllable

# Từ vay / thuật ngữ công nghệ hợp lệ để gợi ý, dù không phải âm tiết VN.
LOAN = set("""
internet wifi link links web website file files video videos audio game
games otp email online offline chat shop fan team fans laptop smartphone
selfie usb bluetooth camera podcast stream livestream code bug bugs login
logout admin android iphone ios ipad google youtube facebook zalo tiktok
telegram messenger instagram whatsapp chrome firefox safari scan card
visa mastercard checkin voucher freeship cancel order sale sales flash
deal deals like share comment inbox story status block report spam virus
hack hacker bot ai ml it dev app apps apk api ui ux cv hr pr ads banner
pixel drone lcd led oled cpu gpu ram ssd hdd psd pdf doc docx xls xlsx
zip rar png jpg jpeg gif mp3 mp4 wav mkv avi html css js php java python
kotlin swift sql nosql json xml yaml csv btc eth usd eur vnd covid flu
hiv aids dna gen chip data cloud backup server client modem router
printer photoshop office word excel powerpoint outlook gmail hotmail
yahoo apple microsoft windows linux macos macbook dell hp asus lenovo
acer samsung oppo xiaomi vivo nokia sony lg panasonic toyota honda
""".split())

def main():
    src = "app/src/main/res/raw/vi_dict.txt"
    words = [l.strip() for l in open(src, encoding="utf8") if l.strip()]
    kept, dropped = [], []
    seen = set()
    for w in words:
        if w in seen:
            continue
        if is_vi_syllable(w) or w in LOAN:
            kept.append(w)
            seen.add(w)
        else:
            dropped.append(w)
    with open(src, "w", encoding="utf8") as f:
        f.write("\n".join(kept) + "\n")
    print(f"giữ {len(kept)}, bỏ {len(dropped)}")
    print("bỏ mẫu:", " ".join(dropped[:40]))

if __name__ == "__main__":
    main()
