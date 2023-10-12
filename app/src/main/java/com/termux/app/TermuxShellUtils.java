package com.termux.app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TermuxShellUtils {

    @NonNull
    public static String[] setupShellCommandArguments(@NonNull String executable, @Nullable String[] arguments) {
        // The file to execute may either be:
        // - An elf file, in which we execute it directly.
        // - A script file without shebang, which we execute with our standard shell $PREFIX/bin/sh instead of the
        //   system /system/bin/sh. The system shell may vary and may not work at all due to LD_LIBRARY_PATH.
        // - A file with shebang, which we try to handle with e.g. /bin/foo -> $PREFIX/bin/foo.
        String interpreter = null;
        try {
            File file = new File(executable);
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[256];
                int bytesRead = in.read(buffer);
                if (bytesRead > 4) {
                    if (buffer[0] == 0x7F && buffer[1] == 'E' && buffer[2] == 'L' && buffer[3] == 'F') {
                        // Elf file, do nothing.
                    } else if (buffer[0] == '#' && buffer[1] == '!') {
                        // Try to parse shebang.
                        StringBuilder builder = new StringBuilder();
                        for (int i = 2; i < bytesRead; i++) {
                            char c = (char) buffer[i];
                            if (c == ' ' || c == '\n') {
                                if (builder.length() == 0) {
                                    // Skip whitespace after shebang.
                                } else {
                                    // End of shebang.
                                    String shebangExecutable = builder.toString();
                                    if (shebangExecutable.startsWith("/usr") || shebangExecutable.startsWith("/bin")) {
                                        String[] parts = shebangExecutable.split("/");
                                        String binary = parts[parts.length - 1];
                                        interpreter = TermuxConstants.BIN_PATH + "/" + binary;
                                    }
                                    break;
                                }
                            } else {
                                builder.append(c);
                            }
                        }
                    } else {
                        // No shebang and no ELF, use standard shell.
                        interpreter = TermuxConstants.BIN_PATH + "/sh";
                    }
                }
            }
        } catch (IOException e) {
            // Ignore.
        }

        List<String> result = new ArrayList<>();
        if (interpreter != null) result.add(interpreter);
        result.add(executable);
        if (arguments != null) Collections.addAll(result, arguments);
        return result.toArray(new String[0]);
    }


    public static String[] setupEnvironment(boolean failsafe) {
        Map<String, String> environment = new HashMap<>();
        environment.put("HOME", TermuxConstants.HOME_PATH);
        environment.put("LANG", "en_US.UTF-8");
        String tmpDir = TermuxConstants.PREFIX_PATH + "/tmp";
        environment.put("TMP", tmpDir);
        environment.put("TMPDIR", tmpDir);
        environment.put("COLORTERM", "truecolor");
        environment.put("TERM", "xterm-256color");
        putToEnvIfInSystemEnv(environment, "PATH");
        putToEnvIfInSystemEnv(environment, "ANDROID_ASSETS");
        putToEnvIfInSystemEnv(environment, "ANDROID_DATA");
        putToEnvIfInSystemEnv(environment, "ANDROID_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_STORAGE");
        // EXTERNAL_STORAGE is needed for /system/bin/am to work on at least
        // Samsung S7 - see https://plus.google.com/110070148244138185604/posts/gp8Lk3aCGp3.
        // https://cs.android.com/android/_/android/platform/system/core/+/fc000489
        putToEnvIfInSystemEnv(environment, "EXTERNAL_STORAGE");
        putToEnvIfInSystemEnv(environment, "ASEC_MOUNTPOINT");
        putToEnvIfInSystemEnv(environment, "LOOP_MOUNTPOINT");
        putToEnvIfInSystemEnv(environment, "ANDROID_RUNTIME_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_ART_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_I18N_ROOT");
        putToEnvIfInSystemEnv(environment, "ANDROID_TZDATA_ROOT");
        putToEnvIfInSystemEnv(environment, "BOOTCLASSPATH");
        putToEnvIfInSystemEnv(environment, "DEX2OATBOOTCLASSPATH");
        putToEnvIfInSystemEnv(environment, "SYSTEMSERVERCLASSPATH");

        if (!failsafe) {
            environment.put("LD_PRELOAD", TermuxConstants.PREFIX_PATH + "/usr/lib/libtermux-exec.so");
            environment.put("PATH", TermuxConstants.PREFIX_PATH + "/usr/bin:" + System.getenv("PATH"));
        }

        List<String> environmentList = new ArrayList<>(environment.values());
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            environmentList.add(entry.getKey() + "=" + entry.getValue());
        }
        Collections.sort(environmentList);
        return environmentList.toArray(new String[0]);
    }

    private static void putToEnvIfInSystemEnv(@NonNull Map<String, String> environment, @NonNull String name) {
        String value = System.getenv(name);
        if (value != null) {
            environment.put(name, value);
        }
    }


}
