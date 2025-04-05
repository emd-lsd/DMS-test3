Руководство для запуска:
    1. Запустить docker-compose, затем проверить docker ps
    2. Проверить наличие топиков в брокере кафки
docker exec dms-test3-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list
Если их нет, то создать вручную:
```
docker exec dms-test3-kafka-1 kafka-topics --bootstrap-server localhost:9092 --create --topic output-decisions --partitions 1 -
-replication-factor 1
```
```
docker exec dms-test3-kafka-1 kafka-topics --bootstrap-server localhost:9092 --create --topic input-events --partitions 1 --rep
lication-factor 1
```
    3. Далее запускаем приложение, потом запускаем в разных окнах консоли продсюер и консьюмер и начинаем читать сообщения:
```
docker exec dms-test3-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic output-decisions --from-beginning
```

```
docker exec -i dms-test3-kafka-1 kafka-console-producer --broker-list localhost:9092 --topic input-events
```
    4. Если приложение не запустилось, указать еще edit config -> add VM options:
```
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/sun.misc=ALL-UNNAMED
```
Сообщения:
{"eventId": "event-123", "amount": 500.50, "country": "DE"}
{"eventId": "event-456", "amount": 12000.00, "country": "US"}


    5. Если все норм то пока не знаю чо дальше делать
Предположительный план:
Что можно делать дальше (для курсовой):

Более сложные правила: Добавить больше входов, выходов, условий в DMN-таблицу. 
Попробовать другие Hit Policies (например, COLLECT, чтобы собрать несколько результатов).

Обработка ошибок: Что делать, если JSON невалидный? 
Если в DMN не сработало ни одно правило (если Hit Policy не FIRST или нет правила по умолчанию)? 
Можно отправлять такие "плохие" сообщения в отдельный топик ошибок.

Обогащение данных: Перед вызовом DMN, возможно, понадобится обогатить событие данными из другого источника 
(например, из базы данных по ID пользователя). Flink позволяет это делать.

Состояние: Использовать Flink для подсчета статистики по решениям 
(например, сколько раз было принято решение "High" за последний час).

Управление правилами: Сделать простой REST API на Spring Boot для загрузки новой версии DMN-файла 
без перезапуска всего приложения (потребуется механизм обновления DMN-определения в RichMapFunction).

Тестирование: Написать unit-тесты для DmnEvaluationMapFunction и 
интеграционные тесты для всего потока.

Документация: Подробно описать архитектуру, взаимодействие компонентов, примеры использования.