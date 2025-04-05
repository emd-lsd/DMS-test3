package dms.test3.entity;

import dms.test3.core.InputEvent;
import jakarta.persistence.*;
import lombok.Getter; // Импортируем Lombok аннотации
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@Getter // Генерирует все геттеры
@Setter // Генерирует все сеттеры
@NoArgsConstructor // Генерирует пустой конструктор (требуется JPA)
@ToString(exclude = {"originalEventJson"}) // Генерирует toString(), исключая большое поле
@Entity
@Table(name = "decision_results")
public class DecisionResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "decision_id", nullable = false, unique = true, length = 36)
    private String decisionId;

    @Column(name = "risk_level", nullable = false, length = 50)
    private String riskLevel;

    // --- Поля из InputEvent ---
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "country", length = 10)
    private String country;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "customer_age")
    private Integer customerAge;

    @Column(name = "customer_history_score")
    private Integer customerHistoryScore;

    @Column(name = "is_new_device")
    private Boolean isNewDevice; // Имя поля оставим isNewDevice для Lombok

    // --- Метаданные ---
    @Column(name = "processing_timestamp", nullable = false)
    private Instant processingTimestamp;

    // --- Полное исходное событие в JSON ---
    @Lob
    @Column(name = "original_event_json", columnDefinition = "TEXT")
    private String originalEventJson;

    /**
     * Пользовательский конструктор сохраняем, так как он содержит логику.
     * Lombok не будет генерировать конструктор со всеми аргументами,
     * если есть хотя бы один явный конструктор.
     * Аннотация @NoArgsConstructor сгенерирует конструктор без аргументов.
     */
    public DecisionResultEntity(String decisionId, String riskLevel, InputEvent originalEvent, String originalEventJson) {
        this.decisionId = decisionId;
        this.riskLevel = riskLevel;
        this.originalEventJson = originalEventJson;
        this.processingTimestamp = Instant.now();

        if (originalEvent != null) {
            this.eventId = originalEvent.getEventId(); // Используем геттеры
            this.transactionId = originalEvent.getTransactionId();
            this.customerId = originalEvent.getCustomerId();
            this.amount = originalEvent.getAmount();
            this.currency = originalEvent.getCurrency();
            this.country = originalEvent.getCountry();
            this.paymentMethod = originalEvent.getPaymentMethod();
            this.customerAge = originalEvent.getCustomerAge();
            this.customerHistoryScore = originalEvent.getCustomerHistoryScore();
            this.isNewDevice = originalEvent.getIsNewDevice(); // Lombok сгенерирует getIsNewDevice() для boolean
        } else {
            this.eventId = "UNKNOWN_EVENT";
        }
    }

    // --- ВСЕ РУЧНЫЕ ГЕТТЕРЫ, СЕТТЕРЫ И toString() УДАЛЕНЫ ---
    // Lombok сгенерирует их во время компиляции

    // Важно: Если у вас boolean поле называется 'isSomething', Lombok по умолчанию
    // генерирует геттер isSomething(). Если поле называется 'something', геттер будет getSomething().
    // Hibernate/JPA обычно корректно работает с `isSomething()` для boolean.
}