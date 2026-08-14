# MC-AI Chat

A Minecraft Forge mod that brings NPCs to life with Google's Gemini API. Whitelisted entities
(villagers by default) get a persistent name, personality, and memory, hold real conversations
shaped by their surroundings and history with you, can be given standing orders, and can even have
their homes founded, renamed, or conquered by the player. It's not just a chatbot bolted onto a
villager - it's a lightweight simulation layer that feeds Gemini everything an NPC would plausibly
know or care about, and lets you actually act on the world it describes.

## Features

### Natural Conversations with NPCs
- **Chat with entities** - click on or walk up to a whitelisted entity and type in chat to talk to it.
- **Persistent identity** - each entity gets a random name and personality the first time it's seen,
  which stick around forever.
- **Living memory** - after each conversation, the entity's memory is summarized and merged into a
  running dossier, so it remembers past interactions across sessions.
- **World-aware context** - conversations are shaped by the entity's home structure and how far it
  currently is from it, nearby civilizations, biome, time of day, weather, and nearby danger.
- **NPCs can start conversations too** - entities occasionally speak up on their own when the player
  is nearby, instead of only responding when spoken to.
- **Location awareness** - conversations account for whether the entity is outdoors, indoors, or in
  a cave, adjusting which ambient details (biome, time, weather, season) are shared accordingly.
- **Sleeping entities can't be chatted with** - an entity lying in a bed won't respond to chat or
  strike up a conversation, and an in-progress conversation ends if the entity falls asleep.
- **Generated lore** - the first time a "civilization" structure is discovered, Gemini writes a short
  history for it, cached forever after.
- **Social circles** - entities sharing a home know about each other (registered the moment a new
  resident is synced in, not just when someone looks at it), including if one has died.
- **It has to actually get to know you** - NPCs refer to you as "the player" until you tell one your
  name in chat, at which point it remembers you by name (and an optional description you set)
  from then on.

### Give them orders
- **`/follow`, `/stay`, `/resume`** - put a standing directive on whichever NPC you're looking at or
  standing near, and it'll narrate the order back to you in character.
- **`/goto`** - send an NPC to a coordinate, an x/z column, or a location it already knows by name
  (its home, or a nearby civilization/base it's aware of).
- Combat always overrides a standing directive, and an NPC that's hostile toward you (a monster by
  nature, or an enemy faction member) never takes one in the first place - it'll still talk to you,
  just not follow orders.

### Found, edit, and conquer your own locations
- **`/base new <name> <description>`** - found a brand-new named location (costs gold), which NPCs
  perceive exactly like a vanilla civilization - lore, nearby-location awareness, the works. Blocked
  from being founded too close to an existing civ/nomad structure or another player-founded location.
- **`/base edit <name> <description>`** - update a location's description (founder-only, costs gold).
- **`/base claim <name> <description>`** - conquer an existing civilization, nomad camp, or
  [Ice and Fire](https://www.curseforge.com/minecraft/mc-mods/ice-and-fire) dragon roost once its
  defenders are dead, migrating its surviving residents to serve your new location instead.
- A location's real name stays hidden from NPCs (shown as a placeholder) until someone actually says
  it in chat near an NPC who'd plausibly know it - except its own residents, who know their home's
  name from the moment they move in.

### Danger and faction awareness
- **Nameplates are color-coded**: green for friendly, red for hostile, yellow for an unaligned
  faction member, and blue for an NPC who knows a secret - except a hostile NPC always stays red,
  so danger is never hidden behind the "knows a secret" color.
- **Faction-aware sentiment and danger scanning** if [Valarian Conquest](https://www.curseforge.com/minecraft/mc-mods/valarian-conquest)
  is installed - allied and enemy faction members are called out by name in an NPC's situational
  awareness, and sentiment/nameplate color reflects team standing instead of just "hostile or not."
- **[Guard Villagers](https://www.curseforge.com/minecraft/mc-mods/guard-villagers), Iron Golems, and
  Valarian Conquest fighters** all count as capable defenders of a home when deciding whether a
  structure can be claimed.
- **Pillager Outpost spawn cap** - outposts normally respawn pillagers forever with no upper bound;
  this mod gives each one a configurable lifetime supply (12 by default) so it can eventually be
  fully cleared out - and, in turn, actually claimed with `/base claim`. Matches modded outpost
  variants too (e.g. [Towns and Towers](https://www.curseforge.com/minecraft/mc-mods/towns-and-towers)'
  22 biome variants) via structure tag matching, not just the vanilla structure.

### Optional mod integrations - all soft, none required
- **[Ice and Fire](https://www.curseforge.com/minecraft/mc-mods/ice-and-fire)** - dragon roosts are
  detected and treated like any other discoverable/claimable location.
- **[Guard Villagers](https://www.curseforge.com/minecraft/mc-mods/guard-villagers)** - guards count
  as capable fighters/defenders.
- **[Valarian Conquest](https://www.curseforge.com/minecraft/mc-mods/valarian-conquest)** - archers
  and soldiers get faction-aware sentiment, nameplate colors, and danger-scan callouts.
- **[Towns and Towers](https://www.curseforge.com/minecraft/mc-mods/towns-and-towers)** - its village
  and pillager outpost variants are recognized as civilizations and covered by the outpost spawn cap.

All four are no-ops if not installed - nothing above requires them.

### In-game config screens
No config files to hand-edit for day-to-day tuning:
- **Creature Config** - whitelist/blacklist entities for chat, categorize them as monsters,
  creatures, or wildlife for the "nearby danger" context Gemini receives, mark a type as always-
  wandering (never assigned a home structure), and set free-text special instructions baked into
  that entity type's system prompt (e.g. "you secretly work for the Thieves' Guild").
- **Structure Config** - categorize discovered (or known) structures as civilizations, nomad camps,
  adventure locations, or ignore them entirely.
- **Player identity** - set the name and short description NPCs will know you by, right on the main
  config screen.

The system prompt templates themselves are fixed internally (not user-editable files) - use the
Creature config's special instructions field for per-entity-type customization instead.

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
| `/follow [message]` | The NPC you're targeting starts following you. |
| `/stay [message]` | The NPC you're targeting holds its position. |
| `/resume [message]` | Clears any standing directive, letting the NPC wander/behave normally again. |
| `/goto <x> <y> <z> [message]` | Sends the NPC you're targeting to an exact coordinate. |
| `/goto <x> <z> [message]` | Same, but at ground level (heightmap-resolved) at that column. |
| `/goto <name> [message]` | Sends the NPC to a location it already knows by name (its home or a nearby civ/base). |
| `/base new <name> <description>` | Founds a new player-owned location at your position (costs gold). |
| `/base edit <name> <description>` | Updates a location's description (founder-only, costs gold). |
| `/base claim <name> <description>` | Conquers an existing structure/dragon roost once its defenders are dead (costs gold). |
| `/aichat debug` | Prints the most recent system prompt and message sent to Gemini. |
| `/aichat lore` | Toggles debug output for structure lore generation. |
| `/aichat init` | Toggles debug output for NPC-initiated conversations. |
| `/aichat location` | Shows the outdoors/indoors/cave determination for the last-checked entity, comparing the current and a retired algorithm side by side. |
| `/aichat findroost` | Lists nearby Ice and Fire dragon roosts (singleplayer/LAN only). |

## Configuration

From **Mods > MC-AI Chat > Config**, alongside your API key and the display name/description NPCs
will know you by, the **Creature Config** and **Structure Config** screens cover everything you'd
normally tune day-to-day. A couple of settings are file-only (`config/mcaichat-client.toml`), since
they're not something you'd change often:
- `homeRadius` - how far (in blocks) an NPC is biased to stay within once it's claimed a home.
- `minLocationDistance` - how close a new `/base new`/`/base claim` location can be to an existing
  civilization or another player-founded location (0 disables the check).
- `maxPillagersPerOutpost` - lifetime pillager spawn cap per outpost (0 disables it, restoring
  unlimited vanilla respawning).

## License

Licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) - share, modify, and
redistribute freely, including commercially, as long as you credit ThanWiggins.
