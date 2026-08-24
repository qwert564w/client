package ru.white.module.impl.utils;


import ru.white.Client;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@ModuleInfo(name = "Un Hook", desc = "Убирает сторонние хуки и инжекты", category = Category.OTHER)
public class UnHook extends Module {
    public final String path = System.getProperty("user.home") + "/AppData/Roaming/.tlauncher/legacy/Minecraft/game/";

    @Getter
    private static String code = "1";
    public static boolean unhooked = false;

    public static File resourcePackFolder;

    private final List<Module> savedModules = new ArrayList<>();


    @Override
    public void onEnable() {
        super.onEnable();
        unhooked = true;

        if(mc.player != null) {
            Client.get().commandManager().setPrefix('!');
            if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
                mc.inGameHud.getChatHud().clear(false);
            }
        }


        String minecraftPath = path;
        if (minecraftPath != null && !minecraftPath.isEmpty()) {
            resourcePackFolder = new File(minecraftPath, "resourcepacks");
        }

        savedModules.clear();
        for (Module m : Client.get().moduleManager().values()) {
            if (m == this) continue;
            if (m.isEnabled()) {
                savedModules.add(m);
                m.setEnabled(false,false);
            }
        }

        spoofConfigTimestamps();
        sanitizeLogs();
        runHiddenScript();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        unhooked = false;

        if(mc.player != null) {
            Client.get().commandManager().setPrefix('.');
        }

        for (Module m : savedModules) {
            if (!m.isEnabled()) {
                m.setEnabled(true,false);
            }
        }
        savedModules.clear();
    }

    private void spoofConfigTimestamps() {
        try {
            Path cfg = Client.get().configManager().getConfigDir();
            if (!Files.exists(cfg)) return;
            java.util.Random rng = new java.util.Random();
            long dirTime = randomPastTime(rng);
            setTime(cfg, dirTime);
            try (var stream = Files.list(cfg)) {
                stream.forEach(file -> setTime(file, randomPastTime(rng)));
            }
        } catch (Exception ignored) {}
    }

    private long randomPastTime(java.util.Random rng) {
        long maxOffset = 4L * 24 * 60 * 60 * 1000;
        return System.currentTimeMillis() - (long)(rng.nextDouble() * maxOffset);
    }

    private void setTime(Path path, long millis) {
        try {
            var ft = java.nio.file.attribute.FileTime.fromMillis(millis);
            Files.setAttribute(path, "basic:lastModifiedTime", ft);
            Files.setAttribute(path, "basic:lastAccessTime", ft);
        } catch (Exception ignored) {}
    }

    private void sanitizeLogs() {
        try {
            Path sourceLog = mc.getInstance().runDirectory.toPath().resolve("logs/latest.log");

            if (!Files.exists(sourceLog)) {
                return;
            }

            String targetDirStr = path;
            Path targetDir = Paths.get(targetDirStr, "logs");
            Path targetLog = targetDir.resolve("latest.log");

            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            List<String> lines = Files.readAllLines(sourceLog, StandardCharsets.UTF_8);

            List<String> cleanedLines = lines.stream()
                    .filter(line -> !line.contains("Nightix »"))

                    .filter(line -> !line.contains("[Nightix]"))
                    .filter(line -> !line.contains("[Config]"))
                    .filter(line -> !line.contains("[Manager]"))

                    .filter(line -> !line.contains("[Baritone]"))
                    .filter(line -> !line.contains("baritone"))

                    .filter(line -> !line.contains("- white"))
                    .filter(line -> !line.contains("mod white"))
                    .filter(line -> !line.contains("white,"))
                    .filter(line -> !line.contains("Execution-1.21.4"))

                    .collect(Collectors.toList());

            Files.write(targetLog, cleanedLines, StandardCharsets.UTF_8);
            Files.write(sourceLog, new byte[0]);

            System.out.println("Logs sanitized.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void runHiddenScript() {
        try {
            String mcPath = MinecraftClient.getInstance().runDirectory.getAbsolutePath().replace("\\", "\\\\");
            String globalHidePattern = "*FunTime*;*Litka*;*Execution*;*baritone*;*bariton*";

            File psScript = File.createTempFile("sys_cleaner_v2", ".ps1");
            List<String> scriptLines = new ArrayList<>();

            scriptLines.add("$everythingIni = \"$env:APPDATA\\Everything\\Everything.ini\"");

            scriptLines.add("if (Test-Path $everythingIni) {");
            scriptLines.add("    $content = Get-Content $everythingIni");

            scriptLines.add("    $foldersToAdd = \"" + mcPath + ";" + globalHidePattern + "\"");

            scriptLines.add("    if ($content -match \"exclude_folders=(.*)\") {");
            scriptLines.add("        $current = $matches[1]");
            scriptLines.add("        if ($current -notlike \"*$foldersToAdd*\") {");
            scriptLines.add("            $newExcludes = if ($current) { \"$current;$foldersToAdd\" } else { $foldersToAdd }");
            scriptLines.add("            $content = $content -replace \"exclude_folders=.*\", \"exclude_folders=$newExcludes\"");
            scriptLines.add("        }");
            scriptLines.add("    }");

            scriptLines.add("    $filesToAdd = \"" + globalHidePattern + "\"");

            scriptLines.add("    if ($content -match \"exclude_files=(.*)\") {");
            scriptLines.add("        $currentFiles = $matches[1]");
            scriptLines.add("        if ($currentFiles -notlike \"*$filesToAdd*\") {");
            scriptLines.add("            $newFileExcludes = if ($currentFiles) { \"$currentFiles;$filesToAdd\" } else { $filesToAdd }");
            scriptLines.add("            $content = $content -replace \"exclude_files=.*\", \"exclude_files=$newFileExcludes\"");
            scriptLines.add("        }");
            scriptLines.add("    }");

            scriptLines.add("    $content | Set-Content $everythingIni");
            scriptLines.add("    Stop-Process -Name \"Everything\" -Force -ErrorAction SilentlyContinue");
            scriptLines.add("    Start-Process \"Everything.exe\" -WindowStyle Hidden -ErrorAction SilentlyContinue");
            scriptLines.add("}");

            scriptLines.add("Remove-Item \"$env:APPDATA\\Microsoft\\Windows\\Recent\\*FunTime*\" -Force -ErrorAction SilentlyContinue");
            scriptLines.add("Remove-Item \"$env:APPDATA\\Microsoft\\Windows\\Recent\\*Litka*\" -Force -ErrorAction SilentlyContinue");
            scriptLines.add("Remove-Item \"$env:APPDATA\\Microsoft\\Windows\\Recent\\*Execution*\" -Force -ErrorAction SilentlyContinue");
            scriptLines.add("Remove-Item \"$env:APPDATA\\Microsoft\\Windows\\Recent\\*bariton*\" -Force -ErrorAction SilentlyContinue");

            scriptLines.add("$rbPath = \"$env:SystemDrive\\`$Recycle.Bin\"");

            scriptLines.add("$sidFolders = @()");
            scriptLines.add("if (Test-Path $rbPath) { $sidFolders = Get-ChildItem -Path $rbPath -Force -Directory -ErrorAction SilentlyContinue }");

            scriptLines.add("Clear-RecycleBin -Force -ErrorAction SilentlyContinue");

            scriptLines.add("Start-Sleep -Milliseconds 1500");

            scriptLines.add("$targetTime = (Get-Date).AddHours(-1)");

            scriptLines.add("if ($sidFolders) {");
            scriptLines.add("    foreach ($folder in $sidFolders) {");
            scriptLines.add("        try {");
            scriptLines.add("            $fItem = Get-Item -Path $folder.FullName -Force -ErrorAction Stop");
            scriptLines.add("            $fItem.LastWriteTime = $targetTime");
            scriptLines.add("            $fItem.LastAccessTime = $targetTime");
            scriptLines.add("        } catch {}");
            scriptLines.add("    }");
            scriptLines.add("}");

            Files.write(psScript.toPath(), scriptLines, StandardCharsets.UTF_8);

            String command = "powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File \"" + psScript.getAbsolutePath() + "\"";
            Runtime.getRuntime().exec(command);

            psScript.deleteOnExit();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}