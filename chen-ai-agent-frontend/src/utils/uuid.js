/** 生成 UUID v4 */
export function generateUUID() {
  return crypto.randomUUID()
}

/** 从 sessionStorage 获取或创建 chatId */
export function getOrCreateChatId(storageKey) {
  let id = sessionStorage.getItem(storageKey)
  if (!id) {
    id = generateUUID()
    sessionStorage.setItem(storageKey, id)
  }
  return id
}
