# TUIBook

TUIBook is a terminal reading-list manager. It imports local EPUB and TXT files, finds and downloads public ebooks, and tracks favorites and reading status in one local library.

## Build and run

Install JDK 21. Use the Gradle Wrapper from the project root.

```bash
./gradlew build installDist
./build/install/tuibook/bin/tuibook list
```

On Windows PowerShell:

```powershell
.\gradlew.bat build installDist
.\build\install\tuibook\bin\tuibook.bat list
```

Once the launcher is on your `PATH`, try these commands:

```text
tuibook add "./story.epub" --title "My Story"
tuibook favorite add "My Story"
tuibook status "My Story" reading
tuibook list --favorites
tuibook search "The Raven" --source wikisource-en --json
```

Book files and metadata are stored under `~/.tuibook`. Set `TUIBOOK_DATA_DIR` to use a different location. Removing a book moves the managed copy into `trash/` within that directory.

## Verification

The `build` task runs the tests and Detekt static analysis and generates a JaCoCo coverage report.
Find the HTML reports under `build/reports`:

- Tests at `tests/test/index.html`
- Coverage at `jacoco/test/html/index.html`
- Static analysis at `detekt/detekt.html`

GitHub Actions runs the same build on Linux and Windows for pull requests and pushes to `main`.
Download the verification reports from the workflow run.

## Project layout

- `src/main/kotlin/tuibook/cli/` handles commands and renders text or JSON.
- `LocalLibrary` manages the reading list. `LibraryStorage` handles file storage and locking.
- `OnlineLibrary` coordinates downloads and source settings through the `BookSource` adapters.

See the [command and source API](docs/API.md) for configuration, response formats, and adapter extension points. The [product requirements](docs/PRODUCT_REQUIREMENTS.md) also describe the planned terminal reading interface.

TUIBook uses Kotlin/JVM, Gradle, and [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization). The [API documentation](docs/API.md#book-sources) credits the book catalog and download services. OpenAI Codex assisted with portions of the implementation and documentation.

See [Contributing](CONTRIBUTING.md) for the pull request workflow.
