package dms.test3.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller // Используем @Controller, а не @RestController, т.к. возвращаем имя HTML шаблона
@RequestMapping("/") // Мапим на корень сайта
public class WebController {

    // Метод для отображения главной страницы тестового стенда
    @GetMapping // Обрабатывает GET запросы на /
    public String getStandPage() {
        // Возвращает имя HTML файла (без расширения) из папки templates
        return "stand"; // Spring Boot + Thymeleaf найдут src/main/resources/templates/stand.html
    }
}