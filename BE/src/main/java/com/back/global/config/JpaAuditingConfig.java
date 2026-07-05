package com.back.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. {@code BaseEntity}의 생성/수정 시각 자동 기록을 담당한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
