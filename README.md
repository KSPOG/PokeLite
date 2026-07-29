# PokeLite

PokeLite is an experimental, 100% Java client shell intended to provide a RuneLite-style desktop and plugin experience around the official PokeMMO client.

## Current prototype

This initial import contains:

- a Java launcher that locates and starts `PokeMMO.exe` or `PokeMMO.sh`;
- an experimental same-JVM class-loader path with external-launch fallback;
- a minimal Java plugin contract;
- recursive plugin class discovery;
- a sample logging plugin.

## Important status

This repository is an early prototype. The client integration, plugin API, desktop interface, build system, overlays, configuration framework, event bus, and compatibility safeguards still require a full redesign before production use.

No PokeMMO game files, ROMs, credentials, or proprietary client assets are included.

## Prototype compilation

From the repository root:

```bash
javac -d . Client.java ClientPlugin.java
javac -cp . -d plugins plugins/pokemmo/plugins/SamplePlugin.java
java pokemmo.Client
```

A standard Gradle multi-module structure will replace this manual compilation flow in a later milestone.
