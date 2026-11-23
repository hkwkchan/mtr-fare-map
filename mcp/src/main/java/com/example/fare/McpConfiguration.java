package com.example.fare;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("mcp")
public class McpConfiguration {
    @Bean
    ToolCallbackProvider mtrTools(DataService dataService) {
        return MethodToolCallbackProvider.builder().toolObjects(new McpTools(dataService)).build();
    }
}
