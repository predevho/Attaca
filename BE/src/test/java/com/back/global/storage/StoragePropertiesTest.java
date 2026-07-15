package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class StoragePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void storage_설정을_바인딩한다() {
        runner.withPropertyValues(
                        "storage.type=s3",
                        "storage.local.root-dir=./uploads",
                        "storage.local.base-url=http://localhost:8080/files",
                        "storage.s3.bucket=attaca-bucket",
                        "storage.s3.region=ap-northeast-2",
                        "storage.s3.access-key=AK",
                        "storage.s3.secret-key=SK",
                        "storage.s3.base-url=https://cdn.attaca.com")
                .run(context -> {
                    StorageProperties props = context.getBean(StorageProperties.class);

                    assertThat(props.type()).isEqualTo("s3");
                    assertThat(props.local().rootDir()).isEqualTo("./uploads");
                    assertThat(props.local().baseUrl()).isEqualTo("http://localhost:8080/files");
                    assertThat(props.s3().bucket()).isEqualTo("attaca-bucket");
                    assertThat(props.s3().region()).isEqualTo("ap-northeast-2");
                    assertThat(props.s3().accessKey()).isEqualTo("AK");
                    assertThat(props.s3().secretKey()).isEqualTo("SK");
                    assertThat(props.s3().baseUrl()).isEqualTo("https://cdn.attaca.com");
                });
    }

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class TestConfig {
    }
}
