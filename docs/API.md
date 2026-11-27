# TUIBook Command and Source API

Run `tuibook` with JDK 21. The CLI currently supports local library management, public source search, downloads, and source configuration. The [product requirements](PRODUCT_REQUIREMENTS.md) describe the terminal reader design.

## Command reference

Arguments in angle brackets are required. Square brackets indicate optional arguments. Add `--json` for structured output from the commands below.

| Command | Behavior |
| --- | --- |
| `tuibook list [--plain] [--favorites] [--query "<title>"]` | List local books. Text output contains the ID, title, status, and favorite marker. |
| `tuibook add "<file>" [--title "<title>"]` | Copy a local EPUB or TXT file into the library. The filename is the default title. Reimporting the same format and content returns the existing book. |
| `tuibook favorite add "<book ID or title>"` | Mark a book as a favorite. |
| `tuibook favorite remove "<book ID or title>"` | Clear the favorite marker. |
| `tuibook status "<book ID or title>" <status>` | Set the status to `unread`, `reading`, or `finished`. |
| `tuibook update "<book ID or title>" [--title "<new title>"]` | Supply `--title` to rename a book. Omitting it refreshes the content of a downloaded book and preserves local metadata. |
| `tuibook remove "<book ID or title>"` | Remove a book from the list and move the managed copy and metadata to local `trash/`. The original import file remains available at its original path. |
| `tuibook search "<query>" [--source <source ID>]` | Search enabled sources, optionally limiting the query to one source. |
| `tuibook download "<source book ID>"` | Download a result into the local library. An existing download is reused. |
| `tuibook source list` | List built-in and external sources and their status. |
| `tuibook source add "<config file>"` | Import the configuration for a public OPDS 1 EPUB acquisition catalog. |
| `tuibook source enable <source ID>` | Enable a source. |
| `tuibook source disable <source ID>` | Disable a source. |
| `tuibook source remove <source ID>` | Remove an external configuration or mark a built-in source as removed. Downloaded books remain available locally. |
| `tuibook source restore <source ID>` | Restore and enable a removed built-in source. |

Library operations accept an exact title or a local `bookId`. If multiple books have the same title, use `list --query` to find the intended ID. Online search returns a `sourceBookId` for use with `download`.

## Book sources

| ID | Catalog | Download format |
| --- | --- | --- |
| `wikisource-zh` | [Chinese Wikisource](https://zh.wikisource.org/) via MediaWiki and [WS Export](https://wikisource.org/wiki/Wikisource:WS_Export/en) | EPUB |
| `wikisource-en` | [English Wikisource](https://en.wikisource.org/) via MediaWiki and WS Export | EPUB |
| `wolne-lektury` | [Wolne Lektury public API](https://eink.wolnelektury.pl/api/) with title and author search | EPUB |

Wikisource searches live catalogs. WS Export determines which chapters and related pages appear in the EPUB. Wolne Lektury caches the catalog in process and retrieves the EPUB URL from the book detail endpoint.

For an external [OPDS 1 catalog](https://specs.opds.io/opds-1.2), save the acquisition feed URL in `textos.json`:

```json
{
  "id": "textos",
  "name": "textos.info",
  "catalogUrl": "https://www.textos.info/populares.atom"
}
```

```text
tuibook source add textos.json
tuibook search "Meditaciones" --source textos --json
```

Search covers the entries returned by that feed URL. For a navigation catalog, select a subsection with direct EPUB acquisition links and use its feed URL.

If the configuration file cannot be decoded, `source add` exits with code `1`, reports `INVALID_SOURCE_CONFIG`, and names the file.

To integrate another public catalog, implement `BookSource.search` and `BookSource.download` in `src/main/kotlin/tuibook/source/`, then register the adapter in `SourceRegistry`. Install with `./gradlew installDist` and run the launcher in `build/install/tuibook/bin/`.

## JSON output

`tuibook search "The Raven" --source wikisource-en --json` returns a JSON object with `results`. Each result contains `sourceBookId`, `sourceId`, `title`, and `sourceUrl`. A successful search with no matches returns `{"results":[]}`. Partial source failures appear under `sourceErrors`.

Pass the selected ID as returned by search:

```text
tuibook download "wikisource-en:4031" --json
```

The download output is a local book record with `bookId`, `title`, `sourceBookId`, `sourceUrl`, `format`, `path`, `favorite`, and `status`. `list --json` returns an array of these records. An imported book has `null` source fields.

With `--json`, the command writes one JSON value to standard output. Exit code `0` indicates success, `2` indicates an invalid argument, and `1` indicates another failure. Failures include `error.code` for automation and `error.message` for people.

## Planned terminal routes

- `tuibook "<title>"` resolves a local title or opens a source selection. For a title that matches a command name, use `tuibook -- "<title>"`.
- `tuibook completion powershell` will emit the registration script for PSReadLine `MenuComplete`.
- The registration script will call `tuibook complete title [--prefix "<prefix>"]`. The command will return a JSON array of local titles. PowerShell will display the candidates and quote the selected title.
