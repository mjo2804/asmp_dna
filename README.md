<div align="center">
  <img src="./Logo.png" height="112px" alt="Proxmox VE Helper-Scripts Logo" />

  <h1>Artifice SMP DNA System</h1>
  <p>
    <a href="https://discord.gg/GWQv55Fs4Q"><img src="https://img.shields.io/badge/discord-ArtificeSMP-7289da?style=flat-square&logo=discord&logoColor=white" /></a>
    <a href="https://discord.gg/GWQv55Fs4Q"><img src="https://img.shields.io/github/v/release/fldebug10/asmp_dna?style=flat-square&logo=github&logoColor=white" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-GNU_GPL_v3.0-green?style=flat-square" /></a>
  </p>
</div>


## Features:
- commands:
  - /dna change Player DNA(Default=1)
  - /dna pay Player DNA(Default=1)
  - /dna item Player DNA(Default=1)
  - /dna clear Player
 
  - /event start Identifier
  - /event info
  - /event sacrifice
  - /event parse Identifier
  - /event list
- DNA Helix Item (immune to fire) which grant 1 DNA each on right click
- 1 DNA Helix drops when a player is killed by a player, removing 1 from the DNA scoreboard
- Origins are changed automatically if formatted as *any_namespace:anything_[number]* with [number] = 0 => base form; 1 => 1st evo...
- Events (more info below)

## Events:
Adds Data Driven Events which are defined in `data/namespace/events/file.json` and can be referred to in-game with the identifier `namespace:file`.
An example implementing all three challenge types is below, it can also be found here: [/src/main/resources/data/test/events/test.json](https://github.com/FLDebug10/asmp_dna/blob/master/src/main/resources/data/test/events/test.json).
You can use the `/event parse` command to check if your datapack is being parsed correctly, you should also check your logs if it doesn't because it should warn if an identifier (for items, entity types or advancements) can't be found.

```json
{
  "name": "Test Event",
  "challs": [
    {
      "type": "item",
      "item": "minecraft:dirt",
      "points": 1
    },
    {
      "type": "achievement",
      "achievement": "minecraft:story/root",
      "points": 2
    },
    {
      "type": "entity_kill",
      "entity_type": "minecraft:zombie",
      "points": 3
    }
  ]
}
```

## Dependencies:
- Fabric >= 0.19.3
- Fabric API
- Fabric Lang. Kotlin >= 1.13.12+kotlin.2.4.0
- Minecraft 1.20.1
- Origins >= 1.10.2+mc.1.20.1
