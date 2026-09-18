package io.github.chenyouxin8.chenaiagent;

import io.modelcontextprotocol.client.transport.ServerParameters;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 MCP stdio 客户端配置：
 * 1. 正确的配置前缀是 spring.ai.mcp.client.stdio.servers-configuration（不是 spring.ai.mcp.client.servers-configuration）；
 * 2. 未配置 servers-configuration 时返回空 Map，保证缺少 mcp-servers.json 时应用也能启动；
 * 3. 示例文件能被正确解析为两个 stdio 服务。
 */
class McpStdioConfigurationTest {

    private static final String PREFIX = "spring.ai.mcp.client.stdio";

    @Test
    void bindsServersConfigurationUnderCorrectPrefix() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(
                Map.of(PREFIX + ".servers-configuration", "classpath:mcp-servers.example.json"));
        Binder binder = new Binder(List.of(source), null, ApplicationConversionService.getSharedInstance());

        McpStdioClientProperties props = binder.bind(PREFIX, Bindable.of(McpStdioClientProperties.class))
                .orElseThrow(() -> new AssertionError("MCP stdio properties should bind"));

        Map<String, ServerParameters> servers = props.toServerParameters();
        assertEquals(2, servers.size(), "示例文件应解析出两个 MCP 服务");
        assertTrue(servers.containsKey("amap-maps"));
        assertTrue(servers.containsKey("chen-image-search-mcp-server"));
        assertNotNull(servers.get("amap-maps").getEnv());
    }

    @Test
    void emptyWhenServersConfigurationMissing() {
        // 基础 application.yml 不配置 servers-configuration：Resource 为 null，必须返回空 Map 而不是抛异常
        McpStdioClientProperties props = new McpStdioClientProperties();
        assertTrue(props.toServerParameters().isEmpty());
    }

    @Test
    void exampleFileIsPresentOnClasspath() {
        assertTrue(new ClassPathResource("mcp-servers.example.json").exists(),
                "mcp-servers.example.json 必须作为无密钥模板存在于 classpath");
    }
}
