import os
import json
import urllib.request
import urllib.parse
from typing import *

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


def get_wiki_bucket_api(query: str, order_by: str) -> Iterator[any]:
    args = {"action": "bucket", "format": "json", "query": query}
    offset = 0

    while True:
        args["query"] = f'{query}.limit(500).offset({offset}).orderBy("{order_by}", "asc").run()'
        url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(args) + "&formatversion=2"
        print("Grabbing " + url)
        with urllib.request.urlopen(urllib.request.Request(url, headers=user_agent)) as raw:
            js = json.load(raw)

        yield js
        if len(js["bucket"]) < 500:
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


def bucket_category_production(category_name: str) -> List[dict]:
    """
    bucket_category_production returns a list of all Production JSON
    properties in a given category
    """
    cache_file_name = category_name + "-production" + ".cache.json"
    if use_cache and os.path.isfile(cache_file_name):
        with open(cache_file_name, "r") as fi:
            return json.load(fi)

    items = []
    for res in get_wiki_bucket_api(
            f'bucket("recipe").select("production_json","page_name").where({{"Category:{category_name}"}},{{"source_template",'
            f'"recipe"}})',
            "production_json"):

        for item in res["bucket"]:
            items.append(json.loads(str(item["production_json"])))

    with open(cache_file_name, "w+") as fi:
        json.dump(items, fi)

    return items


def bucket_category_drop_sources(category_name: str) -> Dict[str, object]:
    """
    bucket_category_drop_sources returns a list of all Dropsline
    properties in a given category
    """
    cache_file_name = category_name + "-drop-sources" + ".cache.json"
    if use_cache and os.path.isfile(cache_file_name):
        with open(cache_file_name, "r") as fi:
            return json.load(fi)

    drop_items = {}

    query = f'bucket("dropsline").select("drop_json","item_name").where({{"rare_drop_table", false}})'

    for res in get_wiki_bucket_api(query, "item_name"):
        for item in res["bucket"]:
            drop_json = json.loads(str(item["drop_json"]))
            if drop_json["Dropped item"] in drop_items:
                drop_items[drop_json["Dropped item"]]["results"].append(drop_json)
            else:
                drop_items[drop_json["Dropped item"]] = {"results": [drop_json]}

    with open(cache_file_name, "w+") as fi:
        json.dump(drop_items, fi)

    return drop_items


def bucket_item_infobox() -> list[dict]:
    """
    bucket_item_infobox returns a list of all Items in the
    infobox_item bucket
    """
    cache_file_name = "items-infobox" + ".cache.json"
    if use_cache and os.path.isfile(cache_file_name):
        with open(cache_file_name, "r") as fi:
            return json.load(fi)

    items = []

    query = (f'bucket("infobox_item").select("page_name", "page_name_sub", "item_name", "version_anchor", '
             f'"default_version", "is_members_only", "tradeable", "examine", "item_id").where({{bucket.Not('
             f'"Category:Pages using information from game APIs or cache")}},{{bucket.Not("Category:Interface '
             f'items")}},{{bucket.Not("Category:Discontinued content")}},{{bucket.Not("Category:Pages with null '
             f'name")}})')

    for res in get_wiki_bucket_api(query, "item_id"):
        for item in res["bucket"]:
            if "item_id" in item:
                items.append(item)

    with open(cache_file_name, "w+") as fi:
        json.dump(items, fi)

    return items
