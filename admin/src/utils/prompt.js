import { ElMessageBox } from 'element-plus'

/** 弹窗让运营填写原因，返回填写的内容；取消时 reject */
export async function askReason(title, { placeholder = '请填写原因（会告知对方）', confirmText = '确定' } = {}) {
  const { value } = await ElMessageBox.prompt('', title, {
    inputType: 'textarea',
    inputPlaceholder: placeholder,
    confirmButtonText: confirmText,
    cancelButtonText: '取消',
    inputValidator: (v) => (v && v.trim() ? (v.trim().length <= 200 ? true : '不超过 200 字') : '请填写原因'),
    closeOnClickModal: false
  })
  return value.trim()
}

/** 二次确认 */
export function confirmAction(message, title = '请确认', type = 'warning') {
  return ElMessageBox.confirm(message, title, {
    type,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    closeOnClickModal: false
  })
}
