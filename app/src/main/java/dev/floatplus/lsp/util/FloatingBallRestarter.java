package dev.floatplus.lsp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Restarts only the ColorOS floating-ball package, using the device's root manager. */
public final class FloatingBallRestarter {
    private static final String COMMAND =
            "am force-stop --user current com.coloros.floatassistant"
                    + " && am start --user current -n com.coloros.floatassistant/.settings.GABallSettingActivity";

    private FloatingBallRestarter() {}

    /** Runs on a worker thread; the first invocation may show the root authorization dialog. */
    public static void restart() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("su", "-c", COMMAND).redirectErrorStream(true).start();
        try {
            process.getOutputStream().close();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                throw new IOException("Root authorization or floating-ball restart timed out");
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 8192) output.append(line).append('\n');
                }
            }
            if (process.exitValue() != 0 || output.indexOf("Error:") >= 0
                    || output.indexOf("Exception") >= 0) {
                throw new IOException("Restart failed (exit " + process.exitValue() + "): " + output);
            }
        } finally {
            process.destroy();
        }
    }
}
