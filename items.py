import hashlib
import json
import os
import re
import traceback
from operator import itemgetter
from typing import Dict, List
from mwparserfromhell.nodes import Template

import api
import mwparserfromhell as mw

import util

output_dir = "output/"


def get_production():
    print("Scraping recipes")
    item_production = api.ask_category_production("Items")
    name = "items-production.json"
    min_name = "items-production.min.json"

    for recipe in item_production:
        recipe["output"].pop("cost")
        for material in recipe["materials"]:
            if "#" in material["name"]:
                material["version"] = material["name"].split("#")[1]
                material["name"] = material["name"].split("#")[0]

        if "#" in recipe["output"]["name"]:
            recipe["output"]["version"] = recipe["output"]["name"].split("#")[1]
            recipe["output"]["name"] = recipe["output"]["name"].split("#")[0]

    sorted_production = sorted(item_production, key=lambda x: x["output"]["name"])

    with open(output_dir + name, "w+") as fi:
        json.dump(sorted_production, fi, indent=2, sort_keys=True)

    with open(output_dir + min_name, "w+") as fi:
        json.dump(sorted_production, fi, separators=(",", ":"), sort_keys=True)


def get_shop_items():
    print("Scraping shops")
    file_name = "items-shopitems.json"
    min_name = "items-shopitems.min.json"

    page_data = api.query_category("Shops")
    page_data.update(api.query_category("Merchants"))

    shop_items = []
    for name, obj in page_data.items():
        page = obj["page"]
        if name.startswith("Category:"):
            continue

        try:
            code = mw.parse(page, skip_style_tags=True)
            store_infobox = code.filter_templates(matches=lambda t: t.name.matches("Infobox Shop"))

            if len(store_infobox) < 1:
                store_infobox = code.filter_templates(matches=lambda t: t.name.matches("Infobox NPC"))
                code.filter_templates()
                if len(store_infobox) < 1:
                    continue

            infobox_data: Dict[str, str] = {}
            for param in store_infobox[0].params:
                infobox_data[param.name.strip()] = param.value.strip()

            raw_page_stores: List[Template] = code.filter_templates(matches=lambda t: t.name.matches("StoreTableHead") or t.name.matches("StoreLine"))
            page_stores= []
            current_store_table = []
            for index, item in enumerate(raw_page_stores):
                if item.name == "StoreTableHead":
                    if index != 0:
                        page_stores.append(current_store_table)
                        current_store_table = []
                    else:
                        current_store_table = []
                current_store_table.append(item)
            page_stores.append(current_store_table)

            if len(page_stores) < 1 or page_stores[0] == []:
                continue

            for page_store in page_stores:
                items = []
                store_table_data: Dict[str, str] = {}
                for param in page_store[0].params:
                    store_table_data[param.name.strip()] = param.value.strip()

                if "smw" in store_table_data:
                    if store_table_data["smw"].lower() == "no":
                        continue

                for store_line in page_store:
                    if store_line.name == "StoreTableHead":
                        continue

                    store_line_data: Dict[str, str] = {}
                    for param in store_line.params:
                        store_line_data[param.name.strip()] = param.value.strip()
                    if "smw" in store_line_data:
                        if store_line_data["smw"].lower() == "no":
                            continue

                    version = ""

                    if "smwname" in store_line_data and "#" in store_line_data["smwname"]:
                        version = store_line_data["smwname"].split("#")[1]


                    shop_item = {
                        "name": store_line_data["name"],
                        "version": version if version else None,
                        "stock": store_line_data["stock"],
                        "currency": store_table_data["currency"] if "currency" in store_table_data else "Coins"
                    }
                    if "sell" in store_line_data:
                        shop_item["sellPrice"] = store_line_data["sell"]
                    if "buy" in store_line_data:
                        shop_item["buyPrice"] = store_line_data["buy"]
                    items.append(shop_item)

                sorted_items = sorted(items, key=itemgetter("name"))

                shop_info = {
                    "name": name + " " + store_table_data["namenotes"] if "namenotes" in store_table_data else name,
                    "location": format_location(infobox_data["location"]) if "location" in infobox_data else "",
                    "isMembers": True if ("members" in infobox_data and infobox_data["members"] == "Yes") else False,
                    "items": sorted_items
                }
                if "sellmultiplier" in store_table_data:
                    shop_info["sellMultiplier"] = store_table_data["sellmultiplier"]
                if "buymultiplier" in store_table_data:
                    shop_info["buyMultiplier"] = store_table_data["buymultiplier"]

                shop_items.append(shop_info)

        except (KeyboardInterrupt, SystemExit):
            raise
        except:
            print("Item {} failed:".format(name))
            traceback.print_exc()

    sorted_shops = sorted(shop_items, key=itemgetter("name"))

    with open(output_dir + file_name, "w+") as fi:
        json.dump(sorted_shops, fi, indent=2, sort_keys=True)

    with open(output_dir + min_name, "w+") as fi:
        json.dump(sorted_shops, fi, separators=(",", ":"), sort_keys=True)


def get_item_spawns():
    print("Scraping item spawns")
    item_pages = api.query_category("Items")
    file_name = "items-spawns.json"
    min_name = "items-spawns.min.json"

    item_spawns = []
    for name, obj in item_pages.items():
        page = obj["page"]
        if ":" in name:
            continue

        try:
            code = mw.parse(page, skip_style_tags=True)
            raw_page_spawns = code.filter_templates(matches=lambda t: t.name.matches("ItemSpawnLine"))
            if len(raw_page_spawns) < 1:
                continue

            spawns = []
            for raw_page_spawn in raw_page_spawns:
                base: Dict[str, str] = {}
                for param in raw_page_spawn.params:
                    base[param.name.strip()] = param.value.strip()

                coords = "?"
                if "1" in base:
                    coords = ""
                    coord_list = base["1"].split(",")
                    for item in coord_list:
                        if ":" not in item:
                            coords += item + ","
                    coords = coords[:-1]

                page_spawn = {
                    "name": base["name"],
                    "location": format_location(base["location"]),
                    "isMembers": True if base["members"] == "Yes" else False,
                    "coords": coords
                }
                spawns.append(page_spawn)

            page_spawns = {
                "group": name,
                "spawns": sorted(spawns, key=itemgetter("location"))
            }

            item_spawns.append(page_spawns)

        except (KeyboardInterrupt, SystemExit):
            raise
        except:
            print("Item {} failed:".format(name))
            traceback.print_exc()

    sorted_spawns = sorted(item_spawns, key=itemgetter("group"))

    with open(output_dir + file_name, "w+") as fi:
        json.dump(sorted_spawns, fi, indent=2, sort_keys=True)

    with open(output_dir + min_name, "w+") as fi:
        json.dump(sorted_spawns, fi, separators=(",", ":"), sort_keys=True)


def get_item_info():
    print("Scraping item info")
    item_pages = api.query_category("Items")
    file_name = "items-info.json"
    min_name = "items-info.min.json"

    regex = r"\{\{.*?\}\}"

    item_info = []
    for name, obj in item_pages.items():
        page = obj["page"]
        url = obj["url"]
        if ":" in name:
            continue

        try:
            code = mw.parse(page, skip_style_tags=True)

            gone = code.filter_templates(matches=lambda t: t.name.matches("Gone"))
            if len(gone) > 0:
                continue

            versions = {}

            for (vid, version) in util.each_version("Infobox Item", code, include_base=True):
                versions[vid] = version

            for (vid, version) in versions.items():
                if len(versions) > 1 and vid == -1:
                    continue

                base: Dict[str, str] = {}
                for param, value in version.items():
                    base[param.strip()] = value.strip()

                if "hist" in base["id"] or "beta" in base["id"] or ("name" in base and base["name"].lower() == "null") or base["id"] == "":
                    continue

                item_id = int(base["id"].split(",")[0])

                obj = {
                    "name": base["name"] if "name" in base else name,
                    "group": name,
                    "version": base["version"] if "version" in base else None,
                    "isMembers": True if base["members"] == "Yes" else False,
                    "isTradeable": True if base["tradeable"] == "Yes" else False,
                    "examineText": re.sub(regex, "", base["examine"]) if "examine" in base and "Clue scroll" not in base["name"] else "",
                    "itemID": item_id,
                    "url": url
                }

                item_info.append(obj)

        except (KeyboardInterrupt, SystemExit):
            raise
        except:
            print("Item {} failed:".format(name))
            traceback.print_exc()

    sorted_info = sorted(item_info, key=itemgetter("itemID"))

    with open(output_dir + file_name, "w+") as fi:
        json.dump(sorted_info, fi, indent=2, sort_keys=True)

    with open(output_dir + min_name, "w+") as fi:
        json.dump(sorted_info, fi, separators=(",", ":"), sort_keys=True)


def get_item_drops():
    print("Scraping drops")
    file_name = "items-drop-sources.json"
    min_name = "items-drop-sources.min.json"
    temp_item_drops = api.ask_category_drop_sources("Items")

    item_drops = []
    for name, results in temp_item_drops.items():
        if len(results["results"]) < 1:
            continue

        drop_sources = []
        try:
            for result in results["results"]:
                result_object = {
                    "source": result["Dropped from"],
                    "quantityLow": result["Quantity Low"] if "Quantity Low" in result else -1,
                    "quantityHigh": result["Quantity High"] if "Quantity High" in result else -1,
                    "rarity": result["Rarity"] if "Rarity" in result else "Unknown",
                    "dropLevel": result["Drop level"] if "Drop level" in result else "",
                    "dropType": result["Drop type"] if "Drop type" in result else ""
                }

                if result_object not in drop_sources:
                    drop_sources.append(result_object)

        except (KeyboardInterrupt, SystemExit):
            raise
        except:
            print("Item {} failed:".format(name))
            traceback.print_exc()

        drop_object = {
            "name": name,
            "dropSources": drop_sources
        }

        item_drops.append(drop_object)

    sorted_drops = sorted(item_drops, key=itemgetter("name"))

    with open(output_dir + file_name, "w+") as fi:
        json.dump(sorted_drops, fi, indent=2, sort_keys=True)

    with open(output_dir + min_name, "w+") as fi:
        json.dump(sorted_drops, fi, separators=(",", ":"), sort_keys=True)


def generate_hashes():
    print("Generating checksums")
    path = os.getcwd() + "/output"
    BLOCKSIZE = 65536
    checksums = {}
    for file_name in os.listdir(path):
        file_path = os.path.join(path, file_name)
        if os.path.isfile(file_path):
            if "min" not in file_name or file_name == "checksums":
                continue

            hasher = hashlib.sha256()
            with open(file_path, 'rb') as afile:
                buf = afile.read(BLOCKSIZE)
                while len(buf) > 0:
                    hasher.update(buf)
                    buf = afile.read(BLOCKSIZE)

            checksum = hasher.hexdigest()
            checksums[file_name] = checksum

    with open(output_dir + "checksums", "w+") as fi:
        json.dump(checksums, fi, indent=2, sort_keys=True)


def format_location(location):
    floor_values = {
        0: "ground floor",
        1: "1st floor",
        2: "2nd floor",
        3: "3rd floor",  # current highest floor
        4: "4th floor",
        5: "5th floor"
    }
    bracket_regex = r"(?<=\[\[).*?(?=\]\])"
    bracket_matches = re.finditer(bracket_regex, location, re.MULTILINE)
    for matchNum, match in enumerate(bracket_matches, start=1):
        parts = match.group().split("|")
        if len(parts) > 1:
            part = parts[1]
        else:
            part = parts[0]
        location = location.replace(match.group(), part).replace("[", "").replace("]", "")

    brace_regex = r"(\{\{).*?(\d+)(\}\})"
    brace_matches = re.finditer(brace_regex, location, re.MULTILINE)
    for matchNum, match in enumerate(brace_matches, start=1):
        floor = int(match.group(2))
        location = location.replace(match.group(), floor_values.get(floor))

    return location


def run():
    print("running")
    get_production()
    get_item_spawns()
    get_shop_items()
    get_item_info()
    get_item_drops()
    generate_hashes()
