# -*- coding: utf-8 -*-
"""Contrôle des fichiers de traduction.

- clés présentes dans values/ mais absentes d'une langue (et l'inverse),
- tableaux de longueur différente,
- paramètres de format (%1$s, %2$d…) qui ne correspondent pas,
- valeurs identiques au français, signe d'une traduction oubliée.
"""
import io
import os
import re
import sys

RES = r"C:\DEV\PlantInfo\app\src\main\res"
LANGS = ["fr", "de", "it", "es"]

STRING = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
ARRAY = re.compile(r'<string-array name="([^"]+)">(.*?)</string-array>', re.S)
ITEM = re.compile(r"<item>(.*?)</item>", re.S)
FORMAT = re.compile(r"%\d+\$[sd]")


def load(folder):
    path = os.path.join(RES, folder, "strings.xml")
    if not os.path.exists(path):
        return None, None
    text = io.open(path, encoding="utf-8").read()
    strings = {}
    for name, attrs, value in STRING.findall(text):
        if 'translatable="false"' in attrs:
            continue
        strings[name] = value
    arrays = {name: ITEM.findall(body) for name, body in ARRAY.findall(text)}
    return strings, arrays


def main():
    base_s, base_a = load("values")
    problems = 0
    for lang in LANGS:
        folder = "values-" + lang
        s, a = load(folder)
        if s is None:
            print("MANQUANT : %s" % folder)
            problems += 1
            continue
        missing = sorted(set(base_s) - set(s))
        extra = sorted(set(s) - set(base_s))
        if missing:
            print("%s : %d clés manquantes -> %s" % (folder, len(missing), ", ".join(missing[:8])))
            problems += len(missing)
        if extra:
            print("%s : %d clés en trop -> %s" % (folder, len(extra), ", ".join(extra[:8])))
            problems += len(extra)
        for name in sorted(set(base_s) & set(s)):
            if sorted(FORMAT.findall(base_s[name])) != sorted(FORMAT.findall(s[name])):
                print("%s : paramètres de format différents pour %s" % (folder, name))
                problems += 1
        missing_a = sorted(set(base_a) - set(a))
        if missing_a:
            print("%s : tableaux manquants -> %s" % (folder, ", ".join(missing_a)))
            problems += len(missing_a)
        for name in sorted(set(base_a) & set(a)):
            if len(base_a[name]) != len(a[name]):
                print("%s : %s a %d entrées au lieu de %d"
                      % (folder, name, len(a[name]), len(base_a[name])))
                problems += 1
        if lang != "fr":
            fr_s, _ = load("values-fr")
            same = [n for n in set(s) & set(fr_s) if s[n] == fr_s[n] and len(s[n]) > 25]
            if same:
                print("%s : %d valeurs identiques au français (traduction oubliée ?) -> %s"
                      % (folder, len(same), ", ".join(sorted(same)[:8])))
                problems += len(same)
    print("---")
    print("OK" if problems == 0 else "%d problème(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
