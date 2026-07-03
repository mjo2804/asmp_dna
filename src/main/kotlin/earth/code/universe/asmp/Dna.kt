package earth.code.universe.asmp

import com.mojang.brigadier.arguments.IntegerArgumentType
import earth.code.universe.asmp.Event.Companion.validateAdvancements
import earth.code.universe.asmp.commands.DNACommand
import io.github.apace100.origins.origin.OriginLayers
import io.github.apace100.origins.registry.ModComponents
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory


class Dna : ModInitializer {

    object ModItems {
        val DNA = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation("asmp_dna", "dna"),
            DnaItem(Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant())
        )

        fun init() {}
    }

    override fun onInitialize() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
            .registerReloadListener(Event.Companion.EventDataLoader.INSTANCE)

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            validateAdvancements(server)
        }

        Event.init()
        ModEvents.init()
        ModItems.init()
        println("ASMP DNA System Initialized")
        DNACommand().setup()

    }
    class DnaItem(properties: Properties) : Item(properties) {

        override fun use(
            level: Level,
            player: Player,
            hand: InteractionHand
        ): InteractionResultHolder<ItemStack> {

            val stack = player.getItemInHand(hand)

            if (!level.isClientSide) {
                if (player.isCrouching) {
                    val dna = player.getItemInHand(hand).count
                    val server = player.createCommandSourceStack().server
                    val obj = server.scoreboard.getOrCreateObjective("dna")
                    player.getItemInHand(hand).count = 0
                    server.scoreboard.getOrCreatePlayerScore(player.scoreboardName,obj).add(dna)
                    onScoreChange(player as ServerPlayer)
                    player.createCommandSourceStack().sendSystemMessage(Component.literal("Gained $dna DNA Points from consuming the Helices."))
                }
                else {

                    val server = player.createCommandSourceStack().server
                    val obj = server.scoreboard.getOrCreateObjective("dna")
                    player.getItemInHand(hand).count--
                    server.scoreboard.getOrCreatePlayerScore(player.scoreboardName, obj).add(1)
                    onScoreChange(player as ServerPlayer)

                    player.createCommandSourceStack().sendSystemMessage(Component.literal("Gained 1 DNA Point from consuming the Helix."))
                }
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
    }
    object ModEvents {
        fun init() {
            ServerLivingEntityEvents.AFTER_DEATH.register { entity, damageSource ->
                if (entity is Player && damageSource.entity is Player) {
                    val player: Player = entity

                    var dna = 0

                    val scoreboard = player.server!!.scoreboard
                    val objective = scoreboard.getObjective("dna")

                    if (objective != null) {
                        val score = scoreboard.getOrCreatePlayerScore(
                            player.scoreboardName,
                            objective
                        )

                        dna = score.score
                    }

                    if(dna > 0) {
                        val server = player.createCommandSourceStack().server

                        server.scoreboard.getObjective("dna")
                        server.scoreboard.getOrCreatePlayerScore(player.scoreboardName, objective).add(dna)
                        onScoreChange(player as ServerPlayer)

                        val item = ItemStack(ModItems.DNA)

                        val itemEntity = ItemEntity(
                            player.level(),
                            player.x,
                            player.y,
                            player.z,
                            item
                        )

                        player.level().addFreshEntity(itemEntity)
                    }
                }
            }
        }
    }
    companion object {
        fun onScoreChange(player: ServerPlayer) {
            var dna = 0

            val scoreboard = player.server!!.scoreboard
            val objective = scoreboard.getObjective("dna")

            if (objective != null) {
                val score = scoreboard.getOrCreatePlayerScore(
                    player.scoreboardName,
                    objective
                )

                dna = score.score
            }

            val component = ModComponents.ORIGIN.get(player)
            val layer = OriginLayers.getLayer(ResourceLocation("origins", "origin"))

            val origin = component.getOrigin(layer).identifier.toString().split("_")
            var originTemplate = ""
            if (origin.size > 1) {
                originTemplate = origin.subList(0, origin.size - 1).joinToString("_")
            }
            else {
                originTemplate = origin.joinToString("_")
            }

            for(i in 0..dna) {
                val newOrigin = originTemplate + "_" + i.toString()

                val server = player.createCommandSourceStack().server
                val elevatedSource = player.createCommandSourceStack().withPermission(4).withSuppressedOutput()
                val runCommand = "origin set ${player.scoreboardName} origins:origin $newOrigin"
                val commandExecutor = server.commands
                commandExecutor.performPrefixedCommand(elevatedSource, runCommand)
            }
        }
    }
}