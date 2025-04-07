package dms.test3.config; // Убедитесь, что пакет правильный

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration // Обязательная аннотация для конфигурационного класса
public class ObjectMapperConfig {

    @Bean // Определяет бин ObjectMapper
    @Primary // Делает этот бин основным, если есть другие
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // --- Важные настройки ---
        // Поддержка типов Java 8 Date/Time API (Instant, LocalDate и т.д.)
        mapper.registerModule(new JavaTimeModule());
        // Поддержка других типов Java 8 (Optional и т.д.)
        mapper.registerModule(new Jdk8Module());
        // Сериализовать даты как строки ISO-8601, а не как числовые timestamp'ы
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // (Опционально) Другие полезные настройки:
        // mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper;
    }
}