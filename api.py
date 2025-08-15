import os
import json
import mwparserfromhell as mw
import traceback
import urllib.request
import urllib.parse
from typing import *

import util

use_cache: bool = True
user_agent: dict[str, str] = {"User-Agent": "Not Enough Runes Scraper/1.0 (+HannahRyanster@gmail.com)"}


def get_wiki_api(args: dict[str, str], continue_key: str) -> Iterator[any]:
    args["format"] = "json"
    while True:
        url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(args)
        print("Grabbing " + url)
        with urllib.request.urlopen(urllib.request.Request(url, headers=user_agent)) as raw:
            js = json.load(raw)

        yield js
        if "continue" in js:
            args[continue_key] = js["continue"][continue_key]
        else:
            return


def get_wiki_ask_api(args: dict[str, str]) -> Iterator[any]:
    args["format"] = "json"
    args["query"] += "|limit=500|offset=0"
    offset = 0

    while True:
        # if offset > 5000:
        #     print("Offset beyond 5000. Aborting ask call")
        #     return
        args["query"] = args["query"][0:(args["query"].index("|offset=") + 8)] + str(offset)
        url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(args)
        print("Grabbing " + url)
        with urllib.request.urlopen(urllib.request.Request(url, headers=user_agent)) as raw:
            js = json.load(raw)

        yield js
        if len(js["query"]["results"]) < 500:
            return
        else:
            offset += 500


def query_category(category_name: str) -> dict[str, dict[str, str]]:
    """
    query_category returns a dict of page title to page wikitext
    you can then use mwparserfromhell to parse the wikitext into
    an ast
    """
    cache_file_name = category_name + ".cache.json"
    if use_cache and os.path.isfile(cache_file_name):
        with open(cache_file_name, "r") as fi:
            return json.load(fi)

    pageids = []
    for res in get_wiki_api(
            {
                "action": "query",
                "list": "categorymembers",
                "cmlimit": "500",
                "cmtitle": "Category:" + category_name,
            }, "cmcontinue"):

        for page in res["query"]["categorymembers"]:
            pageids.append(str(page["pageid"]))

    pages = {}
    for i in range(0, len(pageids), 50):
        for res in get_wiki_api(
                {
                    "action": "query",
                    "prop": "revisions|info",
                    "rvprop": "content",
                    "inprop": "url",
                    "pageids": "|".join(pageids[i:i + 50]),
                }, "rvcontinue"):
            for page_id, page in res["query"]["pages"].items():
                pages[page["title"]] = {
                    "page": page["revisions"][0]["*"],
                    "url": page["fullurl"]
                }

    with open(cache_file_name, "w+") as fi:
        json.dump(pages, fi)

    return pages


def ask_category_production(category_name: str) -> List[dict]:
    """
    query_category_production returns a list of all Production JSON
    properties in a given category
    """
    cache_file_name = category_name + "-production" + ".cache.json"
    if use_cache and os.path.isfile(cache_file_name):
        with open(cache_file_name, "r") as fi:
            return json.load(fi)

    items = []
    for res in get_wiki_ask_api(
            {
                "action": "ask",
                "query": "[[Category:" + category_name + "]][[Production JSON::+]]|?Production JSON",
            }):

        for item in res["query"]["results"]:
            for recipe in res["query"]["results"][item]["printouts"]["Production JSON"]:
                items.append(json.loads(str(recipe)))

    with open(cache_file_name, "w+") as fi:
        json.dump(items, fi)

    return items


def ask_category_drop_sources(category_name: str) -> Dict[str, object]:
    """
    ask_category_shop_items returns a list of all Production JSON
    properties in a given category
    """
    cache_file_name = category_name + "-drop-sources" + ".cache.json"
    if use_cache and os.path.isfile(cache_file_name):
        with open(cache_file_name, "r") as fi:
            return json.load(fi)

    item_pages = query_category(category_name)
    items = []
    drop_items = {}
    for name, obj in item_pages.items():
        page = obj["page"]
        if name.startswith("Category:") or name == "<!--None-->" or name.lower() == "null":
            continue

        try:
            code = mw.parse(page, skip_style_tags=True)

            for (vid, version) in util.each_version("Infobox Item", code):
                if "name" in version:
                    items.append(version["name"].strip())
                else:
                    continue


        except (KeyboardInterrupt, SystemExit):
            raise
        except:
            print("Item {} failed:".format(name))
            traceback.print_exc()

    for i in range(0, len(items), 15):
        items_string = "||".join(items[i:i + 15])
        query = "[[Dropped item::" + items_string + "]]|?Drop JSON|?Dropped item"

        for res in get_wiki_ask_api(
                {
                    "action": "ask",
                    "query": query
                }):

            for item in res["query"]["results"]:
                for drop in res["query"]["results"][item]["printouts"]["Drop JSON"]:
                    drop_json = json.loads(str(drop))
                    if drop_json["Dropped item"] in drop_items:
                        drop_items[drop_json["Dropped item"]]["results"].append(drop_json)
                    else:
                        drop_items[drop_json["Dropped item"]] = {"results": [drop_json]}

    with open(cache_file_name, "w+") as fi:
        json.dump(drop_items, fi)

    return drop_items

