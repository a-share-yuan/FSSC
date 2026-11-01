// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 The FSSC Project Authors (see LICENSE, NOTICE)

package com.fssc.document.service;

import java.util.Set;

/**
 * 中心式：归档登记簿对<b>非单据载体</b>的「归档对象类型」解析器。
 *
 * <p>档案登记只靠 {@code op_archive(archive_type, ref_id)} 两列就能说清"哪一域的哪个对象入了卷"：
 * {@code archive_type} 给对象类别、{@code ref_id} 给该域主键——<b>业务表一律零加列</b>（不给
 * {@code cont_contract}/{@code md_supplier} 之类补 archive_status/archive_time/archive_by）。
 * 载体自己那几张表不存归档结论，结论只在本登记簿里。
 *
 * <p>本接口是「类型 → 载体真名/真归属」的挂点：哪个域想让自己的档案卷在登记簿里带上可信的
 * 目录要素（关联单号、标题、所属公司）并拒绝悬空引用，就在本域提供一个实现 bean；
 * 没有实现的类型仍可按 {@code OTHER} 一类登记，只是不校验载体存在性、目录要素留空（不造数）。
 *
 * <p>实现方<b>不得</b>回写自己域里的单据表：{@code exp_bill} 的回写白名单只有
 * {@code ArchiveService.BILL_SCOPED_ARCHIVE_TYPES}（BILL/EXPENSE/PAYMENT），非单据载体一律不回写。
 */
public interface ArchiveCarrierResolver {

    /** 本实现能解析的归档对象类型（{@code op_archive.archive_type} 取值，大写） */
    Set<String> archiveTypes();

    /**
     * 按载体主键取档案目录要素。
     *
     * @param refId {@code op_archive.ref_id}＝本域主键
     * @return 载体要素；对象不存在时返回 {@code null}（调用方据此拒绝立卷，不写悬空卷）
     */
    Carrier resolve(Long refId);

    /** 档案目录要素（只取登记簿已有的列，零新列） */
    record Carrier(String refNo, String title, Long companyId) {
    }
}
