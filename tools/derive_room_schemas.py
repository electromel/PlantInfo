#!/usr/bin/env python3
"""Reconstruit les schémas Room des versions 1 à 8 à partir du schéma exporté de la version courante.

Pourquoi : `exportSchema` n'a été activé qu'à la version 9. Le test de migration instrumenté
(`app/src/androidTest/.../MigrationTest.kt`) a besoin d'un schéma de départ pour chaque version qu'il
veut migrer, or les versions 1 à 8 n'ont jamais été exportées.

Comment : toutes les migrations de `PlantInfoDatabase` sont **additives** (`ALTER TABLE … ADD COLUMN`
ou `CREATE TABLE`). Le schéma de la version N-1 est donc celui de la version N privé de ce que la
migration (N-1 → N) ajoute. La table ci-dessous reprend ces ajouts ; elle doit rester alignée sur les
migrations de `PlantInfoDatabase.kt`.

Ce que ces fichiers valent : le schéma de **départ** est reconstruit (son `identityHash` est fictif,
dérivé du numéro de version) ; le schéma d'**arrivée** (9.json, puis les suivants) est celui que Room
génère réellement. Le test vérifie que la chaîne de migrations produit exactement ce dernier — c'est
la panne qu'on cherche : une migration oubliée ou fausse.

Usage : python tools/derive_room_schemas.py
(à relancer si une migration est ajoutée ; ne touche jamais au schéma de la version courante).
"""
import hashlib
import json
import re
from pathlib import Path

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "app" / "schemas" / "ch.electromel.plantinfo.data.db.PlantInfoDatabase"

# Version -> ce que la migration (version-1 -> version) ajoute. Source : PlantInfoDatabase.kt.
ADDED_IN = {
    9: {"identifications": ["photoSuggestionsJson"]},
    8: {"identifications": ["propagationJson"]},
    7: {"identifications": ["usageModel", "usageInputTokens", "usageOutputTokens"]},
    6: {"identifications": ["careCalendarJson", "usesJson", "symbolism"]},
    5: {"identifications": ["matureHeight", "matureDiameter", "timeToMaturity", "gbifKey", "iucnCategory"]},
    4: {"identifications": ["edible", "toxic", "edibilityNote"]},
    3: {"identifications": ["userConfirmed"]},
    2: {"__tables__": ["species_range_cache"]},
}


def current_version() -> int:
    return max(int(p.stem) for p in SCHEMA_DIR.glob("*.json") if p.stem.isdigit() and not is_derived(p))


def is_derived(path: Path) -> bool:
    data = json.loads(path.read_text(encoding="utf8"))
    return data["database"].get("_derivedBy") is not None


def drop_column(create_sql: str, column: str) -> str:
    # « , `col` TYPE [NOT NULL] [DEFAULT …] » : une colonne ajoutée par ALTER n'est jamais la clé.
    pattern = r",\s*`%s`\s+[A-Z]+(?:\s+NOT NULL)?(?:\s+DEFAULT\s+\S+)?" % re.escape(column)
    result, count = re.subn(pattern, "", create_sql)
    if count != 1:
        raise SystemExit(f"colonne {column} introuvable dans : {create_sql}")
    return result


def main() -> None:
    latest = current_version()
    schema = json.loads((SCHEMA_DIR / f"{latest}.json").read_text(encoding="utf8"))
    for version in range(latest, 1, -1):
        added = ADDED_IN.get(version)
        if added is None:
            raise SystemExit(f"ADDED_IN ne décrit pas la migration vers la version {version}")
        db = schema["database"]
        db["entities"] = [e for e in db["entities"] if e["tableName"] not in added.get("__tables__", [])]
        for entity in db["entities"]:
            for column in added.get(entity["tableName"], []):
                entity["fields"] = [f for f in entity["fields"] if f["columnName"] != column]
                entity["createSql"] = drop_column(entity["createSql"], column)
        db["version"] = version - 1
        fake_hash = hashlib.md5(f"derived-v{version - 1}".encode()).hexdigest()
        db["identityHash"] = fake_hash
        db["setupQueries"] = [
            q if "INSERT OR REPLACE" not in q else
            f"INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '{fake_hash}')"
            for q in db["setupQueries"]
        ]
        db["_derivedBy"] = "tools/derive_room_schemas.py (schéma de départ reconstruit, hash fictif)"
        out = SCHEMA_DIR / f"{version - 1}.json"
        out.write_text(json.dumps(schema, indent=2, ensure_ascii=False) + "\n", encoding="utf8")
        print(f"écrit {out.name}")


if __name__ == "__main__":
    main()
