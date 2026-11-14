package com.fssc.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fssc.common.core.ErrorCode;
import com.fssc.common.core.PageResult;
import com.fssc.common.exception.BizException;
import com.fssc.platform.entity.PfConfig;
import com.fssc.platform.entity.PfNoticeTemplate;
import com.fssc.platform.mapper.PfConfigMapper;
import com.fssc.platform.mapper.PfNoticeTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台全局配置与消息模板（pf_config / pf_notice_template）。
 * 【平台表-无 tenant_id】租户改不到平台全局参数（物理分表隔离）。
 */
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private final PfConfigMapper configMapper;
    private final PfNoticeTemplateMapper templateMapper;

    // 平台全局配置

    public PageResult<PfConfig> configPage(long pageNum, long pageSize, String group, String keyword) {
        LambdaQueryWrapper<PfConfig> qw = new LambdaQueryWrapper<>();
        if (group != null && !group.isBlank()) {
            qw.eq(PfConfig::getConfigGroup, group);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(PfConfig::getConfigKey, keyword)
                    .or().like(PfConfig::getConfigName, keyword));
        }
        qw.orderByAsc(PfConfig::getConfigKey);
        Page<PfConfig> p = configMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long createConfig(PfConfig cfg) {
        long dup = configMapper.selectCount(new LambdaQueryWrapper<PfConfig>()
                .eq(PfConfig::getConfigKey, cfg.getConfigKey()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数键已存在: " + cfg.getConfigKey());
        }
        cfg.setId(null);
        if (cfg.getConfigGroup() == null) {
            cfg.setConfigGroup("DEFAULT");
        }
        if (cfg.getValueType() == null) {
            cfg.setValueType("STRING");
        }
        configMapper.insert(cfg);
        return cfg.getId();
    }

    public void updateConfig(PfConfig cfg) {
        if (configMapper.selectById(cfg.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数不存在: " + cfg.getId());
        }
        configMapper.updateById(cfg);
    }

    public String getConfig(String configKey) {
        PfConfig c = configMapper.selectOne(new LambdaQueryWrapper<PfConfig>()
                .eq(PfConfig::getConfigKey, configKey));
        return c == null ? null : c.getConfigValue();
    }

    public void deleteConfig(Long id) {
        configMapper.deleteById(id);
    }

    // 消息模板

    public PageResult<PfNoticeTemplate> templatePage(long pageNum, long pageSize, String channel, String keyword) {
        LambdaQueryWrapper<PfNoticeTemplate> qw = new LambdaQueryWrapper<>();
        if (channel != null && !channel.isBlank()) {
            qw.eq(PfNoticeTemplate::getChannel, channel);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(PfNoticeTemplate::getTemplateCode, keyword)
                    .or().like(PfNoticeTemplate::getTemplateName, keyword));
        }
        qw.orderByAsc(PfNoticeTemplate::getTemplateCode);
        Page<PfNoticeTemplate> p = templateMapper.selectPage(Page.of(pageNum, pageSize), qw);
        return PageResult.of(p.getRecords(), p.getTotal(), pageNum, pageSize);
    }

    public Long createTemplate(PfNoticeTemplate t) {
        long dup = templateMapper.selectCount(new LambdaQueryWrapper<PfNoticeTemplate>()
                .eq(PfNoticeTemplate::getTemplateCode, t.getTemplateCode()));
        if (dup > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板编码已存在: " + t.getTemplateCode());
        }
        t.setId(null);
        if (t.getStatus() == null) {
            t.setStatus(1);
        }
        templateMapper.insert(t);
        return t.getId();
    }

    public void updateTemplate(PfNoticeTemplate t) {
        if (templateMapper.selectById(t.getId()) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板不存在: " + t.getId());
        }
        templateMapper.updateById(t);
    }

    /** 按编码取模板（发送消息时引用） */
    public PfNoticeTemplate templateByCode(String templateCode) {
        return templateMapper.selectOne(new LambdaQueryWrapper<PfNoticeTemplate>()
                .eq(PfNoticeTemplate::getTemplateCode, templateCode));
    }

    public void deleteTemplate(Long id) {
        templateMapper.deleteById(id);
    }
}
