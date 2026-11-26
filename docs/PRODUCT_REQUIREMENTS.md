# TUIBook Product Requirements

TUIBook is an installable terminal reading-list manager built with Kotlin/JVM and the Gradle Wrapper. The CLI and planned TUI share the same book and source operations.

## Reading list

- Import local EPUB and TXT files. Add downloaded EPUBs to the same local library.
- List and filter books, manage favorites, and mark books as unread, reading, or finished.
- Rename entries or refresh downloaded text from its source. Move removed books into the local trash directory.
- Persist library entries, book files, and source settings across sessions.

## Book sources

- Search and download from public built-in catalogs. Users can disable, remove, or restore each built-in source.
- Import an external public EPUB acquisition catalog from an OPDS 1 configuration file. Add adapters for other source APIs through the shared `BookSource` interface.
- Expose JSON search and download responses so scripts and AI assistants can find and download a book without interactive prompts.

## Terminal reading design

- `tuibook list` will offer keyboard navigation through the library. `tuibook list --plain` explicitly selects text output.
- `tuibook "The Raven"` will resolve the title against the local library first. Source results can be selected for download when no local match is available.
- In PowerShell, pressing Tab inside the quoted title will display matching local titles under the command. The user can highlight a candidate and insert it into the command line.
- Reading uses the full terminal pane, reflows with terminal width, and saves the reading position. File parsing, pagination, and persistent reading preferences are part of the reader design.

See the [command and source API](API.md) for the implemented commands and the planned interactive routes.
