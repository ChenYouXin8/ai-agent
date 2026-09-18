package io.github.chenyouxin8.chenaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChenAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChenAiAgentApplication.class, args);
    }

}
