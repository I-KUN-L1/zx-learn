<script setup lang="ts">
import { ref } from 'vue'

/** 当前展示的协议：user=用户协议 / privacy=隐私政策 */
const active = ref<'user' | 'privacy'>('user')
const visible = ref(false)

function open(tab: 'user' | 'privacy') {
  active.value = tab
  visible.value = true
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="active === 'user' ? '用户协议' : '隐私政策'"
    width="640px"
    class="zx-agreement-dialog"
  >
    <el-tabs v-model="active">
      <el-tab-pane label="用户协议" name="user">
        <div class="zx-agreement-body">
          <p class="zx-agreement-update">更新日期：2026-09-01 · 生效日期：2026-09-01</p>
          <p>欢迎使用知行智学平台（以下简称"本平台"）。在注册、登录及使用本平台服务前，请您务必仔细阅读并充分理解本协议。</p>
          <h4>一、账号注册与安全</h4>
          <p>1.1 您承诺以真实、准确的信息注册账号，注册手机号须为您本人实名持有。</p>
          <p>1.2 平台账号（含学员、教师账号）仅限本人使用，不得出借、转让或售卖。</p>
          <p>1.3 您应妥善保管账号密码，因保管不善造成的损失由您自行承担；发现账号被盗用请立即联系我们。</p>
          <h4>二、用户行为规范</h4>
          <p>2.1 您不得利用本平台发布、传播违反法律法规或公序良俗的内容，包括但不限于侵权、色情、暴力、广告刷屏等信息。</p>
          <p>2.2 教师用户上传的课程内容应保证拥有合法权利或授权，不得侵犯第三方知识产权。</p>
          <p>2.3 您不得以任何技术手段干扰平台正常运行，或恶意刷取积分、优惠券等平台权益。</p>
          <h4>三、服务说明</h4>
          <p>3.1 平台提供的 AI 助教、学情报告等功能基于人工智能技术生成，内容仅供学习参考，不构成专业建议。</p>
          <p>3.2 付费课程与秒杀活动规则以活动页面公示为准；课程虚拟内容一经兑换，非质量问题不支持退换。</p>
          <h4>四、协议的变更与终止</h4>
          <p>4.1 本平台有权根据运营需要修订本协议，修订后的协议将通过平台公告方式通知，继续使用即视为接受修订。</p>
          <p>4.2 若您违反本协议约定，本平台有权视情节采取警示、限制功能、封禁账号等措施。</p>
          <p>如有疑问，请联系平台客服。</p>
        </div>
      </el-tab-pane>
      <el-tab-pane label="隐私政策" name="privacy">
        <div class="zx-agreement-body">
          <p class="zx-agreement-update">更新日期：2026-09-01 · 生效日期：2026-09-01</p>
          <p>知行智学平台深知个人信息对您的重要性，我们将按照法律法规要求，以最小必要原则收集和使用您的信息。</p>
          <h4>一、我们收集的信息</h4>
          <p>1.1 账号信息：注册时提供的手机号、用户名/姓名，用于创建与识别您的账号。</p>
          <p>1.2 学习信息：学习记录、签到记录、练习与考试成绩、笔记，用于生成学情报告与个性化学习路径。</p>
          <p>1.3 设备与日志信息：访问时间、访问方式，用于保障服务安全与排查故障。</p>
          <h4>二、信息的使用</h4>
          <p>2.1 学情数据仅用于向您本人展示学习报告，以及向您授权的教师用于教学辅导。</p>
          <p>2.2 我们不会向任何第三方出售您的个人信息；确需共享时将遵循合法、正当、必要原则并征得您的同意。</p>
          <h4>三、信息的存储与保护</h4>
          <p>3.1 您的个人信息存储于中华人民共和国境内的服务器；登录密码经不可逆加密存储。</p>
          <p>3.2 我们采取访问控制、传输加密等技术手段防止信息被未经授权地访问、篡改或泄露。</p>
          <h4>四、您的权利</h4>
          <p>4.1 您可以在"个人中心"查看、更正您的个人信息。</p>
          <p>4.2 您可以申请注销账号；注销后我们将依法删除或匿名化处理您的个人信息。</p>
          <p>如对本政策有任何疑问，可通过平台客服与我们联系。</p>
        </div>
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button type="primary" @click="visible = false">我已阅读</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.zx-agreement-body {
  max-height: 46vh;
  overflow-y: auto;
  padding-right: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}
.zx-agreement-body h4 {
  margin: 12px 0 4px;
  color: var(--el-text-color-primary);
}
.zx-agreement-body p {
  margin: 4px 0;
  text-align: justify;
}
.zx-agreement-update {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
