package com.fssc.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 答案反馈（ai_feedback）：只用于运营评测，不参与任何执行判断。 */
@Data
@TableName("ai_feedback")
public class AiFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String traceId;
    private String sessionId;
    private String question;
    private String skill;
    /** 1=赞 -1=踩 */
    private Integer rating;
    /** 不准 / 没查到 / 不是我要的 / 太慢 … */
    private String reason;
    private String answerExcerpt;
    private LocalDateTime createTime;
}
