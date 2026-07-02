package earth.code.universe.asmp

import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import java.time.Instant
import java.time.Duration
import net.minecraft.world.level.saveddata.SavedData
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import net.minecraft.util.GsonHelper
import java.lang.reflect.Type
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.minecraft.ChatFormatting
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import org.slf4j.LoggerFactory
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.TextColor
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import kotlin.String
import kotlin.collections.Map

class Event {
    companion object {
        private const val KEY = "asmp_event"
        fun init() {
            ServerLivingEntityEvents.AFTER_DEATH.register { entity, damageSource ->
                val logger = LoggerFactory.getLogger("asmp_dna")

                val killer = damageSource.entity

                if (killer is ServerPlayer) {
                    val id = loadPersistentData(entity.server!!).getOrDefault(PersistentData()).activeEvent

                    val entityTypes = loadData().associateBy { it.id }.toMutableMap()[id]!!.toParsedData().killEntity
                    val entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)
                    val points = entityTypes[entityId]

                    logger.warn(entityId.toString() + points + entityTypes)

                    val score = entity.server!!.scoreboard

                    if (points != null) {
                        val pointsScore = score.getObjective("event")!!
                        pointsScore.scoreboard.getOrCreatePlayerScore(killer.scoreboardName, pointsScore).score += points
                    }
                }
            }

            var last = 0L

            ServerTickEvents.END_SERVER_TICK.register { server ->
                val now = server.tickCount.toLong()

                if (now - last >= 20) {
                    last = now
                    if (Instant.now() >= loadPersistentData(server).getOrDefault(PersistentData()).time) {
                        val score = server.scoreboard
                        val event = score.getOrCreateObjective("event")

                        val scores = event.scoreboard.trackedPlayers
                            .mapNotNull { name ->
                                val score = event.scoreboard.getOrCreatePlayerScore(name, event)
                                if (score.isLocked) null else name to score.score
                            }
                            .sortedByDescending { it.second }
                            .take(3)

                        scores.forEachIndexed { index, pair ->
                            var dna = 0

                            when (index) {
                                1 -> {
                                    dna = 2
                                }
                                2, 3 -> {
                                    dna = 1
                                }
                            }

                            val dnaScore = score.getObjective("dna")!!
                            dnaScore.scoreboard.getOrCreatePlayerScore(pair.first, dnaScore).score += dna

                            if (server.playerList.getPlayerByName(pair.first) == null) {
                                val data = loadPersistentData(server).getOrDefault(PersistentData())
                                data.delayedChanges[pair.first] = dna
                                savePersistentData(server, data)

                                return@forEachIndexed
                            }

                            Dna.onScoreChange(server.playerList.getPlayerByName(pair.first)!!)
                        }

                        score.removeObjective(event)

                        savePersistentData(server, PersistentData(delayedChanges = loadPersistentData(server).getOrDefault(PersistentData()).delayedChanges))
                    }
                }
            }

            ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
                val player = handler.player
                val data = loadPersistentData(server).getOrDefault(PersistentData())

                if (data.delayedChanges[player.scoreboardName] != null) {
                    val score = server.scoreboard
                    val dnaScore = score.getObjective("dna")!!
                    dnaScore.scoreboard.getOrCreatePlayerScore(player.scoreboardName, dnaScore).score += data.delayedChanges[player.scoreboardName]!!
                }
            }

            CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, env ->
                dispatcher.register(
                    literal("event")
                        .then(
                            literal("start").requires { source -> source.hasPermission(2) }
                                .then(
                                    argument("identifier", ResourceLocationArgument())
                                        .executes { context ->
                                            val source = context.source
                                            val id = ResourceLocationArgument.getId(context, "identifier")
                                            val event = loadData().associateBy { it.id }.toMutableMap()[id]

                                            if (event == null) {
                                                source.sendSystemMessage(Component.literal("Invalid Event Identifier."))
                                                return@executes 0
                                            }

                                            savePersistentData(
                                                source.server,
                                                PersistentData(
                                                    activeEvent = id,
                                                    time = Instant.now().plusSeconds(24 * 60 * 60)
                                                )
                                            )

                                            startEvent(source.server)

                                            val msg =
                                                Component.literal("The Event ${event.name} has started. It will end in 24h. You can see a list of the Challenges as well as the remaining time using ")
                                                    .append(
                                                        Component.literal("/event info")
                                                            .withStyle(
                                                                Style.EMPTY.withClickEvent(
                                                                    ClickEvent(
                                                                        ClickEvent.Action.RUN_COMMAND,
                                                                        "/event info"
                                                                    )
                                                                )
                                                            )
                                                            .withStyle {
                                                                it.withColor(TextColor.fromLegacyFormat(ChatFormatting.LIGHT_PURPLE))
                                                            }
                                                    )

                                            source.server.playerList.players.forEach { player ->
                                                player.sendSystemMessage(msg)
                                            }

                                            1
                                        }
                                )
                        )
                        .then(
                            literal("info")
                                .executes { context ->
                                    val source = context.source
                                    val persistentData: PersistentData =
                                        loadPersistentData(source.server).getOrDefault(PersistentData())
                                    val currentRL = persistentData.activeEvent
                                    if (currentRL == null) {
                                        source.sendSystemMessage(Component.literal("There is no Event active right now! Try again when an Event has started."))
                                        return@executes 0
                                    }
                                    val currentEvent = loadData().associateBy { it.id }.toMutableMap().getOrElse(currentRL) {
                                        source.sendSystemMessage(Component.literal("There is no Event active right now! Try again when an Event has started."))
                                        return@executes 0
                                    }

                                    if (currentEvent == EventData()) {
                                        source.sendSystemMessage(Component.literal("There is no Event active right now! Try again when an Event has started."))
                                        return@executes 0
                                    }

                                    val time: Duration = Duration.between(Instant.now(), persistentData.time)

                                    val challengeText = currentEvent.challs.joinToString("\n") { it.format(source.server) }

                                    val h = time.toHours()
                                    val m = time.toMinutes() % 60
                                    val s = time.seconds % 60

                                    source.sendSystemMessage(
                                        Component.literal(
                                            """Current Event: ${currentEvent.name}
                                    |The Event ends in $h:$m:$s          
                                    |Current Challenge(s): 
                                    |$challengeText
                                    |
                                """.trimMargin()
                                        )
                                    )

                                    1
                                }

                        )
                        .then(
                            literal("sacrifice")
                                .executes { context ->
                                    val source = context.source
                                    val server = source.server
                                    val player = source.player ?: return@executes 0

                                    val heldItemStack = player.getItemInHand(InteractionHand.MAIN_HAND)

                                    val id = loadPersistentData(server).getOrDefault(PersistentData()).activeEvent

                                    val items = loadData().associateBy { it.id }.toMutableMap()[id]!!.toParsedData().item
                                    val itemId = BuiltInRegistries.ITEM.getKey(heldItemStack.item)
                                    val points = items[itemId]

                                    if (points != null) {
                                        val count = heldItemStack.count

                                        val totalPoints = points * count

                                        heldItemStack.count = 0

                                        val score = server.scoreboard

                                        val pointsScore = score.getObjective("event")!!
                                        pointsScore.scoreboard.getOrCreatePlayerScore(player.scoreboardName, pointsScore).score += totalPoints

                                        source.sendSystemMessage(Component.literal("Sacrificed $count ${heldItemStack.hoverName.string} for $totalPoints Points."))
                                    }
                                    else {
                                        source.sendSystemMessage(Component.literal("This item can't be sacrificed for the current event!"))
                                    }

                                    1
                                }
                        )
                        .then(
                            literal("parse").requires { source -> source.hasPermission(2) }
                                .then(
                                    argument("identifier", ResourceLocationArgument())
                                        .executes { context ->
                                            val source = context.source
                                            val currentRL = ResourceLocationArgument.getId(context, "identifier")
                                            if (currentRL == null) {
                                                source.sendSystemMessage(Component.literal("Event couldn't be parsed!"))
                                                return@executes 0
                                            }
                                            val currentEvent = loadData().associateBy { it.id }.toMutableMap().getOrElse(currentRL) {
                                                source.sendSystemMessage(Component.literal("Event couldn't be parsed!"))
                                                return@executes 0
                                            }

                                            if (currentEvent == EventData()) {
                                                source.sendSystemMessage(Component.literal("Event couldn't be parsed or is empty!"))
                                                return@executes 0
                                            }

                                            val challengeText = currentEvent.challs.joinToString("\n") { it.format(source.server) }

                                            source.sendSystemMessage(
                                                Component.literal(
                                                    """Event Name: ${currentEvent.name}
                                    |Challenge(s): 
                                    |$challengeText
                                    |
                                """.trimMargin()
                                                )
                                            )

                                            1
                                        }
                                )
                        )
                )
            }
        }

        class ParsedData (
            var killEntity: MutableMap<ResourceLocation, Int> = mutableMapOf(),
            var item: MutableMap<ResourceLocation, Int> = mutableMapOf(),
            var achievements: MutableMap<ResourceLocation, Int> = mutableMapOf()
        )

        fun EventData.toParsedData(): ParsedData {
            val killEntity: MutableMap<ResourceLocation, Int> = mutableMapOf()
            val item: MutableMap<ResourceLocation, Int> = mutableMapOf()
            val achievements: MutableMap<ResourceLocation, Int> = mutableMapOf()

            val data = this

            data.challs.forEach {
                when (it) {
                    is SacrificeChallenge -> {
                        item[it.item] = it.points
                    }
                    is AchievementChallenge -> {
                        achievements[it.achievement] = it.points
                    }
                    is KillEntityTypeChallenge -> {
                        killEntity[it.entityType] = it.points
                    }
                }
            }

            return ParsedData(killEntity, item, achievements)
        }

        fun startEvent(server: MinecraftServer) {
            val scoreboard = server.scoreboard

            scoreboard.getObjective("event")?.let {
                scoreboard.removeObjective(it)
            }

            val objective = scoreboard.addObjective("event", ObjectiveCriteria.DUMMY, Component.literal("Event"), ObjectiveCriteria.RenderType.INTEGER)

            scoreboard.setDisplayObjective(1, objective)
        }

        class PersistentData(
            var time: Instant = Instant.now(),
            var activeEvent: ResourceLocation? = null,
            var delayedChanges: MutableMap<String, Int> = mutableMapOf()
        )

        class PersistentDataSerializable(
            var time: Long = System.currentTimeMillis(),
            var activeEvent: String? = null,
            var delayedChanges: MutableMap<String, Int> = mutableMapOf()
        )

        fun PersistentDataSerializable.toRuntime(): PersistentData =
            PersistentData(
                time = Instant.ofEpochMilli(time),
                activeEvent = activeEvent?.let(::ResourceLocation),
                delayedChanges = delayedChanges
            )

        fun PersistentData.toSerializable(): PersistentDataSerializable =
            PersistentDataSerializable(
                time = time.toEpochMilli(),
                activeEvent = activeEvent?.toString(),
                delayedChanges = delayedChanges
            )

        class ModState(
            var data: PersistentDataSerializable = PersistentDataSerializable()
        ) : SavedData() {

            override fun save(nbt: CompoundTag): CompoundTag {
                nbt.putLong("time", data.time)
                nbt.putString("activeEvent", data.activeEvent ?: "")

                val nbtMap = CompoundTag()

                data.delayedChanges.forEach { (key, value) ->
                    nbtMap.putInt(key, value)
                }

                nbt.put("delayedChanges", nbtMap)

                return nbt
            }

            companion object {
                fun load(nbt: CompoundTag): ModState {
                    val state = ModState()

                    val compound = nbt.getCompound("delayedChanges")

                    val map = mutableMapOf<String, Int>()

                    for (key in compound.allKeys) {
                        map[key] = compound.getInt(key)
                    }

                    state.data = PersistentDataSerializable(
                        time = nbt.getLong("time"),
                        activeEvent = nbt.getString("activeEvent").ifBlank { null },
                        delayedChanges = map
                    )
                    return state
                }
            }
        }

        fun loadPersistentData(server: MinecraftServer): Result<PersistentData> {
            val storage = server.overworld().dataStorage

            val state = storage.computeIfAbsent(
                ModState::load,
                { ModState() },
                KEY
            )

            return Result.success(state.data.toRuntime())
        }

        fun savePersistentData(server: MinecraftServer, data: PersistentData) {
            val storage = server.overworld().dataStorage

            val state = storage.computeIfAbsent(
                ModState::load,
                { ModState() },
                KEY
            )

            state.data = data.toSerializable()
            state.setDirty()
        }

        class EventData(
            var id: ResourceLocation = ResourceLocation(""),
            var challs: MutableList<Challenge> = mutableListOf(),
            var name: String = ""
        )

        sealed interface Challenge {
            fun format(server: MinecraftServer): String = when (this) {
                is SacrificeChallenge -> "Sacrifice ${BuiltInRegistries.ITEM.get(item).defaultInstance.hoverName.string} ($item), +$points Point(s)"
                is KillEntityTypeChallenge -> "Kill ${BuiltInRegistries.ENTITY_TYPE.get(entityType).description.string} ($entityType), +$points Point(s)"
                is AchievementChallenge -> "Achieve ${server.advancements.getAdvancement(achievement)?.display?.title?.string} ($achievement), +$points Point(s)"
            }
        }

        fun loadData(): MutableList<EventData> {
            val data = EventDataLoader.INSTANCE.data.values.toMutableList()
            return data
        }

        data class SacrificeChallenge(
            var item: ResourceLocation,
            var points: Int
        ) : Challenge

        data class KillEntityTypeChallenge(
            var entityType: ResourceLocation,
            var points: Int
        ) : Challenge

        data class AchievementChallenge(
            var achievement: ResourceLocation,
            var points: Int
        ) : Challenge

        public fun validateAdvancements(server: MinecraftServer) {
            val advancementManager = server.advancements

            for (event in EventDataLoader.INSTANCE.data.values) {
                val LOGGER = LoggerFactory.getLogger("ASMPEventDataLoader")

                event.challs.forEachIndexed { index, chall ->
                    if (chall is AchievementChallenge) {
                        val advancement = advancementManager.getAdvancement(chall.achievement)
                        if (advancement == null) {
                            LOGGER.warn(
                                "[{}] Challenge #{} (AchievementChallenge): unknown advancement '{}' ",
                                event.id, index, chall.achievement
                            )
                        }
                    }
                }
            }
        }


        class EventDataLoader private constructor(
            private val ownGson: Gson
        ) : SimpleJsonResourceReloadListener(
            ownGson,
            "events"
        ), IdentifiableResourceReloadListener {

            companion object {
                private val GSON = GsonBuilder()
                    .registerTypeAdapter(Challenge::class.java, ChallengeDeserializer)
                    .create()

                val INSTANCE = EventDataLoader(GSON)
                private val LOGGER = LoggerFactory.getLogger("asmp_dna")

                private val ID = ResourceLocation("asmp_dna", "events")
            }

            override fun getFabricId(): ResourceLocation = ID

            var data: Map<ResourceLocation, EventData> = mapOf()
                private set

            override fun apply(
                map: MutableMap<ResourceLocation, JsonElement>,
                resourceManager: ResourceManager,
                profiler: ProfilerFiller
            ) {
                val newData = mutableMapOf<ResourceLocation, EventData>()

                for ((id, json) in map) {
                    try {
                        val obj = json.asJsonObject
                        val name = GsonHelper.getAsString(obj, "name")
                        val challs = obj.getAsJsonArray("challs")
                            .map { ownGson.fromJson(it, Challenge::class.java) }
                            .toMutableList()

                        validateChallenges(id, challs)

                        newData[id] = EventData(id = id, challs = challs, name = name)
                    } catch (e: Exception) {
                        LOGGER.error("Failed to parse event data file {}", id, e)
                    }
                }

                data = newData
            }

            private fun validateChallenges(fileId: ResourceLocation, challs: List<Challenge>) {
                val LOGGER = LoggerFactory.getLogger("asmp_dna")
                challs.forEachIndexed { index, chall ->
                    when (chall) {
                        is SacrificeChallenge -> {
                            if (!BuiltInRegistries.ITEM.containsKey(chall.item)) {
                                LOGGER.warn(
                                    "[{}] Challenge #{} (SacrificeChallenge): unknown item '{}' ",
                                    fileId, index, chall.item
                                )
                            }
                        }

                        is KillEntityTypeChallenge -> {
                            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(chall.entityType)) {
                                LOGGER.warn(
                                    "[{}] Challenge #{} (KillEntityTypeChallenge): unknown entity type '{}' ",
                                    fileId, index, chall.entityType
                                )
                            }
                        }

                        is AchievementChallenge -> {
                            // Advancements possibly not loaded yet
                        }
                    }
                }
            }
        }

        object ChallengeDeserializer : JsonDeserializer<Challenge> {
            override fun deserialize(
                json: JsonElement,
                typeOfT: Type,
                context: JsonDeserializationContext
            ): Challenge {
                val obj = json.asJsonObject
                val type = GsonHelper.getAsString(obj, "type")
                val factory = factories[type]
                    ?: throw JsonSyntaxException("Unknown Challenge Type: $type")
                return factory(obj)
            }


            private val factories: Map<String, (JsonObject) -> Challenge> = mapOf(
                "item" to { obj ->
                    SacrificeChallenge(
                        item = ResourceLocation(GsonHelper.getAsString(obj, "item")),
                        points = GsonHelper.getAsInt(obj, "points")
                    )
                },
                "entity_kill" to { obj ->
                    KillEntityTypeChallenge(
                        points = GsonHelper.getAsInt(obj, "points"),
                        entityType = ResourceLocation(GsonHelper.getAsString(obj, "entity_type"))
                    )
                },
                "achievement" to { obj ->
                    AchievementChallenge(
                        points = GsonHelper.getAsInt(obj, "points"),
                        achievement = ResourceLocation(GsonHelper.getAsString(obj, "achievement"))
                    )
                }
            )
        }


    }
}