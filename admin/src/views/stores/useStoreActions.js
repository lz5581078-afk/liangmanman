import { ElMessage } from 'element-plus'
import { approveStore, rejectStore, banStore, unbanStore } from '@/api'
import { askReason, confirmAction } from '@/utils/prompt'

/** 店铺审核/停用操作；成功后调用 onDone 刷新 */
export function useStoreActions(onDone) {
  const run = async (fn, okMsg) => {
    try {
      await fn()
    } catch {
      return
    }
    ElMessage.success(okMsg)
    onDone && onDone()
  }

  return {
    approve: (s) =>
      run(async () => {
        await confirmAction(`确定通过「${s.name}」的开店申请？通过后店铺立即上线。`, '审核通过', 'info')
        await approveStore(s.id)
      }, '已通过'),
    reject: (s) =>
      run(async () => {
        const reason = await askReason(`拒绝「${s.name}」`, { placeholder: '拒绝原因，如：门头照不清晰、位置不在开通区域' })
        await rejectStore(s.id, reason)
      }, '已拒绝'),
    ban: (s) =>
      run(async () => {
        const reason = await askReason(`停用「${s.name}」`, { placeholder: '停用原因，停用后店铺不再对买家展示' })
        await banStore(s.id, reason)
      }, '已停用'),
    unban: (s) =>
      run(async () => {
        await confirmAction(`确定恢复「${s.name}」？恢复后店铺重新上线。`, '恢复店铺', 'info')
        await unbanStore(s.id)
      }, '已恢复')
  }
}
