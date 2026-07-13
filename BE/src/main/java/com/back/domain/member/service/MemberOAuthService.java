package com.back.domain.member.service;

import com.back.domain.member.dto.TokenPairResponse;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.OAuthProvider;
import com.back.domain.member.entity.SocialAccount;
import com.back.domain.member.oauth.OAuthClient;
import com.back.domain.member.oauth.OAuthUserInfo;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.repository.SocialAccountRepository;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.jwt.JwtProvider;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜(OAuth2) 로그인. provider 인가코드를 검증(OAuthClient)해 얻은 유저정보로
 * 기존 로그인 / 이메일 자동연결 / 신규 자동가입을 수행하고 우리 JWT 를 발급한다.
 */
@Service
public class MemberOAuthService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final JwtProvider jwtProvider;
    private final List<OAuthClient> oauthClients;

    public MemberOAuthService(MemberRepository memberRepository,
                              SocialAccountRepository socialAccountRepository,
                              JwtProvider jwtProvider,
                              List<OAuthClient> oauthClients) {
        this.memberRepository = memberRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.jwtProvider = jwtProvider;
        this.oauthClients = oauthClients;
    }

    @Transactional
    public TokenPairResponse oauthLogin(OAuthProvider provider, String code, String redirectUri) {
        OAuthUserInfo info = resolveClient(provider).fetch(code, redirectUri);

        Member member = socialAccountRepository
                .findByProviderAndProviderUserId(provider, info.providerUserId())
                .map(SocialAccount::getMember)
                .orElseGet(() -> linkOrCreate(provider, info));

        String access = jwtProvider.createAccessToken(member.getId(), member.getRole());
        String refresh = jwtProvider.createRefreshToken(member.getId(), member.getRole());
        return new TokenPairResponse(access, refresh);
    }

    private OAuthClient resolveClient(OAuthProvider provider) {
        return oauthClients.stream()
                .filter(c -> c.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR));
    }

    /** SocialAccount 미존재 시: 검증된 이메일로 기존 회원 연결, 없으면 신규 소셜 회원 생성. */
    private Member linkOrCreate(OAuthProvider provider, OAuthUserInfo info) {
        if (!info.emailVerified() || info.email() == null || info.email().isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_EMAIL_UNVERIFIED);
        }

        Member member = memberRepository.findByEmail(info.email())
                .orElseGet(() -> memberRepository.save(
                        Member.createSocial(info.email(), uniqueNickname(info.nickname()))));

        socialAccountRepository.save(SocialAccount.create(member, provider, info.providerUserId()));
        return member;
    }

    /** 닉네임 충돌 시 짧은 랜덤 접미사로 유니크 값을 만든다. */
    private String uniqueNickname(String base) {
        String seed = (base == null || base.isBlank()) ? "user" : base;
        String candidate = seed;
        while (memberRepository.existsByNickname(candidate)) {
            candidate = seed + "_" + UUID.randomUUID().toString().substring(0, 4);
        }
        return candidate;
    }
}
