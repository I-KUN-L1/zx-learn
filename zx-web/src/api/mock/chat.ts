import type { ChatEventVO, ChatMessage, ChatSession } from '@/types/api'

/** 会话与消息（Mock 内存态） */
export const mockSessions: ChatSession[] = [
  { id: 's1', title: '帮我推荐一门 Java 课程', userId: 1, createTime: '2026-09-04 20:15:00' },
  { id: 's2', title: '虚拟线程和平台线程的区别', userId: 1, createTime: '2026-09-05 10:30:00' },
  { id: 's3', title: '如何制定 30 天学习计划', userId: 1, createTime: '2026-09-05 15:00:00' },
]

export const mockSessionMessages: Record<string, ChatMessage[]> = {
  s1: [
    { type: 'USER', content: '帮我推荐一门 Java 课程' },
    {
      type: 'AI',
      content:
        '根据你的学习画像（Java 基础扎实、正在进阶并发与框架），为你推荐以下课程：\n\n1. [Java 21 核心技术：从入门到精通](/courses/1) —— 系统学习虚拟线程、JVM 调优\n2. [Spring Boot 3 全栈开发实战](/courses/2) —— 企业级项目驱动\n\n建议先从并发编程章节切入，配合每周 4 次学习计划效果更佳。',
      agent: 'CourseAgent',
    },
  ],
  s2: [
    { type: 'USER', content: '虚拟线程和平台线程的区别' },
    {
      type: 'AI',
      content:
        '主要区别有三点：\n\n- **调度主体**：虚拟线程由 JVM 调度，平台线程由 OS 内核调度\n- **创建成本**：虚拟线程创建成本极低，可轻松创建百万级；平台线程通常只建数千个\n- **适用场景**：虚拟线程适合 IO 密集型任务；CPU 密集型任务收益有限\n\n```java\ntry (var executor = Executors.newVirtualThreadPerTaskExecutor()) {\n    executor.submit(() -> fetchRemoteData());\n}\n```',
      agent: 'TutorAgent',
    },
  ],
  s3: [
    { type: 'USER', content: '如何制定 30 天学习计划' },
    {
      type: 'AI',
      content:
        '推荐使用平台「学习中心」的计划功能：先在课表中选择课程与每周频率，再结合每日签到打卡。我可以按你的目标课程为你生成阶段计划，需要的话告诉我目标课程即可。',
      agent: 'PlannerAgent',
    },
  ],
}

let sessionSeq = 100

export function createMockSession(): ChatSession {
  const s: ChatSession = { id: `s${sessionSeq++}`, title: '新会话', userId: 1, createTime: new Date().toLocaleString('zh-CN', { hour12: false }) }
  mockSessions.unshift(s)
  return s
}

export function addMockUserMessage(sessionId: string, content: string) {
  ;(mockSessionMessages[sessionId] ??= []).push({ type: 'USER', content })
}

/** 根据问题生成回答文本（含课程推荐 markdown 链接） */
export function mockAnswerText(question: string): string {
  const q = question.toLowerCase()
  if (q.includes('推荐') || q.includes('课程') || q.includes('学什么')) {
    return '根据你的学习画像（Java 基础扎实、正在进阶并发与框架），为你推荐以下课程：\n\n1. [Java 21 核心技术：从入门到精通](/courses/1) —— 系统学习虚拟线程、JVM 调优\n2. [Spring Boot 3 全栈开发实战](/courses/2) —— 企业级项目驱动\n\n建议先从并发编程章节切入，配合每周 4 次学习计划效果更佳。'
  }
  if (q.includes('计划') || q.includes('安排')) {
    return '推荐使用「学习中心」的课表与计划功能：\n\n1. 在课表中设置目标课程与**每周频率**\n2. 每日完成**签到打卡**保持连续性\n3. 结合「学情报告」动态调整节奏\n\n告诉我你的目标课程，我可以帮你拆解为周计划。'
  }
  if (q.includes('虚拟线程') || q.includes('java') || q.includes('并发')) {
    return '虚拟线程是 Java 21 的正式特性（JEP 444）：\n\n- **调度主体**：由 JVM 调度，挂载在载体线程（ForkJoinPool）上\n- **成本**：创建成本极低，可支撑百万级并发\n- **适用**：IO 密集型任务收益最大\n\n```java\ntry (var executor = Executors.newVirtualThreadPerTaskExecutor()) {\n    executor.submit(() -> fetchRemoteData());\n}\n```\n\n注意：`synchronized` 块内会钉住（pin）载体线程，热点路径建议改用 `ReentrantLock`。'
  }
  return '好的，我理解你的问题。作为你的 AI 助教，我可以：\n\n- 推荐适合你的课程\n- 解答技术问题（Java / Spring / MySQL / Redis 等）\n- 制定学习计划\n- 分析学情报告\n\n你可以换个更具体的问法，例如："帮我推荐一门 Spring Boot 课程"。'
}

/**
 * 模拟 SSE 流式输出：START → DELTA… → END（对齐真实 ChatEventVO 事件格式）
 */
export async function mockStreamChat(
  sessionId: string,
  question: string,
  onEvent: (ev: ChatEventVO) => void,
  signal: AbortSignal
): Promise<void> {
  const emit = (ev: ChatEventVO) => {
    if (!signal.aborted) onEvent(ev)
  }
  emit({ type: 'START', content: '', agent: 'ZxAssistant' })
  const text = mockAnswerText(question)
  // 按 2~5 字符切片，模拟打字机节奏
  const chunks: string[] = []
  let i = 0
  while (i < text.length) {
    const size = 2 + Math.floor(Math.random() * 4)
    chunks.push(text.slice(i, i + size))
    i += size
  }
  for (const chunk of chunks) {
    if (signal.aborted) return
    await new Promise((r) => setTimeout(r, 24 + Math.random() * 46))
    emit({ type: 'DELTA', content: chunk })
  }
  if (signal.aborted) return
  emit({ type: 'END', content: '' })
  ;(mockSessionMessages[sessionId] ??= []).push({ type: 'AI', content: text, agent: 'ZxAssistant' })
}
