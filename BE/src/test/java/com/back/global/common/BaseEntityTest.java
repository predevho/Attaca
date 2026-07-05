package com.back.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.global.config.JpaAuditingConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class BaseEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persist_populatesCreatedAtAndUpdatedAt() {
        SampleEntity saved = entityManager.persistFlushFind(new SampleEntity());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Entity
    static class SampleEntity extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
    }
}
