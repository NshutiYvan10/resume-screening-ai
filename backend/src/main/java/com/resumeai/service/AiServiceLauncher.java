package com.resumeai.service;

import com.resumeai.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Local-dev convenience: launches the Python AI microservice as a child process
 * when the backend starts, and terminates it on shutdown. If an AI service is
 * already reachable, it is reused and no child process is spawned.
 *
 * Enabled via {@code app.ai-service.auto-start=true}. Any failure here is logged
 * but never prevents the backend from starting — screening simply falls back to
 * the scheduled retry once an AI service becomes available.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.ai-service", name = "auto-start", havingValue = "true")
@RequiredArgsConstructor
public class AiServiceLauncher implements SmartLifecycle {

    private final AppProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    private Process process;
    private volatile boolean running;

    @Override
    public void start() {
        String baseUrl = properties.getAiService().getBaseUrl();
        if (isHealthy(baseUrl)) {
            log.info("AI service already running at {} — reusing it", baseUrl);
            running = true;
            return;
        }

        Path dir = Path.of(properties.getAiService().getDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            log.warn("AI auto-start skipped: directory not found: {}", dir);
            running = true;
            return;
        }

        String python = resolvePython(dir);
        int port = portOf(baseUrl);

        try {
            log.info("Starting AI service: {} -m uvicorn main:app (dir={}, port={})", python, dir, port);
            ProcessBuilder pb = new ProcessBuilder(
                    python, "-m", "uvicorn", "main:app", "--host", "127.0.0.1", "--port", String.valueOf(port))
                    .directory(dir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(new File(dir.toFile(), "ai-service.local.log"));
            pb.environment().put("AI_SERVICE_API_KEY", properties.getAiService().getApiKey());
            process = pb.start();

            // wait (briefly) for it to come up so the first screening doesn't race the boot
            for (int i = 0; i < 30 && process.isAlive(); i++) {
                if (isHealthy(baseUrl)) {
                    log.info("AI service is up at {} (pid {})", baseUrl, process.pid());
                    running = true;
                    return;
                }
                sleep(1000);
            }
            if (!process.isAlive()) {
                log.warn("AI service process exited early (exit {}). Check {}/ai-service.local.log",
                        process.exitValue(), dir);
            } else {
                log.warn("AI service did not report healthy within 30s; continuing anyway");
            }
        } catch (Exception e) {
            log.warn("Could not auto-start AI service ({}). Start it manually on {} if needed.",
                    e.getMessage(), baseUrl);
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        if (process != null && process.isAlive()) {
            log.info("Stopping AI service (pid {})", process.pid());
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            try {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start late and stop early relative to the web server. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private String resolvePython(Path dir) {
        Path venvPython = dir.resolve(".venv/bin/python");
        return Files.isExecutable(venvPython) ? venvPython.toString() : "python3";
    }

    private int portOf(String baseUrl) {
        try {
            int port = URI.create(baseUrl).getPort();
            return port > 0 ? port : 8001;
        } catch (Exception e) {
            return 8001;
        }
    }

    private boolean isHealthy(String baseUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
