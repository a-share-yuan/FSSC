import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import H5FoldCard from './H5FoldCard.vue'

/**
 * H5 卡片折叠回归护栏。
 *
 * 折叠是为了把审批详情/提单页从五六屏压到一屏多，但两条底线不能破：
 *   ① 收起态必须给摘要，否则关键数（金额/张数/待补项）全藏进二次点击里；
 *      展开后摘要要让位，不与正文里的同一数字重复显示。
 *   ② 正文用 v-show 不用 v-if——提单页的附件 input 挂 ref="fileRef"，
 *      被销毁重建会让「拍票据 / 上传发票」按钮在收起后变死。
 */
function mountFold(props = {}, slots = {}) {
  return mount(H5FoldCard, {
    props: { title: '费用明细', ...props },
    slots: { default: '<div class="body-probe">正文</div>', ...slots },
    attachTo: document.body,
  })
}

describe('H5FoldCard 折叠与摘要', () => {
  it('默认收起：正文不可见、摘要可见', () => {
    const w = mountFold({ summary: '3 行 · ￥12,400' })
    const body = w.find('.h5-fold__body')
    expect(body.exists()).toBe(true)
    expect(body.element.style.display).toBe('none')
    expect(w.find('.h5-fold__sum').text()).toBe('3 行 · ￥12,400')
    expect(w.find('.h5-fold__head').attributes('aria-expanded')).toBe('false')
    w.unmount()
  })

  it('open=true：正文可见、摘要让位给正文（不重复显示同一数字）', () => {
    const w = mountFold({ open: true, summary: '3 行 · ￥12,400' })
    expect(w.find('.h5-fold__body').element.style.display).not.toBe('none')
    expect(w.find('.h5-fold__sum').exists()).toBe(false)
    expect(w.find('.h5-fold__head').attributes('aria-expanded')).toBe('true')
    expect(w.classes()).toContain('is-open')
    w.unmount()
  })

  it('点标题行发 update:open，双向绑定可被页面接管', async () => {
    const w = mountFold({ summary: 'x' })
    await w.find('.h5-fold__head').trigger('click')
    expect(w.emitted('update:open')[0]).toEqual([true])
    await w.setProps({ open: true })
    await w.find('.h5-fold__head').trigger('click')
    expect(w.emitted('update:open')[1]).toEqual([false])
    w.unmount()
  })

  it('收起后正文仍在 DOM（v-show 不销毁 ⇒ 附件 input 的 ref 不会变空）', async () => {
    const w = mountFold({ open: true }, { default: '<input class="file-probe" type="file" />' })
    expect(w.find('.file-probe').exists()).toBe(true)
    await w.setProps({ open: false })
    expect(w.find('.file-probe').exists()).toBe(true)
    expect(w.find('.h5-fold__body').element.style.display).toBe('none')
    w.unmount()
  })

  it('tone=err/warn 时摘要染色（表单待补项要在收起态就看得见）', () => {
    const err = mountFold({ summary: '2 项待补', tone: 'err' })
    expect(err.find('.h5-fold__sum').classes()).toContain('h5-fold__sum--err')
    err.unmount()
    const warn = mountFold({ summary: '缺发票附件', tone: 'warn' })
    expect(warn.find('.h5-fold__sum').classes()).toContain('h5-fold__sum--warn')
    warn.unmount()
  })

  it('#head 插槽可替换标题（单据概要段要把单号与状态标签放进标题行）', () => {
    const w = mountFold({}, { head: '<span class="head-probe">BX202610270001</span>' })
    expect(w.find('.head-probe').text()).toBe('BX202610270001')
    expect(w.find('.h5-fold__title').exists()).toBe(false)
    w.unmount()
  })
})
