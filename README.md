# PokeLite

PokeLite is a 100% Java 17 desktop shell that launches the official PokeMMO client and presents it inside a RuneLite-inspired interface.

## Current milestone

The current client includes:

- a dark Swing desktop shell with a central game area;
- a right-side RuneLite-style navigation rail;
- collapsible Plugins, Settings, and Logs panels;
- persistent plugin enable/disable state;
- classpath and external JAR plugin discovery through `ServiceLoader`;
- official PokeMMO process launching from `poke/`;
- Windows child-window hosting and automatic resize through JNA;
- safe separate-window fallback when embedding is unavailable.

## Required local layout

PokeMMO files are intentionally excluded from Git.

```text
PokeLite/
├── build.gradle
├── settings.gradle
├── src/
├── plugins/             optional external PokeLite plugin JARs
└── poke/
    ├── PokeMMO.exe
    ├── data/
    ├── roms/
    └── other official PokeMMO files
```

## Run from IntelliJ IDEA

1. Open the repository as a Gradle project.
2. Use Java 17 as the project SDK and Gradle JVM.
3. Run the Gradle `run` task, or create an Application configuration with:

```text
Main class: dev.kspog.pokelite.PokeLite
Working directory: repository root
Module classpath: PokeLite.main
```

## Run with Gradle

```bash
gradle run
```

## External plugin contract

External plugin JARs go in `plugins/` and implement:

```text
dev.kspog.pokelite.plugin.PokeLitePlugin
```

Each plugin JAR must provide the matching `META-INF/services` provider entry.

## Status

This is still an early integration milestone. It provides the client shell and native Windows hosting layer, but it does not yet expose PokeMMO game state, game events, widgets, or automation APIs.

No PokeMMO game files, ROMs, credentials, or proprietary client assets are included.
