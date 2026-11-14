package com.fssc.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.common.util.AssertUtil;
import com.fssc.system.entity.SysPost;
import com.fssc.system.mapper.SysPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 岗位管理 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final SysPostMapper sysPostMapper;

    public List<SysPost> list() {
        return sysPostMapper.selectList(new LambdaQueryWrapper<SysPost>()
                .orderByAsc(SysPost::getSort));
    }

    /** 按公司过滤岗位列表 */
    public List<SysPost> listByCompany(Long companyId) {
        return sysPostMapper.selectList(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getCompanyId, companyId)
                .orderByAsc(SysPost::getSort));
    }

    /** 按部门过滤岗位列表 */
    public List<SysPost> listByDept(Long deptId) {
        return sysPostMapper.selectList(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getDeptId, deptId)
                .orderByAsc(SysPost::getSort));
    }

    /** 分页查询：按部门/公司过滤，tenant_id 由拦截器自动追加 */
    public PageResult<SysPost> page(long pageNum, long pageSize, Long companyId, Long deptId) {
        LambdaQueryWrapper<SysPost> w = new LambdaQueryWrapper<SysPost>().orderByAsc(SysPost::getSort);
        if (deptId != null) w.eq(SysPost::getDeptId, deptId);
        else if (companyId != null) w.eq(SysPost::getCompanyId, companyId);
        Page<SysPost> p = sysPostMapper.selectPage(Page.of(pageNum, pageSize), w);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long create(SysPost post) {
        validate(post);
        long dup = sysPostMapper.selectCount(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostCode, post.getPostCode()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位编码已存在: " + post.getPostCode());
        }
        post.setId(null);
        sysPostMapper.insert(post);
        return post.getId();
    }

    public void update(SysPost post) {
        validate(post);
        sysPostMapper.updateById(post);
    }

    /**
     * 必填办结（批次 2C，与前端 Org.vue「岗位名称/岗位编码/请选择公司」同一规则）。
     * dept_id 不拦：岗位可为公司级（现有 20 条岗位全部无部门），拦空等于让这 20 条无法再编辑。
     */
    private void validate(SysPost post) {
        AssertUtil.notBlank(post.getPostName(), ErrorCode.PARAM_INVALID, "请输入岗位名称");
        AssertUtil.notBlank(post.getPostCode(), ErrorCode.PARAM_INVALID, "请输入岗位编码");
        AssertUtil.isTrue(post.getCompanyId() != null && post.getCompanyId() != 0L,
                ErrorCode.PARAM_INVALID, "请选择公司");
    }

    public void enable(Long id, boolean enable) {
        SysPost u = new SysPost();
        u.setId(id);
        u.setStatus(enable ? "NORMAL" : "DISABLED");
        sysPostMapper.updateById(u);
    }

    /** 删除岗位（逻辑删） */
    public void delete(Long id) {
        sysPostMapper.hardDelete(id);
    }
}
