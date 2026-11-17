package com.reqwise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
public class ReqWiseApplication {

    private static final Logger logger = LoggerFactory.getLogger(ReqWiseApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ReqWiseApplication.class);
        Environment env = app.run(args).getEnvironment();
        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {
        String protocol = "http";
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("The host name could not be determined, using `localhost` as fallback");
        }
        logger.info(
                """
                
                ----------------------------------------------------------
                \tApplication '{}' is running! Access URLs:
                \tLocal: \t\t{}://localhost:{}{}
                \tExternal: \t{}://{}:{}{}
                \tSwagger UI: \t{}://localhost:{}{}/swagger-ui.html
                \tHealth: \t{}://localhost:{}{}/api/v1/health
                \tProfile(s): \t{}
                ----------------------------------------------------------
                """,
                env.getProperty("spring.application.name"),
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath,
                protocol, serverPort, contextPath,
                protocol, serverPort, contextPath,
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles()
        );
    }
}
