package com.resumeai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private String frontendBaseUrl;
    private Storage storage = new Storage();
    private AiService aiService = new AiService();
    private Mail mail = new Mail();
    private Seed seed = new Seed();
    private Invitations invitations = new Invitations();

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenMinutes = 30;
        private long refreshTokenDays = 7;
    }

    @Getter @Setter
    public static class Cors {
        private List<String> allowedOrigins;
    }

    @Getter @Setter
    public static class Storage {
        private String root = "./storage";
    }

    @Getter @Setter
    public static class AiService {
        private String baseUrl;
        private String apiKey;
        /** When true, the backend launches the Python AI service as a child process on startup. */
        private boolean autoStart = false;
        /** Filesystem path to the ai-service directory, relative to the backend working dir or absolute. */
        private String directory = "../ai-service";
    }

    @Getter @Setter
    public static class Mail {
        private boolean enabled = true;
        private String from;
        private String fromName;
    }

    @Getter @Setter
    public static class Seed {
        private String adminEmail;
        private String adminPassword;
        private String adminName;
    }

    @Getter @Setter
    public static class Invitations {
        private long expiryHours = 72;
    }
}
