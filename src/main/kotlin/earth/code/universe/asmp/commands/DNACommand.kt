package earth.code.universe.asmp.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import earth.code.universe.asmp.Dna.Companion.onScoreChange
import earth.code.universe.asmp.Dna.ModItems
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class DNACommand {
    fun setup() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, env ->
            dispatcher.register(
                literal("dna")
                    .then(
                        literal("change").requires { source -> source.hasPermission(2) }.then(
                            argument("Player", EntityArgument.player())
                                .executes { context ->
                                    val source = context.source
                                    val server = source.server
                                    val target = EntityArgument.getPlayer(context, "Player").scoreboardName
                                    val obj = server.scoreboard.getOrCreateObjective("dna")
                                    server.scoreboard.getOrCreatePlayerScore(target, obj).add(1)
                                    onScoreChange(source.player!!)
                                    source.sendSystemMessage(Component.literal("Granted 1 DNA Point to $target."))
                                    1
                                }
                                .then(
                                    argument("DNA_Points", IntegerArgumentType.integer())
                                        .executes { context ->
                                            val source = context.source
                                            val server = source.server
                                            val target = EntityArgument.getPlayer(context, "Player").scoreboardName
                                            val points = IntegerArgumentType.getInteger(context, "DNA_Points")
                                            val obj = server.scoreboard.getOrCreateObjective("dna");
                                            server.scoreboard.getOrCreatePlayerScore(target, obj).add(points)
                                            onScoreChange(source.player!!)
                                            source.sendSystemMessage(Component.literal("Granted $points DNA Points to $target."))
                                            1
                                        })
                        )
                    )
                    .then(
                        literal("pay")
                            .then(
                                argument("Player", EntityArgument.player())
                                    .executes { context ->
                                        val source = context.source
                                        val server = source.server
                                        val sourcePlayer = source.player?.scoreboardName
                                        val target = EntityArgument.getPlayer(context, "Player")
                                        val targetPlayer = target.scoreboardName

                                        var dna = 0

                                        val scoreboard = server.scoreboard
                                        val objective = scoreboard.getOrCreateObjective("dna")

                                        if (objective != null) {
                                            val score = scoreboard.getOrCreatePlayerScore(
                                                source.player?.scoreboardName!!,
                                                objective
                                            )

                                            dna = score.score
                                        }

                                        if (dna > 0) {
                                            server.scoreboard.getOrCreatePlayerScore(targetPlayer, objective).add(dna)
                                            server.scoreboard.getOrCreatePlayerScore(sourcePlayer, objective).add(-dna)
                                            onScoreChange(source.player!!)
                                            onScoreChange(target)

                                            source.sendSystemMessage(Component.literal("You gave 1 DNA Points to ${targetPlayer}."))
                                            target.sendSystemMessage(Component.literal("You received 1 DNA Points from ${sourcePlayer}."))
                                        }
                                        else {
                                            source.sendSystemMessage(Component.literal("You don't have any dna left."))
                                        }
                                        1
                                    }
                                    .then(
                                        argument("DNA_Points", IntegerArgumentType.integer(1))
                                            .executes { context ->
                                                val source = context.source
                                                val server = source.server

                                                val target = EntityArgument.getPlayer(context, "Player")
                                                val points = IntegerArgumentType.getInteger(context, "DNA_Points")
                                                val sourcePlayer = source.player?.scoreboardName
                                                val targetPlayer = target.scoreboardName
                                                var dna = 0
                                                val scoreboard = server.scoreboard
                                                val objective = scoreboard.getOrCreateObjective("dna")
                                                if (objective != null) {
                                                    val score = scoreboard.getOrCreatePlayerScore(
                                                        source.player?.scoreboardName!!,
                                                        objective
                                                    )

                                                    dna = score.score
                                                }
                                                if (dna >= points) {
                                                    server.scoreboard.getOrCreatePlayerScore(targetPlayer, objective).add(dna)
                                                    server.scoreboard.getOrCreatePlayerScore(sourcePlayer, objective).add(-dna)
                                                    onScoreChange(source.player!!)
                                                    onScoreChange(target)

                                                    onScoreChange(source.player!!)
                                                    onScoreChange(target)

                                                    source.sendSystemMessage(Component.literal("You gave $points DNA Points to ${target.scoreboardName}."))
                                                    target.sendSystemMessage(Component.literal("You received $points DNA Points from ${source.player?.scoreboardName}"))
                                                }
                                                else {
                                                    source.sendSystemMessage(Component.literal("You don't have enough dna left."))
                                                }

                                                1
                                            }
                                    )
                            )
                    )
                    .then(
                        literal("item")
                            .executes { context ->
                                val source = context.source
                                val server = source.server
                                var dna = 0
                                val scoreboard = server.scoreboard
                                val objective = scoreboard.getObjective("dna")

                                if (objective != null) {
                                    val score = scoreboard.getOrCreatePlayerScore(
                                        source.player?.scoreboardName!!,
                                        objective
                                    )

                                    dna = score.score
                                }

                                if (dna > 0) {

                                    source.player?.addItem(ItemStack(ModItems.DNA,1))
                                    server.scoreboard.getOrCreatePlayerScore(source.player?.scoreboardName,objective).add(-1)
                                    onScoreChange(source.player!!)

                                    source.sendSystemMessage(Component.literal("Converted 1 DNA Point into a Helix."))
                                }
                                else {
                                    source.sendSystemMessage(Component.literal("You don't have any dna left."))
                                }
                                1
                            }
                            .then(
                                argument("DNA_Points", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val source = context.source
                                        val server = source.server
                                        val points = IntegerArgumentType.getInteger(context, "DNA_Points")
                                        var dna = 0
                                        val scoreboard = server.scoreboard
                                        val objective = scoreboard.getObjective("dna")
                                        if (objective != null) {
                                            val score = scoreboard.getOrCreatePlayerScore(
                                                source.player?.scoreboardName!!,
                                                objective
                                            )

                                            dna = score.score
                                        }

                                        if (dna >= points) {
                                            source.player?.addItem(ItemStack(ModItems.DNA,points))
                                            server.scoreboard.getOrCreatePlayerScore(source.player?.scoreboardName,objective).add(-points)
                                            onScoreChange(source.player!!)


                                            onScoreChange(source.player!!)

                                            source.sendSystemMessage(Component.literal("Converted $points DNA Points into Helices."))
                                        }
                                        else {
                                            source.sendSystemMessage(Component.literal("You don't have enough DNA left."))
                                        }

                                        1
                                    }
                            )
                    )
                    .then(
                        literal("clear")
                            .requires { source -> source.hasPermission(2) }
                            .then(
                                argument("Player", EntityArgument.player())
                                    .executes { context ->
                                        val source = context.source
                                        val server = source.server
                                        val target = EntityArgument.getPlayer(context, "Player").scoreboardName
                                        val objective = server.scoreboard.getObjective("dna")
                                        server.scoreboard.getOrCreatePlayerScore(target, objective ).reset()
                                        onScoreChange(source.player!!)
                                        source.sendSystemMessage(Component.literal("Cleared $target's DNA Points."))
                                        1
                                    }
                            )

                    )
            )

        }
    }

}