package dms.test3.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
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
import java.util.UUID; // <-- Импортируем UUID для decisionId

@Service
public class FlinkDmnJobService implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkDmnJobService.class);

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
        LOG.info("Starting Flink DMN Job Service..."); // Улучшенное логирование старта

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId(kafkaGroupId)
                .setStartingOffsets(OffsetsInitializer.latest()) // Начинаем с последних сообщений
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty("isolation.level", "read_committed") // Для транзакционной обработки Kafka, если нужно
                .build();

        LOG.info("Kafka Source configured for topic: {}", inputTopic);

        DataStream<String> inputStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source");

        DataStream<String> resultStream = inputStream
                .map(new DmnEvaluationMapFunction(dmnFilePath, dmnDecisionKey))
                .name("DMN Evaluation") // Даем имя оператору для лучшей читаемости в UI Flink
                .filter(Objects::nonNull) // Фильтруем null результаты (ошибки обработки)
                .name("Filter Errors");

        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(outputTopic)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                // .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE) // Гарантия доставки (можно выбрать EXACTLY_ONCE при необходимости и настройке)
                .build();

        LOG.info("Kafka Sink configured for topic: {}", outputTopic);

        resultStream.sinkTo(kafkaSink).name("Kafka Sink");

        LOG.info("Flink job graph constructed. Starting execution...");
        env.execute("Realtime Financial Transaction Risk Assessment"); // Более осмысленное имя джобы
    }

    // --- Вложенный класс DmnEvaluationMapFunction с изменениями ---
    private static class DmnEvaluationMapFunction extends RichMapFunction<String, String> {
        private static final Logger MAP_LOG = LoggerFactory.getLogger(DmnEvaluationMapFunction.class);

        private final String dmnFilePath;
        private final String dmnDecisionKey;

        // transient - не сериализуются и будут инициализированы в open()
        private transient DmnEngine dmnEngine;
        private transient DmnDecision decision;
        private transient ObjectMapper mapper;

        public DmnEvaluationMapFunction(String dmnFilePath, String dmnDecisionKey) {
            this.dmnFilePath = dmnFilePath;
            this.dmnDecisionKey = dmnDecisionKey;
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            MAP_LOG.info("Initializing DmnEvaluationMapFunction on TaskManager...");
            try {
                this.mapper = new ObjectMapper();
                // Включаем поддержку Java 8 time/Optional, если нужно (не обязательно для текущей структуры)
                // this.mapper.registerModule(new JavaTimeModule());
                // this.mapper.registerModule(new Jdk8Module());

                this.dmnEngine = DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine();
                MAP_LOG.info("DMN Engine created.");

                InputStream dmnInputStream = getClass().getClassLoader().getResourceAsStream(dmnFilePath);
                if (dmnInputStream == null) {
                    MAP_LOG.error("Cannot find DMN file in classpath: {}", dmnFilePath);
                    throw new RuntimeException("Cannot find DMN file on TaskManager: " + dmnFilePath);
                }
                MAP_LOG.debug("DMN file found: {}", dmnFilePath);

                // Парсим все решения из файла
                var parsedDecisions = dmnEngine.parseDecisions(dmnInputStream);
                if (parsedDecisions.isEmpty()) {
                    MAP_LOG.error("No decisions found in DMN file: {}", dmnFilePath);
                    throw new RuntimeException("No decisions found in DMN file: " + dmnFilePath);
                }
                MAP_LOG.debug("Parsed {} decision(s) from DMN file.", parsedDecisions.size());

                // Ищем нужное решение по ключу
                this.decision = parsedDecisions.stream()
                        .filter(d -> d.getKey().equals(dmnDecisionKey))
                        .findFirst()
                        .orElseThrow(() -> {
                            MAP_LOG.error("DMN Decision with key '{}' not found in file {}", dmnDecisionKey, dmnFilePath);
                            return new RuntimeException("DMN Decision with key '" + dmnDecisionKey + "' not found in file " + dmnFilePath);
                        });

                MAP_LOG.info("DMN Engine and Decision '{}' initialized successfully.", dmnDecisionKey);

            } catch (Exception e) {
                MAP_LOG.error("Failed to initialize DMN Engine or parse DMN file: {}", e.getMessage(), e);
                // Провалить запуск задачи, если инициализация не удалась
                throw new RuntimeException("Failed to initialize DmnEvaluationMapFunction", e);
            }
        }

        @Override
        public String map(String jsonValue) throws Exception {
            // Дополнительная проверка инициализации (хотя open должен был упасть)
            if (this.mapper == null || this.dmnEngine == null || this.decision == null) {
                MAP_LOG.error("Map function called before successful initialization! Skipping message: {}", jsonValue);
                return null; // Пропустить сообщение
            }

            InputEvent event = null;
            try {
                // 1. Десериализация JSON в InputEvent
                event = mapper.readValue(jsonValue, InputEvent.class);
                MAP_LOG.info("Processing eventId: {}, transactionId: {}", event.eventId, event.transactionId); // Логируем ID

                // 2. Подготовка переменных для DMN
                VariableMap variables = Variables.createVariables()
                        .putValue("amount", event.amount)
                        .putValue("country", event.country)
                        .putValue("paymentMethod", event.paymentMethod)
                        .putValue("customerAge", event.customerAge)
                        .putValue("customerHistoryScore", event.customerHistoryScore)
                        .putValue("isNewDevice", event.isNewDevice)
                        .putValue("currency", event.currency);
                // Добавляем остальные поля, если они будут нужны для DMN в будущем
                // .putValue("customerId", event.customerId) // Пока не используется в правилах
                // .putValue("transactionId", event.transactionId) // Пока не используется в правилах

                MAP_LOG.debug("Variables prepared for DMN evaluation: {}", variables);

                // 3. Выполнение DMN-решения
                DmnDecisionTableResult dmnResult = dmnEngine.evaluateDecisionTable(this.decision, variables);
                MAP_LOG.debug("DMN evaluation completed for eventId: {}. Result count: {}", event.eventId, dmnResult.size());

                // 4. Получение результата (ожидаем один результат из-за FIRST hit policy)
                // и одной выходной колонки 'riskLevel')
                if (dmnResult.isEmpty()) {
                    MAP_LOG.warn("DMN evaluation for eventId: {} returned no result (no rule matched?). Assigning default or skipping.", event.eventId);
                    // Можно присвоить дефолтный статус или вернуть null
                    return null; // Пропускаем, если ни одно правило не сработало (включая дефолтное)
                }
                // Используем getSingleResult(), ожидая одну строку результата
                // Используем getSingleEntry(), ожидая одну выходную колонку ('riskLevel')
                String riskLevel = dmnResult.getSingleResult().getSingleEntry();
                MAP_LOG.info("DMN evaluation result for eventId {}: riskLevel = {}", event.eventId, riskLevel);

                // 5. Создание DecisionResult
                String decisionId = UUID.randomUUID().toString(); // Генерируем уникальный ID для решения
                DecisionResult result = new DecisionResult(event, decisionId, riskLevel);

                // 6. Сериализация результата в JSON
                return mapper.writeValueAsString(result);

            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // Ошибка парсинга входящего JSON
                MAP_LOG.error("Failed to parse input JSON: {} | Error: {}", jsonValue, e.getMessage(), e);
                return null; // Возвращаем null, чтобы отфильтровать
            } catch (org.camunda.bpm.dmn.engine.impl.DmnEvaluationException e) {
                // Ошибка во время выполнения DMN (например, неверный тип данных)
                MAP_LOG.error("DMN evaluation failed for event: {} | Error: {}", event != null ? event.eventId : "N/A", e.getMessage(), e);
                return null;
            } catch (Exception e) {
                // Любая другая неожиданная ошибка
                MAP_LOG.error("Unexpected error processing event: {} | Error: {}", event != null ? event.eventId : jsonValue, e.getMessage(), e);
                return null;
            }
        }

        @Override
        public void close() throws Exception {
            // Здесь можно освободить ресурсы, если бы они были (например, закрыть соединения)
            // DMN Engine и ObjectMapper не требуют явного закрытия
            MAP_LOG.info("Closing DmnEvaluationMapFunction.");
            super.close();
        }
    }
}