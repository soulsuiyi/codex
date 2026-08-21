/**
 * 后端返回的命中片段已含 <em> 高亮标记。
 * 为避免 XSS，先转义全部 HTML，再仅还原 <em> 标签用于渲染。
 */
export function highlightSnippet(snippet: string): string {
  const escaped = snippet
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
  return escaped
    .replace(/&lt;em&gt;/g, '<em>')
    .replace(/&lt;\/em&gt;/g, '</em>')
}
