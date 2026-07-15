package com.back.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 프로필에 등록할 수 있는 악기/전공. 코드가 API 값이고 {@code label}은 화면 표시용 한글이다.
 * 값 추가는 하위호환이지만 삭제/개명은 기존 데이터 마이그레이션이 필요하므로 주요 결정으로 기록 후 진행한다.
 * (밴드/대중 악기는 2026-07-15 리뷰에서 제외. 노래는 VOICE(성악)/VOCAL(보컬)로 분리)
 */
@Getter
@RequiredArgsConstructor
public enum Instrument {

    // 현악
    VIOLIN("바이올린"), VIOLA("비올라"), CELLO("첼로"), DOUBLE_BASS("더블베이스"), HARP("하프"),
    // 목관
    FLUTE("플루트"), OBOE("오보에"), CLARINET("클라리넷"), BASSOON("바순"),
    // 금관
    HORN("호른"), TRUMPET("트럼펫"), TROMBONE("트롬본"), TUBA("튜바"),
    // 건반
    PIANO("피아노"), ORGAN("오르간"),
    // 성악/보컬
    VOICE("성악"), VOCAL("보컬"),
    // 기타
    PERCUSSION("타악기"), COMPOSITION("작곡"), CONDUCTING("지휘"), ETC("그 외");

    private final String label;
}
