package io.github.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.context.CommandContext
import io.github.MOD_ID
import io.github.ModRegister
import io.github.add
import io.github.util.RoomSystemResult.ALREADY
import io.github.util.RoomSystemResult.FAIL
import io.github.util.RoomSystemResult.ONE_WAY_LINK
import io.github.util.RoomSystemResult.POINT_DOES_NOT_EXIST
import io.github.util.RoomSystemResult.ROOM_DOES_NOT_EXIST
import io.github.util.RoomSystemResult.SAME_ROOM
import io.github.util.RoomSystemResult.SUCCESS
import io.github.util.RoomSystemStorage
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.text.Text.translatable

private const val COMMAND_SUCCESS = 1
private const val COMMAND_FAIL = 2
private const val COMMAND_WARNING = 3
private const val COMMAND_UNKNOWN = 4

private val argumentName = translatable("command.ds.argument.name").string
private val argumentPointFirst = translatable("command.ds.argument.point.first").string
private val argumentPointSecond = translatable("command.ds.argument.point.second").string

class ModCommands(private val storage: RoomSystemStorage) : ModRegister() {
    private val roomSystem
        get() = storage.roomSystem

    override fun register() = CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
        dispatcher.register(
            literal<ServerCommandSource>(MOD_ID)
//                .requires { source -> source.hasPermissionLevel(4) }
                .then(
                    literal<ServerCommandSource>("save")
                        .then(
                            CommandManager.argument("name", StringArgumentType.word()).executes { context ->
                                val player = prepare(context)
                                    ?: return@executes COMMAND_FAIL
                                save(context, player)
                            },
                        ),
                )
                .then(
                    literal<ServerCommandSource>("read")
                        .then(
                            CommandManager.argument("name", StringArgumentType.word()).executes { context ->
                                val player = prepare(context)
                                    ?: return@executes COMMAND_FAIL
                                read(context, player)
                            },
                        ),
                )
                .then(
                    literal<ServerCommandSource>("room")
                        .then(
                            literal<ServerCommandSource>("create")
                                .then(
                                    CommandManager.argument("name", StringArgumentType.word()).executes { context ->
                                        val player = prepare(context)
                                            ?: return@executes COMMAND_FAIL
                                        roomCreate(context, player)
                                    },
                                ),
                        )
                        .then(
                            literal<ServerCommandSource>("delete")
                                .then(
                                    CommandManager.argument("name", StringArgumentType.word()).executes { context ->
                                        val player = prepare(context)
                                            ?: return@executes COMMAND_FAIL
                                        roomDelete(context, player)
                                    },
                                )
                                .then(
                                    literal<ServerCommandSource?>("all").executes { context ->
                                        val player = prepare(context)
                                            ?: return@executes COMMAND_FAIL
                                        roomDeleteAll(player)
                                    },
                                ),
                        )
                        .then(
                            literal<ServerCommandSource>("link")
                                .then(
                                    CommandManager.argument("first name", StringArgumentType.word())
                                        .then(
                                            CommandManager.argument("second name", StringArgumentType.word())
                                                .executes { context ->
                                                    val player = prepare(context)
                                                        ?: return@executes COMMAND_FAIL
                                                    roomLink(context, player)
                                                },
                                        ),
                                ),
                        )
                        .then(
                            literal<ServerCommandSource>("unlink")
                                .then(
                                    CommandManager.argument("first name", StringArgumentType.word())
                                        .then(
                                            CommandManager.argument("second name", StringArgumentType.word())
                                                .executes { context ->
                                                    val player = prepare(context)
                                                        ?: return@executes COMMAND_FAIL
                                                    roomUnlink(context, player)
                                                },
                                        ),
                                ),
                        )
                        .then(
                            literal<ServerCommandSource>("show")
                                .then(
                                    literal<ServerCommandSource>("rooms")
                                        .then(
                                            CommandManager.argument("name", StringArgumentType.word())
                                                .executes { context ->
                                                    val player = prepare(context)
                                                        ?: return@executes COMMAND_FAIL
                                                    roomShowRoomsWithName(context, player)
                                                },
                                        )
                                        .executes { context ->
                                            val player = prepare(context)
                                                ?: return@executes COMMAND_FAIL
                                            roomShowRooms(player)
                                        },
                                )
                                .then(
                                    literal<ServerCommandSource>("links")
                                        .then(
                                            CommandManager.argument("name", StringArgumentType.word())
                                                .executes { context ->
                                                    val player = prepare(context)
                                                        ?: return@executes COMMAND_FAIL
                                                    roomShowLinksWithName(context, player)
                                                },
                                        )
                                        .executes { context ->
                                            val player = prepare(context)
                                                ?: return@executes COMMAND_FAIL
                                            roomShowLinks(player)
                                        },
                                ),
                        ),
                ),
        )
    }

    private fun save(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val argument = context.getArgument("name", String.toString().javaClass)
        val additionalMessage = " ($argumentName: $argument)"
        storage.save(argument)
        return sendReply(player, "save.success", additionalMessage, COMMAND_SUCCESS)
    }

    private fun read(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val argument = context.getArgument("name", String.toString().javaClass)
        val additionalMessage = " ($argumentName: $argument)"
        return when (storage.read(argument)) {
            SUCCESS -> sendReply(player, "read.success", additionalMessage, COMMAND_SUCCESS)
            FAIL -> sendReply(player, "read.fail", additionalMessage, COMMAND_FAIL)
            else -> sendReply(player)
        }
    }

    private fun roomCreate(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val argument = context.getArgument("name", String.toString().javaClass)
        val pair = roomSystem.getPoints(player.id)
        val result = roomSystem.createRoom(player.id, argument)
        val additionalMessage =
            " ($argumentName: $argument $argumentPointFirst: ${pair?.first} $argumentPointSecond: ${pair?.second})"

        return when (result) {
            ALREADY -> sendReply(player, "room.create.fail.already", additionalMessage, COMMAND_FAIL)
            POINT_DOES_NOT_EXIST -> sendReply(player, "room.create.fail.exist", additionalMessage, COMMAND_FAIL)
            SUCCESS -> sendReply(player, "room.create.success", additionalMessage, COMMAND_SUCCESS)
            else -> sendReply(player)
        }
    }

    private fun roomDelete(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val argument = context.getArgument("name", String.toString().javaClass)

        return when (roomSystem.deleteRoom(argument)) {
            ROOM_DOES_NOT_EXIST -> sendReply(player, "room.delete.fail.exist", COMMAND_FAIL)
            ONE_WAY_LINK -> sendReply(player, "room.delete.warning", COMMAND_WARNING)
            SUCCESS -> sendReply(player, "room.delete.success", COMMAND_SUCCESS)
            else -> sendReply(player)
        }
    }

    private fun roomDeleteAll(player: ServerPlayerEntity): Int {
        roomSystem.deleteAllRooms()
        return sendReply(player, "room.delete_all.success", COMMAND_SUCCESS)
    }

    private fun roomLink(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val firstArgument = context.getArgument("first name", String.toString().javaClass)
        val secondArgument = context.getArgument("second name", String.toString().javaClass)
        val result = roomSystem.link(player.id, firstArgument, secondArgument)
        val additionalMessage = " ($argumentName 1: $firstArgument $argumentName 2: $secondArgument)"

        return when (result) {
            SUCCESS -> sendReply(player, "room.link.success", additionalMessage, COMMAND_SUCCESS)
            SAME_ROOM -> sendReply(player, "room.link.fail.same_room", additionalMessage, COMMAND_FAIL)
            ROOM_DOES_NOT_EXIST -> sendReply(player, "room.link.fail.exist.room", additionalMessage, COMMAND_FAIL)
            POINT_DOES_NOT_EXIST -> sendReply(player, "room.link.fail.exist.point", additionalMessage, COMMAND_FAIL)
            ALREADY -> sendReply(player, "room.link.fail.already", additionalMessage, COMMAND_FAIL)
            else -> sendReply(player)
        }
    }

    private fun roomUnlink(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val firstArgument = context.getArgument("first name", String.toString().javaClass)
        val secondArgument = context.getArgument("second name", String.toString().javaClass)
        val result = roomSystem.unlink(firstArgument, secondArgument)
        val additionalMessage = " ($argumentName 1: $firstArgument $argumentName 2: $secondArgument)"

        return when (result) {
            SUCCESS -> sendReply(player, "room.unlink.success", additionalMessage, COMMAND_SUCCESS)
            SAME_ROOM -> sendReply(player, "room.unlink.fail.same_room", additionalMessage, COMMAND_FAIL)
            ROOM_DOES_NOT_EXIST -> sendReply(player, "room.unlink.fail.exist", additionalMessage, COMMAND_FAIL)
            ALREADY -> sendReply(player, "room.unlink.fail.already", additionalMessage, COMMAND_FAIL)
            else -> sendReply(player)
        }
    }

    private fun roomShowRooms(player: ServerPlayerEntity): Int {
        val additionalMessage = roomSystem.getAllRoomNames()
        if (additionalMessage.isEmpty()) {
            return sendReply(player, "room.show.rooms.nothing_found", COMMAND_SUCCESS)
        }
        return sendReply(
            player,
            "room.show.rooms.found",
            additionalMessage.joinToString(prefix = ": "),
            COMMAND_SUCCESS,
        )
    }

    private fun roomShowRoomsWithName(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val argument = context.getArgument("name", String.toString().javaClass)
        val room = roomSystem.getRoom(argument)
        room ?: return sendReply(player, "room.show.rooms.with_name.fail.exist", COMMAND_FAIL)
        val additionalMessage = " ($argumentPointFirst: ${room.firstPoint} $argumentPointSecond: ${room.secondPoint})"
        return sendReply(player, "room.show.rooms.with_name.found", additionalMessage, COMMAND_SUCCESS)
    }

    private fun roomShowLinks(player: ServerPlayerEntity): Int {
        val names = roomSystem.getAllLinkNames()

        if (names.isEmpty()) {
            return sendReply(player, "room.show.links.nothing_found", COMMAND_SUCCESS)
        }
        return sendReply(player, "room.show.links.found", names.joinToString(prefix = ": "), COMMAND_SUCCESS)
    }

    private fun roomShowLinksWithName(context: CommandContext<ServerCommandSource>, player: ServerPlayerEntity): Int {
        val argument = context.getArgument("name", String.toString().javaClass)
        val names = roomSystem.getAllLinkNamesByRoomName(argument)
            ?: return sendReply(player, "room.show.links.with_name.fail.exist", COMMAND_FAIL)

        if (names.isEmpty()) return sendReply(player, "room.show.links.with_name.nothing_found", COMMAND_SUCCESS)

        return sendReply(player, "room.show.links.with_name.found", names.joinToString(prefix = ": "), COMMAND_SUCCESS)
    }

    private fun prepare(context: CommandContext<ServerCommandSource>): ServerPlayerEntity? {
        val player = context.source.player
        if (player == null) {
            context.source.sendMessage(translatable("command.$MOD_ID.error.player_null"))
        }

        player?.sendMessage(Text.empty())
        return player
    }

    private fun sendReply(player: ServerPlayerEntity, path: String, message: String, type: Int): Int {
        player.sendMessage(translatable("command.$MOD_ID.$path").add(message))
        return type
    }

    private fun sendReply(player: ServerPlayerEntity, path: String, type: Int): Int {
        player.sendMessage(translatable("command.$MOD_ID.$path"))
        return type
    }

    private fun sendReply(player: ServerPlayerEntity): Int {
        player.sendMessage(translatable("command.$MOD_ID.error.unknown"))
        return COMMAND_UNKNOWN
    }
}
