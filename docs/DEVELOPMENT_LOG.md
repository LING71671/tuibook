# Development log

## 2026-11-27

- Rebuilt the repository from a Kotlin and Gradle scaffold. Existing local code was migrated through focused pull requests for the reading list and public book sources. This allowed each capability to be built and checked separately.
- Added a local catalog fixture to verify that search results survive a source failure, that a selected result can be downloaded, and that source settings persist between invocations.
- Made the Gradle build the common check for contributors and CI. The wrapper and dependency locks stabilize the build inputs, and Detekt findings can be resolved before opening a pull request. JaCoCo helped identify the source paths that needed tests.
- Started versioned distributions at 0.1.0. CI saves the same archive generated locally so it can be installed and exercised without Gradle.
