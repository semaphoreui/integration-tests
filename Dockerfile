# Image for running Semaphore tests
# Contains: Java 21, Docker CLI, Git, Gradle and required utilities

FROM ubuntu:24.04

LABEL maintainer="Semaphore UI Testing"
LABEL description="Docker image for Semaphore UI automated testing with Java 21 and Docker CLI"

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive
ENV GRADLE_HOME=/opt/gradle
ENV PATH=$GRADLE_HOME/bin:$PATH

# Install dependencies and Java 21
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
    # Core utilities
    curl \
    wget \
    unzip \
    ca-certificates \
    \
    # For tests
    bash \
    coreutils \
    \
    # For debugging
    vim \
    nano \
    less \
    \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean


    
# Install the docker-compose binary
RUN curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose && \
    chmod +x /usr/local/bin/docker-compose

# Install MinIO Client (mc)
RUN arch=$(uname -m) && \
    case "$arch" in \
        x86_64) mc_arch="amd64" ;; \
        aarch64) mc_arch="arm64" ;; \
        *) echo "Unsupported architecture: $arch" && exit 1 ;; \
    esac && \
    curl -L "https://dl.min.io/client/mc/release/linux-${mc_arch}/mc" -o /usr/local/bin/mc && \
    chmod +x /usr/local/bin/mc && \
    mc --version

# Verify Java installation and set JAVA_HOME
RUN java_path=$(dirname $(dirname $(readlink -f $(which java)))) && \
    echo "export JAVA_HOME=$java_path" >> /etc/profile.d/java.sh && \
    export JAVA_HOME=$java_path && \
    java -version && \
    javac -version

# Verify Docker CLI installation
RUN docker --version

# Create the working directory
WORKDIR /workspace

# Copy launch scripts
COPY gradlew /workspace/
COPY gradle /workspace/gradle/
RUN chmod +x /workspace/gradlew

# Copy Gradle configuration
COPY gradle.properties /workspace/
COPY settings.gradle.kts /workspace/
COPY build.gradle.kts /workspace/

# Download Gradle dependencies (to speed up subsequent builds)
RUN ./gradlew --version && \
    ./gradlew downloadAllure --no-daemon 2>/dev/null || true

# Copy source code
COPY . /workspace/

# Create the entry point
RUN chmod +x /workspace/run-external-tests.sh 2>/dev/null || true && \
    chmod +x /workspace/publish-results-local.sh 2>/dev/null || true

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=5s --retries=3 \
    CMD java -version && docker --version

# Default entry point
# ENTRYPOINT ["/bin/bash"]
# CMD ["-c", "echo 'Semaphore test runner ready. Run: ./run-external-tests.sh or ./gradlew externalTest'"]
CMD []
