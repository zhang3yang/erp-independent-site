<!--
  询盘表单。整个站唯一往回写的地方，也是这个站存在的商业理由。

  ## 蜜罐

  后端 submitInquiry 里有一条：website 字段非空就当机器人，**装作成功但不入库**。
  所以这里放一个隐藏的 website 输入框——真人看不见也填不到，自动脚本会把表单里
  每个 input 都填上。

  用 CSS 挪出视口而不是 display:none / hidden：后者稍好一点的爬虫会跳过。
  同时 tabindex=-1 + autocomplete=off，保证键盘 Tab 和浏览器自动填充都碰不到它。

  ## 提交失败时要给退路

  外贸客户的网络到国内服务器本来就不稳。提交失败只弹一句「失败了」等于把这个客户丢了，
  所以失败时把联系邮箱显示出来，让他能直接发邮件。
-->
<template>
  <form class="iq" @submit.prevent="submit">
    <div v-if="done" class="iq-ok">
      <strong>{{ t.thanks }}</strong>
      <p>{{ t.replySoon }}</p>
    </div>

    <template v-else>
      <div class="iq-row">
        <label class="field">
          <span>{{ t.name }} *</span>
          <input v-model="form.name" required maxlength="100" autocomplete="name">
        </label>
        <label class="field">
          <span>{{ t.email }} *</span>
          <input v-model="form.email" type="email" required maxlength="150" autocomplete="email">
        </label>
      </div>

      <div class="iq-row">
        <label class="field">
          <span>{{ t.company }}</span>
          <input v-model="form.company" maxlength="200" autocomplete="organization">
        </label>
        <label class="field">
          <span>{{ t.phone }}</span>
          <input v-model="form.phone" maxlength="50" autocomplete="tel">
        </label>
      </div>

      <label class="field">
        <span>{{ t.message }} *</span>
        <textarea v-model="form.message" required rows="5" maxlength="2000"
                  :placeholder="t.messageHint"></textarea>
      </label>

      <!-- 蜜罐，见文件头注释。不要给它 label，也不要加 required -->
      <div class="iq-trap" aria-hidden="true">
        <input v-model="form.website" type="text" name="website"
               tabindex="-1" autocomplete="off">
      </div>

      <div class="iq-foot">
        <button class="btn" type="submit" :disabled="sending">
          {{ sending ? t.sending : t.submit }}
        </button>
        <p v-if="failed" class="iq-err">
          {{ t.failed }}
          <a v-if="fallbackEmail" :href="`mailto:${fallbackEmail}`">{{ fallbackEmail }}</a>
        </p>
      </div>
    </template>
  </form>
</template>

<script setup lang="ts">
const props = defineProps<{
  /** 从商品详情页进来时带上，商家一眼知道问的是哪个货 */
  goodsCode?: string
  productTitle?: string
  /** 提交失败时显示的兜底邮箱，来自装修里的联系方式 */
  fallbackEmail?: string
  lang: string
  /**
   * 客户在详情页上选中的型号编码。跟着询盘一起提交，销售看询盘时能直接知道
   * 他问的是哪一款。
   *
   * ⚠ 只发编码不发名字：名字由后端按编码查库填（SiteOpenService.resolveInquiryCombo）。
   *   那个名字要发给销售、要拿去开报价单，不能让浏览器随便填。
   */
  comboCode?: string
}>()

const route = useRoute()

const form = reactive({
  name: '', email: '', company: '', phone: '', message: '',
  website: ''   // 蜜罐
})

const sending = ref(false)
const done = ref(false)
const failed = ref(false)

async function submit() {
  sending.value = true
  failed.value = false
  try {
    await submitInquiry({
      ...form,
      goodsCode: props.goodsCode || '',
      productTitle: props.productTitle || '',
      // 商家在询盘列表里能看到客户是从哪个页面问的，用来判断哪些页面在带来生意
      pageUrl: typeof window === 'undefined' ? route.fullPath : window.location.href
    })
    done.value = true
  } catch {
    failed.value = true
  } finally {
    sending.value = false
  }
}

/**
 * 表单文案。这几句不走装修——它们是功能性文字，不是商家要维护的内容，
 * 让商家去 ERP 里翻译「提交」两个字没有意义。
 * 站点语言不在表里就退回英文（外贸站的默认受众）。
 */
const COPY: Record<string, Record<string, string>> = {
  en: { name: 'Name', email: 'Email', company: 'Company', phone: 'Phone', message: 'Message',
        messageHint: 'Quantity, specifications, target price, delivery time...',
        submit: 'Send Inquiry', sending: 'Sending...', thanks: 'Thank you for your inquiry.',
        replySoon: 'We will get back to you shortly.',
        failed: 'Sending failed. Please try again, or email us directly:' },
  zh: { name: '姓名', email: '邮箱', company: '公司', phone: '电话', message: '留言',
        messageHint: '数量、规格、目标价格、交期……',
        submit: '发送询盘', sending: '发送中……', thanks: '感谢您的询盘。',
        replySoon: '我们会尽快与您联系。',
        failed: '发送失败，请重试，或直接邮件联系我们：' },
  ja: { name: 'お名前', email: 'メール', company: '会社名', phone: '電話', message: 'お問い合わせ内容',
        messageHint: '数量、仕様、希望価格、納期など',
        submit: '送信する', sending: '送信中……', thanks: 'お問い合わせありがとうございます。',
        replySoon: '担当者より折り返しご連絡いたします。',
        failed: '送信に失敗しました。再度お試しいただくか、メールでご連絡ください：' },
  es: { name: 'Nombre', email: 'Correo', company: 'Empresa', phone: 'Teléfono', message: 'Mensaje',
        messageHint: 'Cantidad, especificaciones, precio objetivo, plazo de entrega...',
        submit: 'Enviar consulta', sending: 'Enviando...', thanks: 'Gracias por su consulta.',
        replySoon: 'Nos pondremos en contacto con usted en breve.',
        failed: 'Error al enviar. Inténtelo de nuevo o escríbanos:' },
  pt: { name: 'Nome', email: 'E-mail', company: 'Empresa', phone: 'Telefone', message: 'Mensagem',
        messageHint: 'Quantidade, especificações, preço-alvo, prazo de entrega...',
        submit: 'Enviar consulta', sending: 'Enviando...', thanks: 'Obrigado pela sua consulta.',
        replySoon: 'Entraremos em contato em breve.',
        failed: 'Falha no envio. Tente novamente ou envie-nos um e-mail:' },
  ru: { name: 'Имя', email: 'Эл. почта', company: 'Компания', phone: 'Телефон', message: 'Сообщение',
        messageHint: 'Количество, характеристики, целевая цена, сроки поставки...',
        submit: 'Отправить запрос', sending: 'Отправка...', thanks: 'Спасибо за ваш запрос.',
        replySoon: 'Мы свяжемся с вами в ближайшее время.',
        failed: 'Не удалось отправить. Повторите попытку или напишите нам:' },
  it: { name: 'Nome', email: 'Email', company: 'Azienda', phone: 'Telefono', message: 'Messaggio',
        messageHint: 'Quantità, specifiche, prezzo obiettivo, tempi di consegna...',
        submit: 'Invia richiesta', sending: 'Invio in corso...', thanks: 'Grazie per la sua richiesta.',
        replySoon: 'La ricontatteremo a breve.',
        failed: 'Invio non riuscito. Riprovi, oppure ci scriva direttamente:' },
  ko: { name: '이름', email: '이메일', company: '회사명', phone: '전화', message: '문의 내용',
        messageHint: '수량, 규격, 희망 단가, 납기 등',
        submit: '문의 보내기', sending: '전송 중...', thanks: '문의해 주셔서 감사합니다.',
        replySoon: '담당자가 곧 연락드리겠습니다.',
        failed: '전송에 실패했습니다. 다시 시도하시거나 이메일로 연락해 주세요:' },
  fr: { name: 'Nom', email: 'E-mail', company: 'Société', phone: 'Téléphone', message: 'Message',
        messageHint: 'Quantité, spécifications, prix cible, délai de livraison…',
        submit: 'Envoyer la demande', sending: 'Envoi en cours…', thanks: 'Merci pour votre demande.',
        replySoon: 'Nous revenons vers vous très rapidement.',
        failed: 'L’envoi a échoué. Réessayez, ou écrivez-nous directement :' }
}
const t = computed(() => COPY[props.lang] || COPY.en!)
</script>

<style scoped>
.iq { max-width: 640px; }
.iq-row { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
@media (max-width: 560px) { .iq-row { grid-template-columns: 1fr; } }

/* 蜜罐：挪出视口，不用 display:none——那个稍好一点的机器人会跳过 */
.iq-trap {
  position: absolute;
  left: -9999px;
  width: 1px;
  height: 1px;
  overflow: hidden;
}

.iq-foot { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-top: 6px; }
.iq-err { margin: 0; color: #b3261e; font-size: 14px; }
.iq-err a { text-decoration: underline; }
.iq-ok {
  padding: 24px;
  background: var(--bg-soft);
  border-radius: var(--radius);
  border-left: 3px solid var(--brand);
}
.iq-ok strong { font-size: 17px; }
.iq-ok p { margin: 6px 0 0; color: var(--ink-soft); }
</style>
