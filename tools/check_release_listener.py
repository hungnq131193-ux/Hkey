#!/usr/bin/env python3
import re
import subprocess
import sys
import tempfile
import zipfile

REPO = "Lcom/hkey/app/settings/SettingsRepository;"
LISTENER = "Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;"
CTOR = "(Landroid/content/SharedPreferences;)V"
REGISTER = "registerOnSharedPreferenceChangeListener"


def fail(msg):
    print("FAIL: " + msg, file=sys.stderr)
    sys.exit(1)


def main():
    if len(sys.argv) != 3:
        fail("usage: check_release_listener.py <apk> <dexdump>")
    apk, dexdump = sys.argv[1], sys.argv[2]
    with tempfile.TemporaryDirectory() as td:
        zf = zipfile.ZipFile(apk)
        dexes = sorted(n for n in zf.namelist()
                       if re.fullmatch(r"classes\d*\.dex", n))
        if not dexes:
            fail(f"no classes*.dex in {apk}")
        in_repo = in_fields = False
        have_field = in_ctor = iput = reg = ctor_seen = False
        pending = None
        for n in dexes:
            p = f"{td}/{n}"
            with open(p, "wb") as f:
                f.write(zf.read(n))
            proc = subprocess.Popen(
                [dexdump, "-d", p], stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL, text=True, errors="replace")
            for line in proc.stdout:
                m = re.match(r"  Class descriptor  : '(L[^']+;)'", line)
                if m:
                    in_repo = m.group(1) == REPO
                    in_fields = in_ctor = False
                    continue
                if not in_repo:
                    continue
                sec = re.match(r"\s*(Instance fields|Direct methods|"
                               r"Virtual methods|Static fields)\s+-", line)
                if line.startswith("  ") and sec:
                    in_fields = sec.group(1) == "Instance fields"
                    in_ctor = False
                    pending = None
                    continue
                if in_fields:
                    nm = re.search(r"name +: '(\w+)'", line)
                    if nm:
                        pending = nm.group(1)
                        continue
                    ty = re.search(r"type +: '([^']+)'", line)
                    if ty and pending == "listener" and ty.group(1) == LISTENER:
                        have_field = True
                    continue
                nm = re.search(r"name +: '([^']+)'", line)
                if nm:
                    in_ctor = False
                    continue
                ty = re.search(r"type +: '([^']+)'", line)
                if ty and ty.group(1) == CTOR and not ctor_seen:
                    in_ctor = ctor_seen = True
                    continue
                if in_ctor and "|" in line:
                    ins = line.split("|", 1)[1]
                    if "iput-object" in ins and ".listener:" + LISTENER in ins \
                            and not reg:
                        iput = True
                    if REGISTER in ins:
                        reg = True
            if proc.wait() != 0:
                fail(f"dexdump failed on {n} in {apk}")
    if not have_field:
        fail(f"{REPO} lacks 'listener' field of type "
             "OnSharedPreferenceChangeListener (R8 stripped it)")
    if not ctor_seen:
        fail(f"{REPO} has no (SharedPreferences) constructor")
    if not reg:
        fail("constructor never calls registerOnSharedPreferenceChangeListener")
    if not iput:
        fail("constructor registers callback without storing it in "
             "'listener' field (weak-reference regression)")
    print(f"OK: {REPO} retains listener field and stores callback "
          "before registering")


if __name__ == "__main__":
    main()
