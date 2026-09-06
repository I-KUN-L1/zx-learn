import { Marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import DOMPurify from 'dompurify'

/** Markdown 渲染器（代码高亮 + GFM） */
const marked = new Marked({
  gfm: true,
  breaks: true,
})

marked.use({
  renderer: {
    code({ text, lang }: { text: string; lang?: string }) {
      const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
      let html = ''
      try {
        html = hljs.highlight(text, { language }).value
      } catch {
        html = hljs.highlight(text, { language: 'plaintext' }).value
      }
      const langLabel = lang || 'text'
      return `<div class="code-block"><div class="code-block__header"><span>${langLabel}</span></div><pre><code class="hljs language-${langLabel}">${html}</code></pre></div>`
    },
  },
})

/** 渲染 Markdown 为 HTML（XSS 过滤） */
export function renderMarkdown(text: string): string {
  const raw = marked.parse(text ?? '') as string
  return DOMPurify.sanitize(raw, { ADD_ATTR: ['target'] })
}

/**
 * 提取文本中的课程推荐链接（markdown 链接指向 /courses/:id）
 * 返回 [{id, name}]，用于渲染 AI 推荐课程卡片
 */
export function extractCourseRefs(text: string): { id: number; name: string }[] {
  const result: { id: number; name: string }[] = []
  const seen = new Set<number>()
  const reg = /\[([^\]]*)\]\((?:https?:\/\/[^)\s]*)?\/courses\/(\d+)[^)]*\)/g
  let m: RegExpExecArray | null
  while ((m = reg.exec(text ?? '')) !== null) {
    const id = Number(m[2])
    if (!seen.has(id)) {
      seen.add(id)
      result.push({ id, name: m[1] || `课程 #${id}` })
    }
  }
  return result
}
