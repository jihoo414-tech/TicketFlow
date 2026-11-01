package com.ticketflow.domain.member.controller;

import com.ticketflow.domain.member.service.MemberService;
import com.ticketflow.global.exception.BusinessException;
import com.ticketflow.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentMemberIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String MEMBER_ID_HEADER = "X-Member-Id";

    private final MemberService memberService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMemberId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public @Nullable Object resolveArgument(MethodParameter parameter,
                                            @Nullable ModelAndViewContainer mavContainer,
                                            NativeWebRequest webRequest,
                                            @Nullable WebDataBinderFactory binderFactory)
            throws Exception {
        String xMemberId = webRequest.getHeader(MEMBER_ID_HEADER);

        if (xMemberId == null || xMemberId.isBlank()) {
            throw new BusinessException(ErrorCode.MEMBER_ID_MISSING);
        }
        Long memberId = parseMemberId(xMemberId);
        if (parameter.getParameterAnnotation(CurrentMemberId.class).checkExists()) {
            memberService.validateMemberExists(memberId);
        }
        return memberId;
    }

    private Long parseMemberId(String xMemberId) {
        try {
            return Long.valueOf(xMemberId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.MEMBER_ID_INVALID);
        }
    }
}
