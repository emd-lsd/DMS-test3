package dms.test3.dto;

import dms.test3.core.InputEvent;
import jakarta.validation.constraints.*; // Импорты для валидации
import lombok.Data;
import lombok.NoArgsConstructor; // Нужен для Jackson

import java.math.BigDecimal;

@Data // Lombok для геттеров/сеттеров/и т.д.
@NoArgsConstructor // Для Jackson
public class InputEventDto {

    // Можно оставить необязательным или добавить @NotBlank, если нужно
    private String eventId;

    // Пример: ID транзакции не может быть пустым и имеет макс. длину
    @NotBlank(message = "Transaction ID cannot be blank")
    @Size(max = 50, message = "Transaction ID cannot exceed 50 characters")
    private String transactionId;

    @NotBlank(message = "Customer ID cannot be blank")
    @Size(max = 100)
    private String customerId;

    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive") // Сумма должна быть > 0
    // @Digits(integer=15, fraction=2, message="Amount format error") // Если нужны конкретные цифры
    private BigDecimal amount;

    @NotBlank(message = "Currency code cannot be blank")
    @Size(min = 3, max = 3, message = "Currency code must be 3 characters long") // Пример валидации кода валюты
    private String currency;

    @NotBlank(message = "Country code cannot be blank")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters long") // Пример валидации ISO кода страны
    private String country;

    @NotBlank(message = "Payment method cannot be blank")
    // Можно добавить @Pattern, если есть строгий список методов
    // @Pattern(regexp = "^(CREDIT_CARD|BANK_TRANSFER|E_WALLET|CRYPTO)$", message = "Invalid payment method")
    private String paymentMethod;

    @NotNull(message = "Customer age cannot be null")
    @Min(value = 0, message = "Customer age cannot be negative")
    @Max(value = 120, message = "Customer age seems unrealistic") // Разумный предел
    private Integer customerAge;

    @NotNull(message = "Customer history score cannot be null")
    @Min(value = 0, message = "Score cannot be negative")
    @Max(value = 100, message = "Score cannot exceed 100")
    private Integer customerHistoryScore;

    @NotNull(message = "isNewDevice flag must be provided (true or false)")
    private Boolean isNewDevice;

    // Конструкторы, геттеры, сеттеры генерируются Lombok (@Data)

    // Пример простого маппера (можно сделать лучше с MapStruct или BeanUtils)
    public static InputEvent mapDtoToEvent(InputEventDto dto) {
        InputEvent event = new InputEvent();
        event.setEventId(dto.getEventId()); // ID может быть null или прийти от клиента
        event.setTransactionId(dto.getTransactionId());
        event.setCustomerId(dto.getCustomerId());
        event.setAmount(dto.getAmount());
        event.setCurrency(dto.getCurrency());
        event.setCountry(dto.getCountry());
        event.setPaymentMethod(dto.getPaymentMethod());
        event.setCustomerAge(dto.getCustomerAge());
        event.setCustomerHistoryScore(dto.getCustomerHistoryScore());
        event.setIsNewDevice(dto.getIsNewDevice());
        // Важно: Если InputEvent имеет поля, которых нет в DTO, они останутся null/дефолтными
        return event;
    }
}
