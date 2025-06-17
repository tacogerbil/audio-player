package de.maxhenkel.audioplayer.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.maxhenkel.admiral.annotations.Command;
import de.maxhenkel.admiral.annotations.Name;
import de.maxhenkel.admiral.annotations.RequiresPermission;
import de.maxhenkel.audioplayer.AudioManager;
import de.maxhenkel.audioplayer.AudioPlayer;
import de.maxhenkel.audioplayer.CustomSound;
import de.maxhenkel.audioplayer.Filebin;
import de.maxhenkel.audioplayer.PlayerType;
import de.maxhenkel.audioplayer.webserver.UrlUtils;
import de.maxhenkel.audioplayer.webserver.WebServer;
import de.maxhenkel.audioplayer.webserver.WebServerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock; // Added import
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity; // Added import
import net.minecraft.nbt.CompoundTag; // Added import
import net.minecraft.nbt.ListTag; // Added import
import net.minecraft.nbt.Tag; // Added import

import javax.sound.sampled.UnsupportedAudioFileException;
import java.net.UnknownHostException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional; // Added import
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command("audioplayer")
public class UploadCommands {

    public static final Pattern SOUND_FILE_PATTERN = Pattern.compile("^[a-z0-9_ -]+.((wav)|(mp3))$", Pattern.CASE_INSENSITIVE);

    @RequiresPermission("audioplayer.upload")
    @Command
    public void audioPlayer(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
                        Component.literal("Upload audio via Filebin ")
                                .append(Component.literal("here").withStyle(style -> {
                                    return style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/audioplayer upload"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to show more")));
                                }).withStyle(ChatFormatting.GREEN))
                                .append(".")
                , false);
        context.getSource().sendSuccess(() ->
                        Component.literal("Upload audio with access to the servers file system ")
                                .append(Component.literal("here").withStyle(style -> {
                                    return style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/audioplayer serverfile"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to show more")));
                                }).withStyle(ChatFormatting.GREEN))
                                .append(".")
                , false);
        context.getSource().sendSuccess(() ->
                        Component.literal("Upload audio from a URL ")
                                .append(Component.literal("here").withStyle(style -> {
                                    return style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/audioplayer url"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to show more")));
                                }).withStyle(ChatFormatting.GREEN))
                                .append(".")
                , false);
    }

    @RequiresPermission("audioplayer.upload")
    @Command("upload")
    @Command("filebin")
    public void filebin(CommandContext<CommandSourceStack> context) {
        UUID uuid = UUID.randomUUID();
        String uploadURL = Filebin.getBin(uuid);

        MutableComponent msg = Component.literal("Click ")
                .append(Component.literal("this link")
                        .withStyle(style -> {
                            return style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uploadURL))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open")));
                        })
                        .withStyle(ChatFormatting.GREEN)
                )
                .append(" and upload your sound as ")
                .append(Component.literal("mp3").withStyle(ChatFormatting.GRAY))
                .append(" or ")
                .append(Component.literal("wav").withStyle(ChatFormatting.GRAY))
                .append(".\n")
                .append("Once you have uploaded the file, click ")
                .append(Component.literal("here")
                        .withStyle(style -> {
                            return style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/audioplayer filebin " + uuid))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to confirm upload")));
                        })
                        .withStyle(ChatFormatting.GREEN)
                )
                .append(".");

        context.getSource().sendSuccess(() -> msg, false);
    }

    @RequiresPermission("audioplayer.upload")
    @Command("filebin")
    public void filebinUpload(CommandContext<CommandSourceStack> context, @Name("id") UUID sound) {
        new Thread(() -> {
            try {
                context.getSource().sendSuccess(() -> Component.literal("Downloading sound, please wait..."), false);
                Filebin.downloadSound(context.getSource().getServer(), sound);
                context.getSource().sendSuccess(() -> sendUUIDMessage(sound, Component.literal("Successfully downloaded sound.")), false);
            } catch (Exception e) {
                AudioPlayer.LOGGER.warn("{} failed to download a sound: {}", context.getSource().getTextName(), e.getMessage());
                context.getSource().sendFailure(Component.literal("Failed to download sound: %s".formatted(e.getMessage())));
            }
        }).start();
    }

    @RequiresPermission("audioplayer.upload")
    @Command("url")
    public void url(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
                        Component.literal("If you have a direct link to a ")
                                .append(Component.literal(".mp3").withStyle(ChatFormatting.GRAY))
                                .append(" or ")
                                .append(Component.literal(".wav").withStyle(ChatFormatting.GRAY))
                                .append(" file, enter the following command: ")
                                .append(Component.literal("/audioplayer url <link-to-your-file>").withStyle(ChatFormatting.GRAY).withStyle(style -> {
                                    return style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/audioplayer url "))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to fill in the command")));
                                }))
                                .append(".")
                , false);
    }

    @RequiresPermission("audioplayer.upload")
    @Command("url")
    public void urlUpload(CommandContext<CommandSourceStack> context, @Name("url") String url) {
        UUID sound = UUID.randomUUID();
        new Thread(() -> {
            try {
                context.getSource().sendSuccess(() -> Component.literal("Downloading sound, please wait..."), false);
                AudioManager.saveSound(context.getSource().getServer(), sound, url);
                context.getSource().sendSuccess(() -> sendUUIDMessage(sound, Component.literal("Successfully downloaded sound.")), false);
            } catch (UnknownHostException e) {
                AudioPlayer.LOGGER.warn("{} failed to download a sound: {}", context.getSource().getTextName(), e.toString());
                context.getSource().sendFailure(Component.literal("Failed to download sound: Unknown host"));
            } catch (UnsupportedAudioFileException e) {
                AudioPlayer.LOGGER.warn("{} failed to download a sound: {}", context.getSource().getTextName(), e.toString());
                context.getSource().sendFailure(Component.literal("Failed to download sound: Invalid file format"));
            } catch (Exception e) {
                AudioPlayer.LOGGER.warn("{} failed to download a sound: {}", context.getSource().getTextName(), e.toString());
                context.getSource().sendFailure(Component.literal("Failed to download sound: %s".formatted(e.getMessage())));
            }
        }).start();
    }

    @RequiresPermission("audioplayer.upload")
    @Command("urlwithname")
    public void urlUploadWithName(CommandContext<CommandSourceStack> context, @Name("url") String url, @Name("custom_name") String customName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException(); // Added player object
        ItemStack itemInHand = player.getItemInHand(InteractionHand.MAIN_HAND); // Get item in hand

        PlayerType type = PlayerType.fromItemStack(itemInHand); // Check player type
        if (type == null) { // If not valid item, send message and return
            sendInvalidHandItemMessage(context, itemInHand); // Call send invalid message
            return; // Return
        }

        UUID sound = UUID.randomUUID();
        new Thread(() -> {
            try {
                context.getSource().sendSuccess(() -> Component.literal("Downloading sound, please wait..."), false);
                AudioManager.saveSound(context.getSource().getServer(), sound, url);

                // Apply the sound to the item and set custom name
                CustomSound customSound = new CustomSound(sound, null, false); // Create custom sound
                ApplyCommands.applyToItem(context, itemInHand, type, customSound, customName); // Apply to item with custom name

                context.getSource().sendSuccess(() -> sendUUIDMessage(sound, Component.literal("Successfully downloaded and applied sound with name '%s'.".formatted(customName))), false);
            } catch (UnknownHostException e) {
                AudioPlayer.LOGGER.warn("{} failed to download a sound: {}", context.getSource().getTextName(), e.toString());
                context.getSource().sendFailure(Component.literal("Failed to download sound: Unknown host"));
            } catch (UnsupportedAudioFileException e) {
                AudioPlayer.LOGGER.warn("{} failed to download a sound: {}", context.getSource().getTextName(), e.toString());
                context.getSource().sendFailure(Component.literal("Failed to download sound: Invalid file format"));
            } catch (CommandSyntaxException e) { // Catch CommandSyntaxException from applyToItem
                AudioPlayer.LOGGER.warn("{} failed to apply sound: {}", context.getSource().getTextName(), e.getMessage());
                context.getSource().sendFailure(Component.literal("Failed to apply sound: %s".formatted(e.getMessage())));
            } catch (Exception e) {
                AudioPlayer.LOGGER.warn("{} failed to download or apply a sound: {}", context.getSource().getTextName(), e.toString());
                context.getSource().sendFailure(Component.literal("Failed to download or apply sound: %s".formatted(e.getMessage())));
            }
        }).start();
    }


    @RequiresPermission("audioplayer.upload")
    @Command("web")
    public void web(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WebServer webServer = WebServerEvents.getWebServer();
        if (webServer == null) {
            context.getSource().sendFailure(Component.literal("Web server is not running"));
            return;
        }

        UUID token = webServer.getTokenManager().generateToken(context.getSource().getPlayerOrException().getUUID());

        String uploadUrl = UrlUtils.generateUploadUrl(token);

        if (uploadUrl != null) {
            context.getSource().sendSuccess(() ->
                            Component.literal("Click ")
                                    .append(Component.literal("here").withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE).withStyle(style -> {
                                        return style
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uploadUrl))
                                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open")));
                                    }))
                                    .append(" to upload your sound.")
                    , false);
            return;
        }

        context.getSource().sendSuccess(() ->
                        Component.literal("Visit the website and use ")
                                .append(Component.literal("this token").withStyle(ChatFormatting.GREEN).withStyle(style -> {
                                    return style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, token.toString()))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy")));
                                }))
                                .append(".")
                , false);
    }

    @RequiresPermission("audioplayer.upload")
    @Command("serverfile")
    public void serverFile(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() ->
                        Component.literal("Upload a ")
                                .append(Component.literal(".mp3").withStyle(ChatFormatting.GRAY))
                                .append(" or ")
                                .append(Component.literal(".wav").withStyle(ChatFormatting.GRAY))
                                .append(" file to ")
                                .append(Component.literal(AudioManager.getUploadFolder().toAbsolutePath().toString()).withStyle(ChatFormatting.GRAY))
                                .append(" on the server and run the command ")
                                .append(Component.literal("/audioplayer serverfile \"yourfile.mp3\"").withStyle(ChatFormatting.GRAY).withStyle(style -> {
                                    return style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/audioplayer serverfile "))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to fill in the command")));
                                }))
                                .append(".")
                , false);
    }

    @RequiresPermission("audioplayer.upload")
    @Command("serverfile")
    public void serverFileUpload(CommandContext<CommandSourceStack> context, @Name("filename") String fileName) {
        Matcher matcher = SOUND_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            context.getSource().sendFailure(Component.literal("Invalid file name! Valid characters are ")
                    .append(Component.literal("A-Z").withStyle(ChatFormatting.GRAY))
                    .append(", ")
                    .append(Component.literal("0-9").withStyle(ChatFormatting.GRAY))
                    .append(", ")
                    .append(Component.literal("_").withStyle(ChatFormatting.GRAY))
                    .append(" and ")
                    .append(Component.literal("-").withStyle(ChatFormatting.GRAY))
                    .append(". The name must also end in ")
                    .append(Component.literal(".mp3").withStyle(ChatFormatting.GRAY))
                    .append(" or ")
                    .append(Component.literal(".wav").withStyle(ChatFormatting.GRAY))
                    .append(".")
            );
            return;
        }
        UUID uuid = UUID.randomUUID();
        new Thread(() -> {
            Path file = AudioManager.getUploadFolder().resolve(fileName);
            try {
                AudioManager.saveSound(context.getSource().getServer(), uuid, file);
                context.getSource().sendSuccess(() -> sendUUIDMessage(uuid, Component.literal("Successfully copied sound.")), false);
                context.getSource().sendSuccess(() -> Component.literal("Deleted temporary file ").append(Component.literal(fileName).withStyle(ChatFormatting.GRAY)).append("."), false);
            } catch (NoSuchFileException e) {
                context.getSource().sendFailure(Component.literal("Could not find file ").append(Component.literal(fileName).withStyle(ChatFormatting.GRAY)).append("."));
            } catch (Exception e) {
                AudioPlayer.LOGGER.warn("{} failed to copy a sound: {}", context.getSource().getTextName(), e.getMessage());
                context.getSource().sendFailure(Component.literal("Failed to copy sound: %s".formatted(e.getMessage())));
            }
        }).start();
    }

    public static MutableComponent sendUUIDMessage(UUID soundID, MutableComponent component) {
        return component.append(" ")
                .append(ComponentUtils.wrapInSquareBrackets(Component.literal("Copy ID"))
                        .withStyle(style -> {
                            return style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, soundID.toString()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Copy sound ID")));
                        })
                        .withStyle(ChatFormatting.GREEN)
                )
                .append(" ")
                .append(ComponentUtils.wrapInSquareBrackets(Component.literal("Put on item"))
                        .withStyle(style -> {
                            return style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/audioplayer apply %s".formatted(soundID.toString())))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Put the sound on an item")));
                        })
                        .withStyle(ChatFormatting.GREEN)
                );
    }

    // Helper method from ApplyCommands.java to avoid code duplication
    private static void sendInvalidHandItemMessage(CommandContext<CommandSourceStack> context, ItemStack invalidItem) {
        if (invalidItem.isEmpty()) {
            context.getSource().sendFailure(Component.literal("You don't have an item in your main hand"));
            return;
        }
        context.getSource().sendFailure(Component.literal("The item in your main hand can not have custom audio"));
    }
}
