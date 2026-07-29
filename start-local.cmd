@echo off
set JAVA_HOME=D:\java
cd /d D:\projectt\chen-ai-agent
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local"
