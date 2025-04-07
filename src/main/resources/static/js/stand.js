// Дожидаемся полной загрузки DOM
document.addEventListener('DOMContentLoaded', () => {
    console.log("Stand UI Initialized");

    // --- Элементы DOM ---
    // Генератор
    const generateCountInput = document.getElementById('generate-count');
    const generateButton = document.getElementById('generate-button');
    const generatorMessageArea = document.getElementById('generator-message');

    // Ручная отправка (пока только элементы, логику позже)
    const manualSendForm = document.getElementById('manual-send-form');
    const manualSendMessageArea = document.getElementById('manual-send-message');

    // Статистика (пока только элементы)
    const statsTotalSpan = document.getElementById('stats-total');
    const riskChartCanvas = document.getElementById('riskDistributionChart').getContext('2d'); // Получаем 2D контекст для рисования
    const refreshStatsButton = document.getElementById('refresh-stats-button');
    const statsMessageArea = document.getElementById('stats-message');
    let riskChart = null; // Переменная для хранения экземпляра графика

    // Live Log (пока только элементы)
    const logLimitSelect = document.getElementById('log-limit');
    const logRefreshIntervalSelect = document.getElementById('log-refresh-interval');
    const liveLogList = document.getElementById('live-log-list');
    const logMessageArea = document.getElementById('log-message');
    let logRefreshIntervalId = null; // ID для setInterval

    // --- Вспомогательная функция для отображения сообщений ---
    function showMessage(areaElement, message, isError = false) {
        areaElement.textContent = message;
        areaElement.className = 'message-area ' + (isError ? 'error' : 'success');
        // Очистка через некоторое время
        setTimeout(() => {
            areaElement.textContent = '';
            areaElement.className = 'message-area';
        }, 5000); // Показать сообщение на 5 секунд
    }

    // --- Логика Генератора ---
    generateButton.addEventListener('click', async () => {
        const count = generateCountInput.value;
        showMessage(generatorMessageArea, `Инициирую генерацию ${count} сообщений...`);
        generateButton.disabled = true; // Блокируем кнопку на время запроса

        try {
            // Используем fetch для отправки GET запроса
            const response = await fetch(`/api/generate?count=${count}`);

            if (response.ok) {
                const responseText = await response.text();
                showMessage(generatorMessageArea, responseText);
            } else {
                const errorText = await response.text();
                showMessage(generatorMessageArea, `Ошибка: ${response.status} - ${errorText || response.statusText}`, true);
            }
        } catch (error) {
            console.error("Fetch error for /api/generate:", error);
            showMessage(generatorMessageArea, `Сетевая ошибка или сбой сервера: ${error.message}`, true);
        } finally {
            generateButton.disabled = false; // Разблокируем кнопку
        }
    });

    // --- Логика Ручной Отправки ---
    manualSendForm.addEventListener('submit', async (event) => {
        event.preventDefault(); // Предотвращаем стандартную отправку формы
        const submitButton = manualSendForm.querySelector('button[type="submit"]');
        showMessage(manualSendMessageArea, 'Отправка данных...');
        submitButton.disabled = true; // Блокируем кнопку

        // Собираем данные из полей формы
        // Используем .value для input/select
        // Преобразуем к нужным типам (числа, boolean)
        const eventData = {
            eventId: document.getElementById('manual-eventId').value || null, // Отправляем null, если пусто
            transactionId: document.getElementById('manual-transactionId').value,
            customerId: document.getElementById('manual-customerId').value,
            // Преобразуем строку в число (можно использовать parseFloat)
            amount: parseFloat(document.getElementById('manual-amount').value) || 0,
            currency: document.getElementById('manual-currency').value,
            country: document.getElementById('manual-country').value,
            paymentMethod: document.getElementById('manual-paymentMethod').value,
            // Преобразуем строку в целое число
            customerAge: parseInt(document.getElementById('manual-customerAge').value) || 0,
            customerHistoryScore: parseInt(document.getElementById('manual-customerHistoryScore').value) || 0,
            // Преобразуем строку "true"/"false" в boolean
            isNewDevice: document.getElementById('manual-isNewDevice').value === 'true'
        };

        console.log("Sending manual event data:", eventData); // Для отладки

        try {
            const response = await fetch('/api/send-manual', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json' // Указываем, что отправляем JSON
                },
                // Сериализуем объект eventData в JSON строку
                body: JSON.stringify(eventData)
            });

            const responseText = await response.text(); // Получаем текст ответа в любом случае

            if (response.ok) {
                showMessage(manualSendMessageArea, responseText);
                // Опционально: очистить форму после успешной отправки
                // manualSendForm.reset();
            } else {
                // Отображаем ошибку, которую вернул сервер
                showMessage(manualSendMessageArea, `Ошибка отправки: ${response.status} - ${responseText}`, true);
            }

        } catch (error) {
            console.error("Fetch error for /api/send-manual:", error);
            showMessage(manualSendMessageArea, `Сетевая ошибка или сбой сервера: ${error.message}`, true);
        } finally {
            submitButton.disabled = false; // Разблокируем кнопку
        }
    });


    // --- Логика Статистики ---

    async function fetchAndDisplayStats() {
        showMessage(statsMessageArea, 'Загрузка статистики...');
        refreshStatsButton.disabled = true; // Блокируем кнопку

        try {
            const response = await fetch('/api/statistics/summary');
            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Ошибка сервера: ${response.status} - ${errorText || response.statusText}`);
            }

            const statsData = await response.json(); // Парсим JSON ответа
            console.log("Received stats data:", statsData); // Для отладки

            // Обновляем общее количество
            statsTotalSpan.textContent = statsData.totalDecisions ?? 'N/A'; // Используем ?? для обработки null

            // Подготавливаем данные для графика Chart.js
            const labels = [];
            const dataPoints = [];
            const backgroundColors = []; // Цвета для секторов

            const colorMap = { // Зададим цвета для каждого уровня риска
                'LOW': 'rgba(75, 192, 192, 0.6)',    // Зеленоватый
                'MEDIUM': 'rgba(255, 206, 86, 0.6)', // Желтый
                'HIGH': 'rgba(255, 99, 132, 0.6)',   // Красный
                'REVIEW': 'rgba(153, 102, 255, 0.6)', // Фиолетовый
                'UNKNOWN': 'rgba(201, 203, 207, 0.6)' // Серый для непредвиденных
            };

            if (statsData.riskDistribution && statsData.riskDistribution.length > 0) {
                statsData.riskDistribution.forEach(item => {
                    labels.push(item.riskLevel);
                    dataPoints.push(item.count);
                    backgroundColors.push(colorMap[item.riskLevel] || colorMap['UNKNOWN']);
                });
            } else {
                // Если данных нет, можно показать пустой график или сообщение
                labels.push('Нет данных');
                dataPoints.push(1); // Chart.js не любит пустые данные
                backgroundColors.push(colorMap['UNKNOWN']);
            }

            // Обновляем или создаем график
            if (riskChart) {
                // Если график уже есть, обновляем его данные
                riskChart.data.labels = labels;
                riskChart.data.datasets[0].data = dataPoints;
                riskChart.data.datasets[0].backgroundColor = backgroundColors;
                riskChart.update();
                console.log("Chart data updated");
            } else {
                // Если графика нет, создаем новый
                console.log("Creating new chart");
                riskChart = new Chart(riskChartCanvas, {
                    type: 'pie', // Тип графика - круговая диаграмма
                    data: {
                        labels: labels, // Метки секторов (LOW, MEDIUM, HIGH...)
                        datasets: [{
                            label: 'Распределение Рисков',
                            data: dataPoints, // Значения (количество)
                            backgroundColor: backgroundColors, // Цвета
                            borderColor: backgroundColors.map(color => color.replace('0.6', '1')), // Границы чуть темнее
                            borderWidth: 1
                        }]
                    },
                    options: {
                        responsive: true, // Адаптивность
                        maintainAspectRatio: false, // Не сохранять пропорции, чтобы вписаться в контейнер
                        plugins: {
                            legend: {
                                position: 'top', // Позиция легенды
                            },
                            title: {
                                display: true,
                                text: 'Распределение по Уровням Риска' // Заголовок графика
                            },
                            tooltip: { // Настройка всплывающих подсказок
                                callbacks: {
                                    label: function(context) {
                                        let label = context.label || '';
                                        if (label) {
                                            label += ': ';
                                        }
                                        if (context.parsed !== null) {
                                            // Показываем и количество, и процент
                                            const total = context.dataset.data.reduce((acc, value) => acc + value, 0);
                                            const percentage = total > 0 ? ((context.parsed / total) * 100).toFixed(1) : 0;
                                            label += `${context.parsed} (${percentage}%)`;
                                        }
                                        return label;
                                    }
                                }
                            }
                        }
                    }
                });
            }

            showMessage(statsMessageArea, 'Статистика обновлена.');

        } catch(error) {
            console.error("Error fetching or displaying stats:", error);
            showMessage(statsMessageArea, `Ошибка загрузки статистики: ${error.message}`, true);
            // Можно очистить старые данные при ошибке
            statsTotalSpan.textContent = 'Ошибка';
            if(riskChart) {
                // Можно очистить график или показать сообщение об ошибке на нем
                riskChart.data.labels = ['Ошибка загрузки'];
                riskChart.data.datasets[0].data = [1];
                riskChart.data.datasets[0].backgroundColor = [colorMap['UNKNOWN']];
                riskChart.update();
            }
        } finally {
            refreshStatsButton.disabled = false; // Разблокируем кнопку
        }
    }

    refreshStatsButton.addEventListener('click', fetchAndDisplayStats);


    // --- Логика Live Log ---
    async function fetchAndDisplayLogs() {
        // Не показываем сообщение о загрузке каждый раз при автообновлении,
        // чтобы не мешать пользователю. Можно добавить его, если нужно.
        // showMessage(logMessageArea, 'Загрузка логов...');
        console.log("Fetching recent logs..."); // Лог для отладки

        const limit = logLimitSelect.value; // Получаем текущий лимит из select

        try {
            const response = await fetch(`/api/decisions/recent?limit=${limit}`);
            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Ошибка сервера: ${response.status} - ${errorText || response.statusText}`);
            }

            const recentDecisions = await response.json(); // Парсим массив DTO
            console.log("Received recent decisions:", recentDecisions);

            // Очищаем текущий список
            liveLogList.innerHTML = '';

            if (recentDecisions && recentDecisions.length > 0) {
                recentDecisions.forEach(decision => {
                    const listItem = document.createElement('li');

                    // Форматируем timestamp для читаемости
                    const timestamp = decision.processingTimestamp
                        ? new Date(decision.processingTimestamp).toLocaleString('ru-RU') // Локализованный формат
                        : 'N/A';

                    // Формируем текст для элемента списка
                    listItem.textContent = `[${timestamp}] Event: ${decision.eventId} | Risk: ${decision.riskLevel} (Amount: ${decision.amount ?? 'N/A'} ${decision.currency ?? ''}, Country: ${decision.country ?? 'N/A'}) | DecisionId: ${decision.decisionId}`;

                    // Добавляем классы для стилизации в зависимости от уровня риска (опционально)
                    listItem.classList.add(`risk-${(decision.riskLevel || 'unknown').toLowerCase()}`);

                    liveLogList.appendChild(listItem); // Добавляем элемент в список
                });
                // Убираем сообщение об ошибке, если оно было
                //showMessage(logMessageArea, '');
            } else {
                // Если данных нет
                const listItem = document.createElement('li');
                listItem.textContent = 'Нет данных о последних решениях.';
                liveLogList.appendChild(listItem);
                showMessage(logMessageArea, 'Нет данных о последних решениях.');
            }

        } catch (error) {
            console.error("Error fetching or displaying logs:", error);
            showMessage(logMessageArea, `Ошибка загрузки логов: ${error.message}`, true);
            // Можно очистить список при ошибке
            liveLogList.innerHTML = '<li>Ошибка загрузки логов...</li>';
        }
    }

    function setupLogAutoRefresh() {
        // Останавливаем предыдущий таймер, если он был
        if (logRefreshIntervalId) {
            clearInterval(logRefreshIntervalId);
            logRefreshIntervalId = null;
            console.log("Cleared previous log refresh interval.");
        }

        const intervalSeconds = parseInt(logRefreshIntervalSelect.value);
        console.log(`Setting up log auto-refresh interval: ${intervalSeconds} seconds.`);

        if (intervalSeconds > 0) {
            // Запускаем новый интервал
            logRefreshIntervalId = setInterval(fetchAndDisplayLogs, intervalSeconds * 1000); // Переводим секунды в миллисекунды
            console.log(`Log refresh interval started with ID: ${logRefreshIntervalId}`);
        }
    }

    logLimitSelect.addEventListener('change', fetchAndDisplayLogs); // Обновить при смене лимита
    logRefreshIntervalSelect.addEventListener('change', setupLogAutoRefresh); // Настроить интервал при смене

    // --- Первоначальная загрузка данных при открытии страницы ---
    fetchAndDisplayStats(); // Загрузить статистику
    fetchAndDisplayLogs();  // Загрузить логи
    setupLogAutoRefresh(); // Настроить автообновление логов


}); // Конец DOMContentLoaded