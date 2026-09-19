import { Marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import 'highlight.js/styles/github-dark.css'
import DOMPurify from 'dompurify'

// 按需注册语言：全量 `import hljs from 'highlight.js'` 会把 ~190 种语言全打进产物
// （实测 markdown 分片 ≈1023KB，是首屏最大的一块）。这里只保留平台讲义/AI 回复
// 实际会出现的语言，分片体积可下降一个数量级；未注册的语言自动回退 plaintext，功能不降级。
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml' // 同时覆盖 HTML
import css from 'highlight.js/lib/languages/css'
import json from 'highlight.js/lib/languages/json'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'

hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('json', json)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('python', python)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('markdown', markdown)

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
