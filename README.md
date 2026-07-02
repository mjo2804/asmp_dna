# ArtificeSMP DNA System

## Features:
- commands:
  - /dna change Player DNA(Default=1)
  - /dna pay Player DNA(Default=1)
  - /dna item Player DNA(Default=1)
  - /dna clear Player
 
  - /event start Identifier
  - /event info
  - /event sacrifice
- DNA Helix Item (immune to fire) which grant 1 DNA each on right click
- 1 DNA Helix drops when a player is killed by a player, removing 1 from the DNA scoreboard
- Origins are changed automatically if formatted as *any_namespace:anything_[number]* with [number] = 0 => base form; 1 => 1st evo...
- Events (more info below)

## Events:
Data Driven Events 

## Dependencies:
- Fabric >= 0.19.3
- Fabric API
- Fabric Lang. Kotlin >= 1.13.12+kotlin.2.4.0
- Minecraft 1.20.1
- Origins >= 1.10.2+mc.1.20.1
