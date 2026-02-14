package com.getoffer.test;

import com.getoffer.domain.session.adapter.repository.ISessionMessageRepository;
import com.getoffer.domain.session.adapter.repository.ISessionTurnRepository;
import com.getoffer.trigger.application.sse.ChatSseEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class ChatSseEventMapperTest {

    private ChatSseEventMapper mapper;

    @BeforeEach
    public void setUp() {
        this.mapper = new ChatSseEventMapper(
                mock(ISessionMessageRepository.class),
                mock(ISessionTurnRepository.class)
        );
    }

    @Test
    public void shouldNormalizeNodeIdAndTaskNameFromLegacyTaskNodeId() {
        Map<String, Object> metadata = mapper.normalizeMetadata(Map.of(
                "taskNodeId", "collect_data",
                "status", "RUNNING"
        ));

        assertEquals("collect_data", metadata.get("taskNodeId"));
        assertEquals("collect_data", metadata.get("nodeId"));
        assertEquals("collect_data", metadata.get("taskName"));
    }

    @Test
    public void shouldPreferExplicitTaskNameWhenProvided() {
        Map<String, Object> metadata = mapper.normalizeMetadata(Map.of(
                "nodeId", "collect_data",
                "taskNodeId", "legacy_node",
                "taskName", "抓取数据"
        ));

        assertEquals("collect_data", metadata.get("nodeId"));
        assertEquals("抓取数据", metadata.get("taskName"));
        assertEquals("legacy_node", metadata.get("taskNodeId"));
    }

    @Test
    public void shouldReturnEmptyMapWhenMetadataMissing() {
        assertTrue(mapper.normalizeMetadata(null).isEmpty());
        assertTrue(mapper.normalizeMetadata(Map.of()).isEmpty());
    }
}
