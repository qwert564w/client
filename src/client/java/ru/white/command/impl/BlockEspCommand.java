package ru.white.command.impl;

import ru.white.command.Command;
import ru.white.module.impl.render.BlockEsp;
import ru.white.utils.math.ChatUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BlockEspCommand extends Command {

    public BlockEspCommand() {
        super("blockesp", ".blockesp <add|remove|list|clear> [блок]", "Управление Block ESP");
    }

    @Override
    
    public void execute(String[] args) {
        if (args.length < 1) {
            showHelp();
            return;
        }

        BlockEsp esp = BlockEsp.getInstance();
        if (esp == null) {
            ChatUtils.addChatMessage("§cBlock ESP модуль не найден");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("§7Использование: .blockesp add §f<блок>"); return; }
                Block block = findBlock(args[1]);
                if (block == null) { ChatUtils.addChatMessage("§cБлок §f" + args[1] + "§c не найден"); return; }
                if (esp.addBlock(block)) {
                    esp.rescan();
                    ChatUtils.addChatMessage("§a" + args[1] + "§r добавлен в Block ESP");
                } else {
                    ChatUtils.addChatMessage("§7" + args[1] + " уже добавлен");
                }
            }
            case "remove" -> {
                if (args.length < 2) { ChatUtils.addChatMessage("§7Использование: .blockesp remove §f<блок>"); return; }
                Block block = findBlock(args[1]);
                if (block == null) { ChatUtils.addChatMessage("§cБлок §f" + args[1] + "§c не найден"); return; }
                if (esp.removeBlock(block)) {
                    ChatUtils.addChatMessage("§c" + args[1] + "§r удалён из Block ESP");
                } else {
                    ChatUtils.addChatMessage("§7" + args[1] + " не был добавлен");
                }
            }
            case "list" -> {
                if (esp.getTargetBlocks().isEmpty()) {
                    ChatUtils.addChatMessage("§7Список блоков пуст");
                } else {
                    String names = esp.getTargetBlocks().stream()
                            .map(b -> Registries.BLOCK.getId(b).getPath())
                            .collect(Collectors.joining("§7, §a"));
                    ChatUtils.addChatMessage("§7Блоки §8[" + esp.getTargetBlocks().size() + "§8]§r: §a" + names);
                }
            }
            case "clear" -> {
                int count = esp.clearBlocks();
                ChatUtils.addChatMessage("§7Удалено §f" + count + "§7 блоков из Block ESP");
            }
            default -> showHelp();
        }
    }

    
    private Block findBlock(String name) {
        String raw = name.toLowerCase();
        Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null || !Registries.BLOCK.containsId(id)) return null;
        Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR && !raw.equals("air") && !raw.equals("minecraft:air")) return null;
        return block;
    }

    @Override
    public List<String> getSuggestions(String subPrefix) {
        String[] parts = subPrefix.split(" ", 2);
        String sub = parts[0].toLowerCase();

        // первый аргумент — подкоманда
        if (parts.length <= 1) {
            return List.of("add", "remove", "list", "clear").stream()
                    .filter(s -> s.startsWith(sub))
                    .collect(Collectors.toList());
        }

        // второй аргумент — имя блока (только для add/remove)
        if (sub.equals("add") || sub.equals("remove")) {
            String blockPrefix = parts[1].toLowerCase();
            List<String> out = new ArrayList<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                String path = id.getPath();
                if (path.startsWith(blockPrefix)) {
                    out.add(sub + " " + path);
                    if (out.size() >= 30) break;
                }
            }
            return out;
        }

        return List.of();
    }

    
    private void showHelp() {
        ChatUtils.addChatMessage("§7.blockesp add/remove §f<блок> §8| §7.blockesp list §8| §7.blockesp clear");
    }
}
