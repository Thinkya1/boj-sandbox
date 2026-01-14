package com.bin.sandbox.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.time.Duration;

/**
 * Docker 客户端工厂。
 */
public final class DockerClientFactory {

    private static final String DEFAULT_DOCKER_HOST = "tcp://localhost:2375";

    private DockerClientFactory() {
    }

    public static DockerClient createClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(resolveDockerHost())
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofMinutes(5))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    private static String resolveDockerHost() {
        String envHost = System.getenv("DOCKER_HOST");
        if (envHost == null || envHost.isEmpty()) {
            return DEFAULT_DOCKER_HOST;
        }
        return envHost;
    }
}
