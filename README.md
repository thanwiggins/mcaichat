# MC-AI Chat

A Minecraft Forge mod that lets you talk to nearby entities using Google's Gemini API. Whitelisted
entities (villagers by default) get a persistent name, personality, and memory, and can hold real
conversations shaped by their surroundings, their history with you, and what's going on around them
right now.

## Features

- **Chat with entities** - click on or walk up to a whitelisted entity and type in chat to talk to it.
- **Persistent identity** - each entity gets a random name and personality the first time it's seen,
  which stick around forever.
- **Living memory** - after each conversation, the entity's memory is summarized and merged into a
  running dossier, so it remembers past interactions across sessions.
- **World-aware context** - conversations are shaped by the entity's home structure, nearby
  civilizations, biome, time of day, weather, nearby danger, and more.
- **NPCs can start conversations too** - entities occasionally speak up on their own when the player
  is nearby, instead of only responding when spoken to.
- **Location awareness** - conversations account for whether the entity is outdoors, indoors, or in
  a cave, adjusting which ambient details (biome, time, weather, season) are shared accordingly.
- **Sleeping entities can't be chatted with** - an entity lying in a bed won't respond to chat or
  strike up a conversation, and an in-progress conversation ends if the entity falls asleep.
- **Generated lore** - the first time a "civilization" structure is discovered, Gemini writes a short
  history for it, cached forever after.
- **Social circles** - entities sharing a home know about each other, including if one has died.
- **Ice and Fire dragon roost detection** - if [Ice and Fire](https://www.curseforge.com/minecraft/mc-mods/ice-and-fire)
  is installed, dragon roosts are detected and treated like any other discoverable location. This is
  a no-op if Ice and Fire isn't installed.
- **In-game config screens** - toggle which entities can be chatted with, and categorize both
  entities and structures, without editing config files by hand.

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0+
- [Structurify](https://www.curseforge.com/minecraft/mc-mods/structurify) 2.0.28+ (required dependency)
- A free [Google Gemini API key](https://ai.google.dev/)

## Installation

1. Install Forge for Minecraft 1.20.1.
2. Drop `mcaichat-<version>.jar` and `structurify-<version>.jar` into your `mods` folder.
3. Launch the game once to generate the config files.
4. Open **Mods > MC-AI Chat > Config** and paste in your Gemini API key.

## Usage

Chat like normal while looking at or standing near a whitelisted entity - your message goes to it
instead of global chat.

| Command | Effect |
|---|---|
| `/aichat debug` | Prints the most recent system prompt and message sent to Gemini. |
| `/aichat lore` | Toggles debug output for structure lore generation. |
| `/aichat init` | Toggles debug output for NPC-initiated conversations. |
| `/aichat location` | Shows the outdoors/indoors/cave determination for the last-checked entity, comparing the current and a retired algorithm side by side. |
| `/aichat findroost` | Lists nearby Ice and Fire dragon roosts (singleplayer/LAN only). |

## Configuration

From **Mods > MC-AI Chat > Config**:
- **Creature Config** - whitelist/blacklist entities for chat, categorize them as monsters,
  creatures, or wildlife for the "nearby danger" context Gemini receives, mark a type as always-
  wandering (never assigned a home structure), and set free-text special instructions baked into
  that entity type's system prompt (e.g. "you secretly work for the Thieves' Guild").
- **Structure Config** - categorize discovered (or known) structures as civilizations, nomad camps,
  adventure locations, or ignore them entirely.

The system prompt templates themselves are fixed internally (not user-editable files) - use the
Creature config's special instructions field for per-entity-type customization instead.

## Building from source

```
./gradlew build
```

The output jar is written to `build/libs/`.

## License

Licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) - share, modify, and
redistribute freely, including commercially, as long as you credit ThanWiggins.
