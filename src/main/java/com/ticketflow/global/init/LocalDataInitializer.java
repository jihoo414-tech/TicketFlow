package com.ticketflow.global.init;

import com.ticketflow.domain.member.entity.Member;
import com.ticketflow.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

// 로컬 실행에서만 초기 데이터를 넣는다. 테스트는 필요한 데이터를 스스로 만든다.
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final MemberRepository memberRepository;

    @Override
    public void run(ApplicationArguments args) {
        memberRepository.saveAll(List.of(
                new Member("회원1"),
                new Member("회원2"),
                new Member("회원3")
        ));
    }
}
