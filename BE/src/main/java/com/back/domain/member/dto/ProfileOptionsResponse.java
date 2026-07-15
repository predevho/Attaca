package com.back.domain.member.dto;

import com.back.domain.member.entity.Instrument;
import java.util.Arrays;
import java.util.List;

/** FE가 선택지를 하드코딩하지 않도록 enum 목록을 제공한다. 순서는 enum 선언 순서(악기군별 묶음). */
public record ProfileOptionsResponse(List<OptionItem> instruments) {

    public record OptionItem(String code, String label) {
    }

    public static ProfileOptionsResponse create() {
        List<OptionItem> instruments = Arrays.stream(Instrument.values())
                .map(i -> new OptionItem(i.name(), i.getLabel()))
                .toList();
        return new ProfileOptionsResponse(instruments);
    }
}
