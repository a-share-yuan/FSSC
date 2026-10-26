// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.arflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fssc.arflow.entity.ArMailRecipient;
import com.fssc.arflow.mapper.ArMailRecipientMapper;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** 邮包收件人地址簿：客户联系人的姓名/电话/邮箱/地址维护。 */
@Service
@RequiredArgsConstructor
public class ArMailRecipientService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ArMailRecipientMapper mapper;

    public List<ArMailRecipient> list(String keyword, String customerCode) {
        LambdaQueryWrapper<ArMailRecipient> q = new LambdaQueryWrapper<>();
        if (customerCode != null && !customerCode.isBlank()) {
            q.eq(ArMailRecipient::getCustomerCode, customerCode);
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            q.and(w -> w.like(ArMailRecipient::getRecipientName, k)
                    .or().like(ArMailRecipient::getCustomerName, k)
                    .or().like(ArMailRecipient::getPhone, k)
                    .or().like(ArMailRecipient::getEmail, k));
        }
        return mapper.selectList(q.orderByDesc(ArMailRecipient::getIsDefault)
                .orderByDesc(ArMailRecipient::getId).last("LIMIT 300"));
    }

    @Transactional(rollbackFor = Exception.class)
    public ArMailRecipient create(ArMailRecipient r) {
        validate(r);
        r.setId(null);
        if (r.getIsDefault() == null) {
            r.setIsDefault(0);
        }
        if (r.getIsDefault() == 1) {
            clearDefault(r);
        }
        mapper.insert(r);
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArMailRecipient update(ArMailRecipient r) {
        if (r.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收件人ID不能为空");
        }
        validate(r);
        if (r.getIsDefault() == null) {
            r.setIsDefault(0);
        }
        if (r.getIsDefault() == 1) {
            clearDefault(r);
        }
        mapper.updateById(r);
        return mapper.selectById(r.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null || mapper.selectById(id) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收件人不存在 id=" + id);
        }
        mapper.deleteById(id);
    }

    public ArMailRecipient get(Long id) {
        return id == null ? null : mapper.selectById(id);
    }

    private void clearDefault(ArMailRecipient r) {
        LambdaUpdateWrapper<ArMailRecipient> u = new LambdaUpdateWrapper<>();
        u.set(ArMailRecipient::getIsDefault, 0)
                .eq(ArMailRecipient::getIsDefault, 1)
                .ne(ArMailRecipient::getId, r.getId() == null ? -1L : r.getId());
        if (r.getCustomerCode() == null || r.getCustomerCode().isBlank()) {
            u.isNull(ArMailRecipient::getCustomerCode);
        } else {
            u.eq(ArMailRecipient::getCustomerCode, r.getCustomerCode());
        }
        mapper.update(null, u);
    }

    private void validate(ArMailRecipient r) {
        if (r.getRecipientName() == null || r.getRecipientName().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "收件人姓名必填");
        }
        if (r.getEmail() != null && !r.getEmail().isBlank() && !EMAIL.matcher(r.getEmail().trim()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "邮箱格式不正确：" + r.getEmail());
        }
        r.setRecipientName(r.getRecipientName().trim());
        if (r.getEmail() != null) {
            r.setEmail(r.getEmail().trim());
        }
    }
}
