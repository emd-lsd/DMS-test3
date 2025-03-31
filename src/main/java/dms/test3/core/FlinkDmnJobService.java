package dms.test3.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.camunda.bpm.dmn.engine.DmnDecision;
import org.camunda.bpm.dmn.engine.DmnDecisionTableResult;
import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.DmnEngineConfiguration;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Objects;


@Service
public class FlinkDmnJobService implements CommandLineRunner {

    // ... (поля @Value остаются как были) ...
    private static final Logger LOG = LoggerFactory.getLogger(FlinkDmnJobService.class);
    // ObjectMapper больше не нужен как поле здесь, он будет создаваться в MapFunction

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${kafka.topic.input}")
    private String inputTopic;
    @Value("${kafka.topic.output}")
    private String outputTopic;
    @Value("${kafka.group.id}")
    private String kafkaGroupId;
    @Value("${dmn.file.path}")
    private String dmnFilePath;
    @Value("${dmn.decision.key}")
    private String dmnDecisionKey;


    @Override
    public void run(String... args) throws Exception {
        // ... (логирование старта) ...

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId(kafkaGroupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // !!! Изменения ниже !!!
        // DMN Engine и Decision больше не создаются здесь

        DataStream<String> inputStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source");

        DataStream<String> resultStream = inputStream
                // Передаем путь и ключ, а не объекты
                .map(new DmnEvaluationMapFunction(dmnFilePath, dmnDecisionKey))
                .filter(Objects::nonNull); // Фильтруем ошибки

        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .build();

        resultStream.sinkTo(kafkaSink).name("Kafka Sink");

        LOG.info("Executing Flink job...");
        env.execute("Flink DMN Kafka Example");
    }

    // --- Измененный Вложенный класс ---
    private static class DmnEvaluationMapFunction extends RichMapFunction<String, String> { // <-- Наследуем RichMapFunction
        private static final Logger MAP_LOG = LoggerFactory.getLogger(DmnEvaluationMapFunction.class);

        private final String dmnFilePath; // Путь к файлу (сериализуемый)
        private final String dmnDecisionKey; // Ключ решения (сериализуемый)

        // Объекты, которые будут инициализированы в open()
        private transient DmnEngine dmnEngine;
        private transient DmnDecision decision;
        private transient ObjectMapper mapper;

        // Конструктор принимает только сериализуемые данные
        public DmnEvaluationMapFunction(String dmnFilePath, String dmnDecisionKey) {
            this.dmnFilePath = dmnFilePath;
            this.dmnDecisionKey = dmnDecisionKey;
        }

        // Метод open() вызывается один раз на TaskManager перед обработкой данных
        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            MAP_LOG.info("Initializing DmnEvaluationMapFunction...");
            try {
                // Инициализируем здесь
                this.mapper = new ObjectMapper();
                this.dmnEngine = DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine();

                // Загружаем и парсим DMN
                InputStream dmnInputStream = getClass().getClassLoader().getResourceAsStream(dmnFilePath);
                if (dmnInputStream == null) {
                    throw new RuntimeException("Cannot find DMN file on TaskManager: " + dmnFilePath);
                }
                // Ищем решение по ключу
                this.decision = dmnEngine.parseDecisions(dmnInputStream)
                        .stream()
                        .filter(d -> d.getKey().equals(dmnDecisionKey))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("DMN Decision with key '" + dmnDecisionKey + "' not found in file " + dmnFilePath));

                MAP_LOG.info("DMN Engine and Decision '{}' initialized successfully.", dmnDecisionKey);

            } catch (Exception e) {
                MAP_LOG.error("Failed to initialize DMN Engine or parse DMN file", e);
                throw new RuntimeException("Failed to initialize DmnEvaluationMapFunction", e); // Провалить запуск задачи, если инициализация не удалась
            }
        }

        @Override
        public String map(String jsonValue) throws Exception {
            // Проверка на случай, если open() не смог инициализировать (хотя он должен кинуть Exception)
            if (this.mapper == null || this.dmnEngine == null || this.decision == null) {
                MAP_LOG.error("Map function called before successful initialization!");
                return null; // Или бросить исключение
            }

            try {
                InputEvent event = mapper.readValue(jsonValue, InputEvent.class);
                MAP_LOG.debug("Processing event: {}", event);

                VariableMap variables = Variables.createVariables()
                        .putValue("amount", event.amount)
                        .putValue("country", event.country);

                DmnDecisionTableResult dmnResult = dmnEngine.evaluateDecisionTable(this.decision, variables);
                String riskScore = dmnResult.getSingleResult().getSingleEntry();
                MAP_LOG.debug("DMN evaluation result for eventId {}: {}", event.eventId, riskScore);

                DecisionResult result = new DecisionResult(event, riskScore);
                return mapper.writeValueAsString(result);

            } catch (Exception e) {
                MAP_LOG.error("Failed to process event or evaluate DMN: {} | Error: {}", jsonValue, e.getMessage());
                // Можно добавить детальное логирование ошибки e
                return null; // Возвращаем null, чтобы отфильтровать
            }
        }
    }
}