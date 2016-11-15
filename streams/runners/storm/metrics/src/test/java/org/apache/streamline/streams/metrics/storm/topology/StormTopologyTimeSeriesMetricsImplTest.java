package org.apache.streamline.streams.metrics.storm.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import mockit.Mock;
import mockit.MockUp;
import org.apache.streamline.streams.layout.TopologyLayoutConstants;
import org.apache.streamline.streams.layout.component.TopologyLayout;
import org.apache.streamline.streams.metrics.TimeSeriesQuerier;
import mockit.Expectations;
import mockit.Mocked;
import org.apache.streamline.streams.metrics.storm.StormRestAPIClient;
import org.apache.streamline.streams.metrics.storm.StormTopologyUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class StormTopologyTimeSeriesMetricsImplTest {
    private StormTopologyTimeSeriesMetricsImpl stormTopologyTimeSeriesMetrics;
    @Mocked
    private TimeSeriesQuerier mockTimeSeriesQuerier;
    private Random random = new Random();
    private ObjectMapper mapper = new ObjectMapper();

    private static final String SOURCE_ID = "device";
    private static final String TOPIC_NAME = "topic";

    private TopologyLayout topology;
    private String mockedTopologyName;

    @Before
    public void setUp() throws IOException {
        topology = getTopologyLayoutForTest();

        String generatedTopologyName = StormTopologyUtil.generateStormTopologyName(topology);
        mockedTopologyName = generatedTopologyName + "-old";

        StormRestAPIClient mockClient = createMockStormRestAPIClient(mockedTopologyName);

        stormTopologyTimeSeriesMetrics = new StormTopologyTimeSeriesMetricsImpl(mockClient);
        stormTopologyTimeSeriesMetrics.setTimeSeriesQuerier(mockTimeSeriesQuerier);
    }

    private StormRestAPIClient createMockStormRestAPIClient(final String mockTopologyName) {
        return new MockUp<StormRestAPIClient>() {
                @Mock
                public Map getTopologySummary() {
                    Map<String, Object> topologySummary = new HashMap<>();
                    List<Map<String, Object>> topologies = new ArrayList<>();
                    topologySummary.put(StormMetricsConstant.TOPOLOGY_SUMMARY_JSON_TOPOLOGIES, topologies);
                    Map<String, Object> mockTopology = new HashMap<>();
                    mockTopology.put(StormMetricsConstant.TOPOLOGY_SUMMARY_JSON_TOPOLOGY_NAME, mockTopologyName);
                    mockTopology.put(StormMetricsConstant.TOPOLOGY_SUMMARY_JSON_TOPOLOGY_ID_ENCODED, mockTopologyName + "-1234567890");
                    topologies.add(mockTopology);
                    return topologySummary;
                }
            }.getMockInstance();
    }

    @Test(expected = IllegalStateException.class)
    public void testWithoutAssigningTimeSeriesQuerier() throws IOException {
        stormTopologyTimeSeriesMetrics.setTimeSeriesQuerier(null);

        final long from = 1L;
        final long to = 3L;

        stormTopologyTimeSeriesMetrics.getCompleteLatency(topology, SOURCE_ID, from, to);
        fail("It should throw Exception!");
    }

    private TopologyLayout getTopologyLayoutForTest() throws IOException {
        Map<String, Object> configurations = buildTopologyConfigWithKafkaDataSource(SOURCE_ID, TOPIC_NAME);
        return new TopologyLayout(1L, "topology", mapper.writeValueAsString(configurations), null);
    }

    @Test
    public void testGetCompleteLatency() throws Exception {
        final long from = 1L;
        final long to = 3L;

        final Map<Long, Double> expected = generateTestPointsMap();

        // also verification
        new Expectations() {{
            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(StormMappedMetric.completeLatency.getStormMetricName()),
                    withEqual(StormMappedMetric.completeLatency.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected;
        }};

        Map<Long, Double> actual = stormTopologyTimeSeriesMetrics.getCompleteLatency(topology, SOURCE_ID, from, to);
        assertEquals(expected, actual);
    }

    @Test
    public void getKafkaTopicOffsets() throws Exception {
        final long from = 1L;
        final long to = 3L;

        final Map<String, Map<Long, Double>> expected = new HashMap<>();

        expected.put(StormMappedMetric.logsize.name(), generateTestPointsMap());
        expected.put(StormMappedMetric.offset.name(), generateTestPointsMap());
        expected.put(StormMappedMetric.lag.name(), generateTestPointsMap());

        // also verification
        new Expectations() {{
            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(String.format(StormMappedMetric.logsize.getStormMetricName(), TOPIC_NAME)),
                    withEqual(StormMappedMetric.logsize.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.logsize.name());

            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(String.format(StormMappedMetric.offset.getStormMetricName(), TOPIC_NAME)),
                    withEqual(StormMappedMetric.offset.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.offset.name());

            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(String.format(StormMappedMetric.lag.getStormMetricName(), TOPIC_NAME)),
                    withEqual(StormMappedMetric.lag.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.lag.name());
        }};

        Map<String, Map<Long, Double>> actual = stormTopologyTimeSeriesMetrics.getkafkaTopicOffsets(topology, SOURCE_ID, from, to);
        assertEquals(expected, actual);
    }

    @Test
    public void getComponentStats() throws Exception {
        final TopologyLayout topology = getTopologyLayoutForTest();

        final long from = 1L;
        final long to = 3L;

        final Map<String, Map<Long, Double>> expected = new HashMap<>();

        expected.put(StormMappedMetric.inputRecords.name(), generateTestPointsMap());
        expected.put(StormMappedMetric.outputRecords.name(), generateTestPointsMap());
        expected.put(StormMappedMetric.failedRecords.name(), generateTestPointsMap());
        expected.put(StormMappedMetric.processedTime.name(), generateTestPointsMap());
        expected.put(StormMappedMetric.recordsInWaitQueue.name(), generateTestPointsMap());

        new Expectations() {{
            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(StormMappedMetric.inputRecords.getStormMetricName()),
                    withEqual(StormMappedMetric.inputRecords.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.inputRecords.name());

            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(StormMappedMetric.outputRecords.getStormMetricName()),
                    withEqual(StormMappedMetric.outputRecords.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.outputRecords.name());

            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(StormMappedMetric.failedRecords.getStormMetricName()),
                    withEqual(StormMappedMetric.failedRecords.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.failedRecords.name());

            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(StormMappedMetric.processedTime.getStormMetricName()),
                    withEqual(StormMappedMetric.processedTime.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.processedTime.name());

            mockTimeSeriesQuerier.getMetrics(
                    withEqual(mockedTopologyName),
                    withEqual(SOURCE_ID),
                    withEqual(StormMappedMetric.recordsInWaitQueue.getStormMetricName()),
                    withEqual(StormMappedMetric.recordsInWaitQueue.getAggregateFunction()),
                    withEqual(from), withEqual(to)
            );

            result = expected.get(StormMappedMetric.recordsInWaitQueue.name());
        }};

        Map<String, Map<Long, Double>> actual = stormTopologyTimeSeriesMetrics.getComponentStats(topology, SOURCE_ID, from, to);
        assertEquals(expected, actual);
    }

    private Map<String, Object> buildTopologyConfigWithKafkaDataSource(String sourceId, String topicName) {
        Map<String, Object> configurations = new HashMap<>();

        Map<String, Object> dataSource = new HashMap<>();
        dataSource.put(TopologyLayoutConstants.JSON_KEY_UINAME, sourceId);
        dataSource.put(TopologyLayoutConstants.JSON_KEY_TYPE, "KAFKA");

        Map<String, Object> dataSourceConfig = new HashMap<>();
        dataSourceConfig.put(TopologyLayoutConstants.JSON_KEY_TOPIC, topicName);
        dataSource.put(TopologyLayoutConstants.JSON_KEY_CONFIG, dataSourceConfig);

        configurations.put(TopologyLayoutConstants.JSON_KEY_DATA_SOURCES, Collections.singletonList(dataSource));
        return configurations;
    }

    private Map<Long, Double> generateTestPointsMap() {
        Map<Long, Double> ret = new HashMap<>();
        int count = random.nextInt(5);
        for (int i = 0 ; i < count ; i++) {
            ret.put(random.nextLong(), random.nextDouble());
        }

        return ret;
    }
}