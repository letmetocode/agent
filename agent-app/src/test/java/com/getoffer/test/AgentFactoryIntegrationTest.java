package com.getoffer.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getoffer.config.AgentAiCoreConfig;
import com.getoffer.domain.agent.adapter.factory.IAgentFactory;
import com.getoffer.domain.agent.adapter.repository.IAgentRegistryRepository;
import com.getoffer.domain.agent.adapter.repository.IAgentToolCatalogRepository;
import com.getoffer.domain.agent.adapter.repository.IAgentToolRelationRepository;
import com.getoffer.domain.agent.model.entity.AgentRegistryEntity;
import com.getoffer.domain.agent.model.entity.AgentToolCatalogEntity;
import com.getoffer.domain.agent.model.entity.AgentToolRelationEntity;
import com.getoffer.infrastructure.ai.AgentAdvisorFactory;
import com.getoffer.infrastructure.ai.AgentFactoryImpl;
import com.getoffer.infrastructure.mcp.McpClientManager;
import com.getoffer.infrastructure.util.JsonCodec;
import com.getoffer.types.enums.ToolTypeEnum;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agent factory integration test: verifies ChatClient creation and memory behavior.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AgentFactoryIntegrationTest.TestConfig.class)
public class AgentFactoryIntegrationTest {

    @Autowired
    private IAgentFactory agentFactory;

    @Test
    public void shouldReturnChatClientWithMemory() {
        ChatClient client = agentFactory.createAgent("chat", "conv-1");
        Assert.assertNotNull(client);

        client.prompt("My name is Alice.").call().content();
        String response = client.prompt("What is my name?").call().content();

        Assert.assertTrue("Response should contain remembered name", response.contains("Alice"));
    }

    @Configuration
    @Import(AgentAiCoreConfig.class)
    public static class TestConfig {

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public JsonCodec jsonCodec(ObjectMapper objectMapper) {
            return new JsonCodec(objectMapper);
        }

        @Bean
        public ChatMemory chatMemory() {
            return MessageWindowChatMemory.builder()
                    .chatMemoryRepository(new InMemoryChatMemoryRepository())
                    .maxMessages(20)
                    .build();
        }

        @Bean
        public ChatModel chatModel() {
            return new MemoryAwareChatModel();
        }

        @Bean
        public IAgentRegistryRepository agentRegistryRepository() {
            return new StubAgentRegistryRepository();
        }

        @Bean
        public IAgentToolRelationRepository agentToolRelationRepository() {
            return new StubAgentToolRelationRepository();
        }

        @Bean
        public IAgentToolCatalogRepository agentToolCatalogRepository() {
            return new StubAgentToolCatalogRepository();
        }

        @Bean
        public McpClientManager mcpClientManager(JsonCodec jsonCodec) {
            return new McpClientManager(jsonCodec);
        }

        @Bean
        public AgentAdvisorFactory agentAdvisorFactory(ToolCallingManager toolCallingManager,
                                                       ObjectProvider<ChatMemory> chatMemoryProvider,
                                                       ObjectProvider<VectorStore> vectorStoreProvider,
                                                       ListableBeanFactory beanFactory,
                                                       JsonCodec jsonCodec) {
            return new AgentAdvisorFactory(toolCallingManager, chatMemoryProvider, vectorStoreProvider, beanFactory, jsonCodec);
        }

        @Bean
        public IAgentFactory agentFactory(IAgentRegistryRepository agentRegistryRepository,
                                          IAgentToolRelationRepository agentToolRelationRepository,
                                          IAgentToolCatalogRepository agentToolCatalogRepository,
                                          ObjectProvider<ChatModel> chatModelProvider,
                                          ListableBeanFactory beanFactory,
                                          AgentAdvisorFactory agentAdvisorFactory,
                                          McpClientManager mcpClientManager,
                                          JsonCodec jsonCodec) {
            return new AgentFactoryImpl(agentRegistryRepository,
                    agentToolRelationRepository,
                    agentToolCatalogRepository,
                    chatModelProvider,
                    beanFactory,
                    agentAdvisorFactory,
                    mcpClientManager,
                    jsonCodec);
        }
    }

    private static final class MemoryAwareChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            String latest = "";
            if (prompt.getUserMessage() != null) {
                latest = prompt.getUserMessage().getText();
            }
            String rememberedName = extractName(prompt.getInstructions());
            String responseText = buildResponse(latest, rememberedName);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(responseText))));
        }

        private String buildResponse(String latest, String rememberedName) {
            String value = latest == null ? "" : latest;
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.contains("what is my name")) {
                if (rememberedName == null) {
                    return "I do not know.";
                }
                return "Your name is " + rememberedName + ".";
            }
            if (lower.contains("my name is")) {
                return "Got it.";
            }
            return "OK.";
        }

        private String extractName(List<Message> messages) {
            if (messages == null || messages.isEmpty()) {
                return null;
            }
            String name = null;
            for (Message message : messages) {
                if (message == null || message.getMessageType() != MessageType.USER) {
                    continue;
                }
                String text = message.getText();
                if (text == null) {
                    continue;
                }
                String lower = text.toLowerCase(Locale.ROOT);
                int index = lower.indexOf("my name is");
                if (index < 0) {
                    continue;
                }
                String tail = text.substring(index + "my name is".length()).trim();
                tail = trimPunctuation(tail);
                if (!tail.isEmpty()) {
                    name = tail;
                }
            }
            return name;
        }

        private String trimPunctuation(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            int end = value.length();
            while (end > 0) {
                char ch = value.charAt(end - 1);
                if (ch == '.' || ch == '!' || ch == '?' || ch == ',' || ch == ';' || ch == ':') {
                    end--;
                } else {
                    break;
                }
            }
            return value.substring(0, end).trim();
        }
    }

    private static final class StubAgentRegistryRepository implements IAgentRegistryRepository {

        @Override
        public AgentRegistryEntity save(AgentRegistryEntity entity) {
            return entity;
        }

        @Override
        public AgentRegistryEntity update(AgentRegistryEntity entity) {
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public AgentRegistryEntity findById(Long id) {
            if (id != null && id == 1L) {
                return buildAgent();
            }
            return null;
        }

        @Override
        public AgentRegistryEntity findByKey(String key) {
            if ("chat".equals(key)) {
                return buildAgent();
            }
            return null;
        }

        @Override
        public List<AgentRegistryEntity> findAll() {
            return Collections.singletonList(buildAgent());
        }

        @Override
        public List<AgentRegistryEntity> findByActive(Boolean isActive) {
            if (Boolean.TRUE.equals(isActive)) {
                return Collections.singletonList(buildAgent());
            }
            return Collections.emptyList();
        }

        @Override
        public List<AgentRegistryEntity> findByModelProvider(String modelProvider) {
            if ("test".equalsIgnoreCase(modelProvider)) {
                return Collections.singletonList(buildAgent());
            }
            return Collections.emptyList();
        }

        @Override
        public boolean existsByKey(String key) {
            return "chat".equals(key);
        }

        private AgentRegistryEntity buildAgent() {
            AgentRegistryEntity agent = new AgentRegistryEntity();
            agent.setId(1L);
            agent.setKey("chat");
            agent.setName("Chat Agent");
            agent.setModelProvider("test");
            agent.setModelName("test-model");
            agent.setIsActive(true);
            agent.setAdvisorConfig(buildAdvisorConfig());
            return agent;
        }

        private Map<String, Object> buildAdvisorConfig() {
            Map<String, Object> memory = new HashMap<>();
            memory.put("enabled", true);
            memory.put("type", "message");
            Map<String, Object> config = new HashMap<>();
            config.put("memory", memory);
            return config;
        }
    }

    private static final class StubAgentToolRelationRepository implements IAgentToolRelationRepository {

        @Override
        public AgentToolRelationEntity save(AgentToolRelationEntity entity) {
            return entity;
        }

        @Override
        public boolean delete(Long agentId, Long toolId) {
            return false;
        }

        @Override
        public boolean deleteByAgentId(Long agentId) {
            return false;
        }

        @Override
        public boolean deleteByToolId(Long toolId) {
            return false;
        }

        @Override
        public List<AgentToolRelationEntity> findByAgentId(Long agentId) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentToolRelationEntity> findByToolId(Long toolId) {
            return Collections.emptyList();
        }

        @Override
        public List<AgentToolRelationEntity> findAll() {
            return Collections.emptyList();
        }

        @Override
        public boolean exists(Long agentId, Long toolId) {
            return false;
        }

        @Override
        public boolean batchSave(Long agentId, List<Long> toolIds) {
            return false;
        }
    }

    private static final class StubAgentToolCatalogRepository implements IAgentToolCatalogRepository {

        @Override
        public AgentToolCatalogEntity save(AgentToolCatalogEntity entity) {
            return entity;
        }

        @Override
        public AgentToolCatalogEntity update(AgentToolCatalogEntity entity) {
            return entity;
        }

        @Override
        public boolean deleteById(Long id) {
            return false;
        }

        @Override
        public AgentToolCatalogEntity findById(Long id) {
            return null;
        }

        @Override
        public AgentToolCatalogEntity findByName(String name) {
            return null;
        }

        @Override
        public List<AgentToolCatalogEntity> findAll() {
            return new ArrayList<>();
        }

        @Override
        public List<AgentToolCatalogEntity> findByType(ToolTypeEnum type) {
            return Collections.emptyList();
        }

        @Override
        public boolean existsByName(String name) {
            return false;
        }
    }
}
