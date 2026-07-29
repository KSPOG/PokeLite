# PokeLite

PokeLite is a 100% Java 17 desktop shell that launches the official PokeMMO client and presents it inside a RuneLite-inspired interface.

## Current milestone

The client currently includes:

- a dark Swing desktop shell with a central embedded game area;
- a right-side navigation rail with vector Poké Ball, Plugins, Game Data, Settings, and Logs icons;
- persistent plugin enable/disable state;
- classpath and external JAR plugin discovery through `ServiceLoader`;
- a public read-only `ClientApi`, `PluginContext`, event bus, typed client events, and capability model;
- replaceable `GameDataProvider` support;
- an optional calibrated screen-OCR provider for Money and Experience;
- official PokeMMO process launching from `poke/`;
- Windows child-window hosting and automatic resize through JNA.

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

## Plugin API

External plugin JARs go in `plugins/` and implement:

```text
dev.kspog.pokelite.plugin.PokeLitePlugin
```

Each plugin JAR must provide a matching service-provider file:

```text
META-INF/services/dev.kspog.pokelite.plugin.PokeLitePlugin
```

Plugins receive their services during initialization:

```java
public final class SessionPlugin implements PokeLitePlugin {
    private PluginContext context;
    private EventBus.Subscription moneySubscription;

    @Override
    public void initialize(PluginContext context) {
        this.context = context;
        this.moneySubscription = context.events().subscribe(
            ClientEvents.MoneyChanged.class,
            event -> context.logger(getId()).info(
                "Money changed: " + event.previous() + " -> " + event.current()
            )
        );
    }

    @Override
    public void onDisable() {
        if (moneySubscription != null) {
            moneySubscription.close();
        }
    }
}
```

Plugins should check `ClientApi.supports(ClientCapability)` before using optional data. This allows future official, log-based, or otherwise authorized providers to replace OCR without requiring plugin rewrites.

## Optional OCR provider

The Game Data panel can calibrate screen regions for Money and Experience and can invoke a locally installed Tesseract executable. OCR is optional and is isolated behind `GameDataProvider`; plugins do not depend on its implementation.

## Status

The API currently exposes process state, provider capabilities, money snapshots, experience snapshots, session deltas, and typed change events. Additional game state will be added only when a reliable data provider is available.

No PokeMMO game files, ROMs, credentials, or proprietary client assets are included.
