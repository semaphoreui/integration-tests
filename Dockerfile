# Образ для запуска тестов Semaphore
# Содержит: Java 21, Docker CLI, Git, Gradle и необходимые утилиты

FROM ubuntu:24.04

LABEL maintainer="Semaphore UI Testing"
LABEL description="Docker image for Semaphore UI automated testing with Java 21 and Docker CLI"

# Установка переменных окружения
ENV DEBIAN_FRONTEND=noninteractive
ENV GRADLE_HOME=/opt/gradle
ENV PATH=$GRADLE_HOME/bin:$PATH

# Установка зависимостей и Java 21
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Java 21
    openjdk-21-jdk \
    \
    # Docker CLI
    docker.io \
    \
    # Git
    git \
    \
    # Основные утилиты
    curl \
    wget \
    unzip \
    ca-certificates \
    \
    # Для тестов
    bash \
    coreutils \
    \
    # Для отладки
    vim \
    nano \
    less \
    \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Установка docker-compose бинарного файла
RUN curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose && \
    chmod +x /usr/local/bin/docker-compose

# Проверка установки Java и установка JAVA_HOME
RUN java_path=$(dirname $(dirname $(readlink -f $(which java)))) && \
    echo "export JAVA_HOME=$java_path" >> /etc/profile.d/java.sh && \
    export JAVA_HOME=$java_path && \
    java -version && \
    javac -version

# Проверка установки Docker CLI
RUN docker --version

# Создание рабочей директории
WORKDIR /workspace

# Копирование скриптов запуска
COPY gradlew /workspace/
COPY gradle /workspace/gradle/
RUN chmod +x /workspace/gradlew

# Копирование конфигурации Gradle
COPY gradle.properties /workspace/
COPY settings.gradle.kts /workspace/
COPY build.gradle.kts /workspace/

# Загрузка зависимостей Gradle (для ускорения последующих сборок)
RUN ./gradlew --version && \
    ./gradlew downloadAllure --no-daemon 2>/dev/null || true

# Копирование исходного кода
COPY . /workspace/

# Создание точки входа
RUN chmod +x /workspace/run-external-tests.sh 2>/dev/null || true && \
    chmod +x /workspace/publish-results-local.sh 2>/dev/null || true

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=3 \
    CMD java -version && docker --version

# Точка входа по умолчанию
# ENTRYPOINT ["/bin/bash"]
# CMD ["-c", "echo 'Semaphore test runner ready. Run: ./run-external-tests.sh or ./gradlew externalTest'"]
