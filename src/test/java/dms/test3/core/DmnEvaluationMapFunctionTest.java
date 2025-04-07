package dms.test3.core; // Тот же пакет, что и тестируемый класс

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.dmn.engine.DmnDecision;
import org.camunda.bpm.dmn.engine.DmnEngine;
import org.camunda.bpm.dmn.engine.impl.DmnDecisionRuleResultImpl;
import org.camunda.bpm.dmn.engine.impl.DmnDecisionTableResultImpl;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для класса {@link FlinkDmnJobService.DmnEvaluationMapFunction}.
 * <p>
 * Тесты проверяют логику метода {@code map}, изолируя его от реального Flink окружения
 * и реального DMN движка с помощью Mockito.
 * Основное внимание уделяется корректности (де)сериализации JSON, подготовке переменных для DMN,
 * обработке (мок-)результатов DMN и обработке различных ошибочных ситуаций.
 * </p>
 *
 * @see FlinkDmnJobService.DmnEvaluationMapFunction
 */
@ExtendWith(MockitoExtension.class)
class DmnEvaluationMapFunctionTest {

    // Моки для зависимостей DMN
    @Mock
    private DmnEngine mockDmnEngine;
    @Mock
    private DmnDecision mockDecision;

    // Реальный ObjectMapper для теста (де)сериализации
    private ObjectMapper realObjectMapper;

    // Экземпляр тестируемой функции
    private FlinkDmnJobService.DmnEvaluationMapFunction functionUnderTest;

    @BeforeEach
    void setUp() {
        // Инициализируем ObjectMapper
        realObjectMapper = new ObjectMapper();
        // Настройте его так же, как в вашем приложении, если необходимо
        // realObjectMapper.registerModule(new JavaTimeModule());
        // realObjectMapper.registerModule(new Jdk8Module());

        // Создаем экземпляр функции (пути/ключи не важны для map теста)
        functionUnderTest = new FlinkDmnJobService.DmnEvaluationMapFunction("dummy.dmn", "dummyDecisionKey");

        // --- Имитируем метод open() ---
        // Внедряем реальный ObjectMapper и моки в поля функции
        functionUnderTest.mapper = realObjectMapper;
        functionUnderTest.dmnEngine = mockDmnEngine;
        functionUnderTest.decision = mockDecision;
        // ----------------------------
    }

    // --- Тестовые сценарии ---

    /**
     * Тестирует успешный сценарий: валидный входной JSON, DMN возвращает "HIGH" риск.
     * <p>
     * Проверяет, что:
     * <ul>
     *     <li>Метод возвращает не null JSON строку.</li>
     *     <li>Результат корректно десериализуется в {@link DecisionResult}.</li>
     *     <li>Поля {@code riskLevel}, {@code decisionId} и {@code originalEvent} в результате корректны.</li>
     *     <li>В DMN движок передаются правильные переменные.</li>
     *     <li>Метод DMN движка вызывается ровно один раз.</li>
     * </ul>
     * </p>
     *
     * @throws Exception если происходит ошибка во время теста.
     */
    @Test
    void map_shouldReturnCorrectJson_whenInputIsValidAndDmnReturnsHighRisk() throws Exception {
        // Arrange (Подготовка)
        // 1. Входной JSON
        String inputJson = """
                {
                  "eventId": "evt-high-1", "transactionId": "txn-h-1", "customerId": "cust-h",
                  "amount": 15000.00, "currency": "USD", "country": "NG", "paymentMethod": "CREDIT_CARD",
                  "customerAge": 30, "customerHistoryScore": 70, "isNewDevice": true
                }""";
        // 2. Ожидаемый InputEvent (для проверки передачи в DMN и включения в результат)
        InputEvent expectedEvent = realObjectMapper.readValue(inputJson, InputEvent.class);

        // 3. Настраиваем мок DMN Engine
        // Создаем мок-результат DMN
        DmnDecisionRuleResultImpl ruleResult = new DmnDecisionRuleResultImpl();
        ruleResult.putValue("riskLevel", Variables.stringValue("HIGH")); // Имя выхода DMN
        DmnDecisionTableResultImpl dmnResult = new DmnDecisionTableResultImpl(List.of(ruleResult));

        // Говорим моку dmnEngine вернуть этот результат при вызове evaluateDecisionTable
        // Используем ArgumentCaptor для захвата переменных, переданных в DMN
        ArgumentCaptor<VariableMap> variablesCaptor = ArgumentCaptor.forClass(VariableMap.class);
        when(mockDmnEngine.evaluateDecisionTable(eq(mockDecision), variablesCaptor.capture()))
                .thenReturn(dmnResult);

        // Act (Действие)
        String resultJson = functionUnderTest.map(inputJson);

        // Assert (Проверка)
        // 1. Проверяем, что результат не null
        assertThat(resultJson).isNotNull();

        // 2. Десериализуем результат обратно в объект
        DecisionResult actualResult = realObjectMapper.readValue(resultJson, DecisionResult.class);

        // 3. Проверяем поля результата
        assertThat(actualResult.getRiskLevel()).isEqualTo("HIGH");
        assertThat(actualResult.getDecisionId()).isNotNull().isNotBlank(); // ID должен быть сгенерирован
        // Сравниваем исходное событие (требует рабочего equals в InputEvent - Lombok @Data его дает)
        assertThat(actualResult.getOriginalEvent()).isEqualTo(expectedEvent);

        // 4. Проверяем переменные, переданные в DMN
        VariableMap capturedVariables = variablesCaptor.getValue();
        assertThat(capturedVariables)
                .containsEntry("amount", new BigDecimal("15000.00")) // Сравниваем с BigDecimal
                .containsEntry("country", "NG")
                .containsEntry("paymentMethod", "CREDIT_CARD")
                .containsEntry("customerAge", 30)
                .containsEntry("customerHistoryScore", 70)
                .containsEntry("isNewDevice", true)
                .containsEntry("currency", "USD");

        // 5. Убедимся, что DMN движок был вызван один раз
        verify(mockDmnEngine, times(1)).evaluateDecisionTable(eq(mockDecision), any(VariableMap.class));
    }

    /**
     * Тестирует успешный сценарий: валидный входной JSON, DMN возвращает "LOW" риск.
     * <p>
     * Проверяет аналогичные аспекты, как и предыдущий тест, но для другого результата DMN.
     * </p>
     *
     * @throws Exception если происходит ошибка во время теста.
     */
    @Test
    void map_shouldReturnCorrectJson_whenInputIsValidAndDmnReturnsLowRisk() throws Exception {
        // Arrange
        String inputJson = """
                {
                  "eventId": "evt-low-1", "transactionId": "txn-l-1", "customerId": "cust-l",
                  "amount": 150.00, "currency": "EUR", "country": "FR", "paymentMethod": "CREDIT_CARD",
                  "customerAge": 40, "customerHistoryScore": 85, "isNewDevice": false
                }""";
        InputEvent expectedEvent = realObjectMapper.readValue(inputJson, InputEvent.class);

        DmnDecisionRuleResultImpl ruleResult = new DmnDecisionRuleResultImpl();
        ruleResult.putValue("riskLevel", Variables.stringValue("LOW")); // DMN возвращает LOW
        DmnDecisionTableResultImpl dmnResult = new DmnDecisionTableResultImpl(List.of(ruleResult));

        when(mockDmnEngine.evaluateDecisionTable(eq(mockDecision), any(VariableMap.class)))
                .thenReturn(dmnResult);

        // Act
        String resultJson = functionUnderTest.map(inputJson);

        // Assert
        assertThat(resultJson).isNotNull();
        DecisionResult actualResult = realObjectMapper.readValue(resultJson, DecisionResult.class);
        assertThat(actualResult.getRiskLevel()).isEqualTo("LOW");
        assertThat(actualResult.getOriginalEvent()).isEqualTo(expectedEvent);
        assertThat(actualResult.getDecisionId()).isNotNull();
        verify(mockDmnEngine, times(1)).evaluateDecisionTable(eq(mockDecision), any(VariableMap.class));
    }

    /**
     * Тестирует сценарий, когда на вход функции приходит невалидный JSON.
     * <p>
     * Проверяет, что:
     * <ul>
     *     <li>Метод возвращает {@code null}.</li>
     *     <li>Метод DMN движка {@code evaluateDecisionTable} не вызывается.</li>
     * </ul>
     * </p>
     *
     * @throws Exception если происходит ошибка во время теста.
     */
    @Test
    void map_shouldReturnNull_whenInputJsonIsInvalid() throws Exception {
        // Arrange
        String invalidJson = "{ this is not json";

        // Act
        String resultJson = functionUnderTest.map(invalidJson);

        // Assert
        assertThat(resultJson).isNull();
        // Проверяем, что DMN движок НЕ был вызван
        verify(mockDmnEngine, never()).evaluateDecisionTable(any(DmnDecision.class), any(VariableMap.class));
    }

    /**
     * Тестирует сценарий, когда вызов DMN движка выбрасывает исключение.
     * <p>
     * Проверяет, что:
     * <ul>
     *     <li>Метод возвращает {@code null}.</li>
     *     <li>Метод DMN движка {@code evaluateDecisionTable} был вызван (произошла попытка).</li>
     * </ul>
     * </p>
     *
     * @throws Exception если происходит ошибка во время теста.
     */
    @Test
    void map_shouldReturnNull_whenDmnEvaluationThrowsException() throws Exception {
        // Arrange
        String inputJson = """
                { "eventId": "evt-err-1", "amount": 100.0, "country": "XX", "paymentMethod": "XX", "customerAge": 1, "customerHistoryScore": 1, "isNewDevice": false, "currency": "XXX" }""";

        // Настраиваем мок DMN Engine на выброс исключения
        when(mockDmnEngine.evaluateDecisionTable(eq(mockDecision), any(VariableMap.class)))
                .thenThrow(new RuntimeException("DMN Engine simulated error"));

        // Act
        String resultJson = functionUnderTest.map(inputJson);

        // Assert
        assertThat(resultJson).isNull();
        // Проверяем, что DMN движок был вызван (попытка была)
        verify(mockDmnEngine, times(1)).evaluateDecisionTable(eq(mockDecision), any(VariableMap.class));
    }

    /**
     * Тестирует сценарий, когда DMN движок возвращает пустой результат (ни одно правило не сработало).
     * <p>
     * Проверяет, что:
     * <ul>
     *     <li>Метод возвращает {@code null}.</li>
     *     <li>Метод DMN движка {@code evaluateDecisionTable} был вызван.</li>
     * </ul>
     * </p>
     *
     * @throws Exception если происходит ошибка во время теста.
     */
    @Test
    void map_shouldReturnNull_whenDmnResultIsEmpty() throws Exception {
        // Arrange
        String inputJson = """
                { "eventId": "evt-empty-1", "amount": 100.0, "country": "YY", "paymentMethod": "YY", "customerAge": 1, "customerHistoryScore": 1, "isNewDevice": false, "currency": "YYY" }""";

        // Настраиваем мок DMN Engine на возврат ПУСТОГО результата
        DmnDecisionTableResultImpl emptyDmnResult = new DmnDecisionTableResultImpl(Collections.emptyList());
        when(mockDmnEngine.evaluateDecisionTable(eq(mockDecision), any(VariableMap.class)))
                .thenReturn(emptyDmnResult);

        // Act
        String resultJson = functionUnderTest.map(inputJson);

        // Assert
        assertThat(resultJson).isNull();
        // Проверяем, что DMN движок был вызван
        verify(mockDmnEngine, times(1)).evaluateDecisionTable(eq(mockDecision), any(VariableMap.class));
    }

    /**
     * Тестирует сценарий, когда функция вызывается до инициализации её внутренних компонентов
     * (например, если бы {@code open()} не был вызван или завершился с ошибкой).
     * <p>
     * Проверяет, что метод возвращает {@code null} благодаря внутренней проверке на null.
     * </p>
     *
     * @throws Exception если происходит ошибка во время теста.
     */
    @Test
    void map_shouldReturnNull_whenFunctionIsNotInitialized() throws Exception {
        // Arrange
        // Создаем новый экземпляр, но НЕ вызываем "open" (т.е. не устанавливаем поля)
        FlinkDmnJobService.DmnEvaluationMapFunction uninitializedFunction =
                new FlinkDmnJobService.DmnEvaluationMapFunction("p", "k");
        String inputJson = "{}"; // Любой валидный JSON

        // Act
        String resultJson = uninitializedFunction.map(inputJson);

        // Assert
        assertThat(resultJson).isNull();
    }

}