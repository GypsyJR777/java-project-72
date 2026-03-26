### Hexlet tests and linter status:
[![Actions Status](https://github.com/GypsyJR777/java-project-72/actions/workflows/hexlet-check.yml/badge.svg)](https://github.com/GypsyJR777/java-project-72/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=GypsyJR777_java-project-72&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=GypsyJR777_java-project-72)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=GypsyJR777_java-project-72&metric=bugs)](https://sonarcloud.io/summary/new_code?id=GypsyJR777_java-project-72)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=GypsyJR777_java-project-72&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=GypsyJR777_java-project-72)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=GypsyJR777_java-project-72&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=GypsyJR777_java-project-72)

## Локальный запуск

```bash
./gradlew run
```

Приложение стартует на порту из переменной окружения `PORT`, либо на `7070` по умолчанию.

## Сборка исполняемого jar

```bash
./gradlew shadowJar
java -jar build/libs/app-1.0-SNAPSHOT-all.jar
```

## Деплой на Render

В проект добавлен файл `render.yaml` для деплоя на Render.

Ссылка на приложение на Render после деплоя: `https://java-project-72-kz8x.onrender.com/`