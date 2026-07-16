package com.back.domain.member.service;

import com.back.domain.member.dto.MemberDisplay;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.verifiedperformer.service.VerifiedPerformerService;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인이 작성자/회원 표시정보를 조회할 때 쓰는 협력 서비스.
 * id 묶음을 한 번에 조회(N+1 방지)하고 인증 뱃지를 함께 파생한다.
 */
@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final MemberRepository memberRepository;
    private final VerifiedPerformerService verifiedPerformerService;

    @Transactional(readOnly = true)
    public Map<Long, MemberDisplay> findDisplaysByIds(Set<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> verifiedIds = verifiedPerformerService.findVerifiedMemberIds(memberIds);
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, member -> new MemberDisplay(
                        member.getId(), member.getNickname(), verifiedIds.contains(member.getId())),
                        (a, b) -> a));
    }
}
