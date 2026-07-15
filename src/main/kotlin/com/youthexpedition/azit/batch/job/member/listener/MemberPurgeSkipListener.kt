package com.youthexpedition.azit.batch.job.member.listener

import com.youthexpedition.azit.batch.domain.Member
import com.youthexpedition.azit.batch.job.member.dto.MemberPurgeTarget
import org.slf4j.LoggerFactory
import org.springframework.batch.core.listener.SkipListener
import org.springframework.stereotype.Component

@Component
class MemberPurgeSkipListener : SkipListener<Member, MemberPurgeTarget> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSkipInRead(t: Throwable) {
        log.error("[MEMBER_PURGE] 대상 조회 중 오류로 스킵되었습니다.", t)
    }

    override fun onSkipInProcess(item: Member, t: Throwable) {
        log.error("[MEMBER_PURGE] memberId: {} 변환 중 오류로 스킵되었습니다.", item.id, t)
    }

    override fun onSkipInWrite(item: MemberPurgeTarget, t: Throwable) {
        // 스킵된 회원은 WITHDRAWN 상태로 남아 다음 정기 실행에서 자동 재시도
        log.error("[MEMBER_PURGE] memberId: {} 파기 중 오류로 스킵되었습니다. 다음 실행에서 재시도됩니다.", item.memberId, t)
    }
}
