FROM eclipse-temurin:25-jdk-noble@sha256:80f67fb3d6f76e2d817d98a4cc2a9cf05f054d4e6adc5e2144bde671d722e63f

ARG COMMAND_LINE_TOOLS=15859902
ARG COMMAND_LINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/build/gradle \
    HOME=/build/home \
    JAVA_TOOL_OPTIONS=-Duser.home=/build/home
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${PATH}"

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN test "$(uname -m)" = x86_64 \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl unzip rsync git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fL --retry 3 "https://dl.google.com/android/repository/commandlinetools-linux-${COMMAND_LINE_TOOLS}_latest.zip" -o /tmp/tools.zip \
    && echo "${COMMAND_LINE_TOOLS_SHA256}  /tmp/tools.zip" | sha256sum -c - \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" /build/home \
    && unzip -q /tmp/tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/tools.zip \
    && (yes | sdkmanager --licenses >/dev/null; test "${PIPESTATUS[1]}" -eq 0) \
    && sdkmanager "platforms;android-37.0" "build-tools;37.0.0" \
    && chmod -R a+rX "${ANDROID_HOME}" \
    && rm -rf /build/home/.android/cache

COPY entrypoint.sh /opt/shiyin/entrypoint.sh
WORKDIR /tmp
ENTRYPOINT ["bash", "/opt/shiyin/entrypoint.sh"]
CMD [":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug"]
