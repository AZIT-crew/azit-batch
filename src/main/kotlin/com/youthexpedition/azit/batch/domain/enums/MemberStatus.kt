package com.youthexpedition.azit.batch.domain.enums

enum class MemberStatus {
    ACTIVE, // 약관 동의 완료, 앱 사용 가능
    WITHDRAWN, // 탈퇴 (유예기간 중, 재로그인 시 복구 가능)
    DELETED, // 유예기간 만료 후 개인정보 파기 완료 (복구 불가)
    PENDING_TERMS, // 약관 동의 전
}
