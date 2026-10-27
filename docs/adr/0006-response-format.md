# 성공 응답은 DTO 그대로, 실패 응답은 ProblemDetail + code

성공하면 HTTP 상태 코드와 함께 DTO를 감싸지 않고 그대로 응답한다. 실패하면 RFC 9457 `ProblemDetail`에 `code`(`ErrorCode` 이름)를 추가해 응답한다. 화면은 HTTP 상태로 성공과 실패를 나누고, 실패면 `code`와 `detail`을 본다. 스프링이 내는 오류도 `spring.mvc.problemdetails.enabled`로 같은 모양이 되지만, `code`는 없다.

**선택 이유 (사용자)**: 대화에서 AI 추천에 동의했다. 직접 쓴 이유로 바꿀 것.

## Considered Options

- **직접 정한 오류 JSON `{code, message}`**: 단순하지만 스프링이 내는 오류는 다른 모양으로 나가서, 통일하려면 스프링 예외를 하나씩 직접 잡아야 한다.
- **`{success, code, message, data}`로 성공·실패를 모두 감싸기**: 필드를 나중에 추가하기 쉽다. 하지만 HTTP 상태와 같은 정보를 본문에 한 번 더 담아 서로 어긋날 수 있다. 오류를 `ProblemDetail`로 정했으므로 성공만 감싸면 모양이 둘이 된다. 필드 추가가 필요한 API(예: 페이지네이션)는 그때 그 응답만 객체로 바꾼다.
