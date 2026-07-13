package com.back.domain.member.repository;

import com.back.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Optional<Member> findByLoginId(String loginId);

    Optional<Member> findByEmail(String email);
}
