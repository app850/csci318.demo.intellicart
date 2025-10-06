package com.intellicart.aiassistantservice.config;

import com.intellicart.aiassistantservice.agent.AssistantTools;
import com.intellicart.aiassistantservice.agent.CustomerAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    private final ChatLanguageModel model;
    private final AssistantTools tools;

    public AgentConfiguration(ChatLanguageModel model, AssistantTools tools) {
        this.model = model;
        this.tools = tools;
    }

    @Bean
    public CustomerAgent baseCustomerAgent() {
        return AiServices.builder(CustomerAgent.class)
                .chatLanguageModel(model)
                .tools(tools)
                .build();
    }
}
