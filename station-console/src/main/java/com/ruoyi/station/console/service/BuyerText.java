package com.ruoyi.station.console.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 前台买家会看到的提示语，按语言（2026-09-22 起）。
 *
 * <p>之前所有 {@link ServiceException} 的 message 都是中文，前台又是把 msg 原样上屏的——
 * 日语站上登录失败看到的是「邮箱或密码不正确」。这里给每一句一个键，八种语言各一份，
 * 出错时由 {@code ApiExceptionHandler} 按请求里的语言（{@link SiteLang}）渲染。</p>
 *
 * <h3>怎么用</h3>
 * <pre>
 *   throw BuyerText.error("auth.badCredentials", 401);
 *   throw BuyerText.error("order.badQty", 500, code);      // {0} 是参数
 *   limited.put("msg", BuyerText.render("order.tooFrequent", SiteLang.current()));
 * </pre>
 *
 * <p>{@link #error} 抛出来的异常 message 仍然是<b>中文</b>（zh 那一份）：日志、管理台、
 * 以及没经过拦截器的路径看到的还是原来那句。只有走 ApiExceptionHandler 出去的响应才换语言。</p>
 *
 * <p>⚠ 只放<b>买家</b>看得到的句子。管理台那一侧（ConsoleApiController 那些）只有中文界面，
 * 继续直接 {@code new ServiceException("…")}，不要为它们造键。</p>
 *
 * <p>语言集合和 {@link BuyerMailCopy} 一样是站点开得了的八种；缺键退回英文，英文也缺退回中文。</p>
 */
public final class BuyerText
{
    private BuyerText()
    {
    }

    private static final Map<String, Map<String, String>> TEXT = new HashMap<>();

    private static void put(String key, String zh, String en, String ja, String es,
            String pt, String ru, String it, String ko)
    {
        Map<String, String> row = new HashMap<>();
        row.put("zh", zh);
        row.put("en", en);
        row.put("ja", ja);
        row.put("es", es);
        row.put("pt", pt);
        row.put("ru", ru);
        row.put("it", it);
        row.put("ko", ko);
        TEXT.put(key, row);
    }

    static
    {
        put("common.error",
            "系统异常，请稍后重试",
            "Something went wrong on our side. Please try again later.",
            "システムエラーが発生しました。しばらくしてからもう一度お試しください。",
            "Se produjo un error en nuestro sistema. Inténtelo de nuevo más tarde.",
            "Ocorreu um erro no nosso sistema. Tente novamente mais tarde.",
            "На нашей стороне произошла ошибка. Попробуйте позже.",
            "Si è verificato un errore da parte nostra. Riprova più tardi.",
            "시스템 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

        /* ---- 买家账号 ---- */
        put("auth.verifyLinkInvalid",
            "这个验证链接无效或已过期，请重新注册或让我们再发一次",
            "This confirmation link is invalid or has expired. Register again or ask us to resend it.",
            "この確認リンクは無効か、期限切れです。再度登録するか、再送信をご依頼ください。",
            "Este enlace de confirmación no es válido o ha caducado. Regístrese de nuevo o pida que lo reenviemos.",
            "Este link de confirmação é inválido ou expirou. Registre-se novamente ou peça um novo envio.",
            "Эта ссылка недействительна или устарела. Зарегистрируйтесь заново или запросите повторную отправку.",
            "Questo link di conferma non è valido o è scaduto. Registrati di nuovo o chiedici di reinviarlo.",
            "이 확인 링크는 유효하지 않거나 만료되었습니다. 다시 가입하거나 재발송을 요청해 주세요.");
        put("auth.accountMissing",
            "账号不存在",
            "This account does not exist.",
            "このアカウントは存在しません。",
            "Esta cuenta no existe.",
            "Esta conta não existe.",
            "Такого аккаунта нет.",
            "Questo account non esiste.",
            "존재하지 않는 계정입니다.");
        put("auth.tooManyAttempts",
            "尝试次数过多，请过一会儿再试",
            "Too many attempts. Please wait a while and try again.",
            "試行回数が多すぎます。しばらくしてからもう一度お試しください。",
            "Demasiados intentos. Espere un momento y vuelva a intentarlo.",
            "Muitas tentativas. Aguarde um pouco e tente novamente.",
            "Слишком много попыток. Подождите немного и попробуйте снова.",
            "Troppi tentativi. Attendi un momento e riprova.",
            "시도 횟수가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        put("auth.badCredentials",
            "邮箱或密码不正确",
            "The email or password is incorrect.",
            "メールアドレスまたはパスワードが正しくありません。",
            "El correo o la contraseña no son correctos.",
            "E-mail ou senha incorretos.",
            "Неверная почта или пароль.",
            "Email o password non corretti.",
            "이메일 또는 비밀번호가 올바르지 않습니다.");
        put("auth.disabled",
            "这个账号已被停用，请联系我们",
            "This account has been disabled. Please contact us.",
            "このアカウントは停止されています。お問い合わせください。",
            "Esta cuenta ha sido desactivada. Póngase en contacto con nosotros.",
            "Esta conta foi desativada. Entre em contato conosco.",
            "Этот аккаунт отключён. Свяжитесь с нами.",
            "Questo account è stato disattivato. Contattaci.",
            "이 계정은 비활성화되었습니다. 문의해 주세요.");
        put("auth.unverified",
            "邮箱还没验证。我们又发了一封验证邮件，请查收后再登录",
            "Your email is not confirmed yet. We have sent the confirmation email again — open the link, then sign in.",
            "メールアドレスがまだ確認されていません。確認メールを再送しましたので、リンクを開いてからログインしてください。",
            "Su correo aún no está confirmado. Hemos vuelto a enviar el correo de confirmación: abra el enlace y luego inicie sesión.",
            "Seu e-mail ainda não foi confirmado. Enviamos o e-mail de confirmação novamente: abra o link e depois entre.",
            "Почта ещё не подтверждена. Мы отправили письмо повторно: откройте ссылку, затем войдите.",
            "La tua email non è ancora confermata. Abbiamo reinviato l'email di conferma: apri il link, poi accedi.",
            "이메일이 아직 확인되지 않았습니다. 확인 메일을 다시 보냈으니 링크를 연 뒤 로그인해 주세요.");
        put("auth.resetLinkInvalid",
            "这个重置链接无效或已过期，请重新发起一次",
            "This reset link is invalid or has expired. Please request a new one.",
            "この再設定リンクは無効か、期限切れです。もう一度お手続きください。",
            "Este enlace de restablecimiento no es válido o ha caducado. Solicite uno nuevo.",
            "Este link de redefinição é inválido ou expirou. Solicite um novo.",
            "Эта ссылка для сброса недействительна или устарела. Запросите новую.",
            "Questo link di reimpostazione non è valido o è scaduto. Richiedine uno nuovo.",
            "이 재설정 링크는 유효하지 않거나 만료되었습니다. 다시 요청해 주세요.");
        put("auth.oldPasswordWrong",
            "原密码不正确",
            "The current password is incorrect.",
            "現在のパスワードが正しくありません。",
            "La contraseña actual no es correcta.",
            "A senha atual está incorreta.",
            "Текущий пароль указан неверно.",
            "La password attuale non è corretta.",
            "현재 비밀번호가 올바르지 않습니다.");
        put("auth.noDomain",
            "本站还没登记主域名（u_s_siteinfo.domain），发不出验证链接，请联系站长",
            "This site is not fully configured yet, so we cannot send the link. Please contact us.",
            "サイトの設定が完了していないため、リンクを送信できません。お問い合わせください。",
            "Este sitio aún no está completamente configurado, así que no podemos enviar el enlace. Contáctenos.",
            "Este site ainda não está totalmente configurado, por isso não podemos enviar o link. Entre em contato conosco.",
            "Сайт ещё не настроен полностью, поэтому мы не можем отправить ссылку. Свяжитесь с нами.",
            "Questo sito non è ancora completamente configurato, quindi non possiamo inviare il link. Contattaci.",
            "사이트 설정이 아직 완료되지 않아 링크를 보낼 수 없습니다. 문의해 주세요.");
        put("auth.mailQuota",
            "今天给这个邮箱发的邮件太多了，请明天再试或直接联系我们",
            "We have already sent this address several emails today. Please try again tomorrow or contact us directly.",
            "本日このメールアドレスへの送信回数が上限に達しました。明日もう一度お試しいただくか、直接ご連絡ください。",
            "Hoy ya hemos enviado varios correos a esta dirección. Inténtelo mañana o contáctenos directamente.",
            "Já enviamos vários e-mails para este endereço hoje. Tente novamente amanhã ou fale conosco diretamente.",
            "Сегодня на этот адрес уже отправлено несколько писем. Попробуйте завтра или свяжитесь с нами напрямую.",
            "Oggi abbiamo già inviato diverse email a questo indirizzo. Riprova domani o contattaci direttamente.",
            "오늘 이 주소로 보낸 메일이 너무 많습니다. 내일 다시 시도하거나 직접 문의해 주세요.");
        put("auth.badEmail",
            "请填一个有效的邮箱地址",
            "Please enter a valid email address.",
            "有効なメールアドレスを入力してください。",
            "Introduzca una dirección de correo válida.",
            "Informe um endereço de e-mail válido.",
            "Введите корректный адрес электронной почты.",
            "Inserisci un indirizzo email valido.",
            "유효한 이메일 주소를 입력해 주세요.");
        put("auth.passwordShort",
            "密码至少 {0} 位",
            "The password must be at least {0} characters.",
            "パスワードは {0} 文字以上にしてください。",
            "La contraseña debe tener al menos {0} caracteres.",
            "A senha deve ter pelo menos {0} caracteres.",
            "Пароль должен содержать не менее {0} символов.",
            "La password deve avere almeno {0} caratteri.",
            "비밀번호는 {0}자 이상이어야 합니다.");
        put("auth.passwordLong",
            "密码太长了，请不超过 72 位",
            "The password is too long. Please use at most 72 characters.",
            "パスワードが長すぎます。72 文字以内にしてください。",
            "La contraseña es demasiado larga. Use como máximo 72 caracteres.",
            "A senha é muito longa. Use no máximo 72 caracteres.",
            "Пароль слишком длинный. Не более 72 символов.",
            "La password è troppo lunga. Usa al massimo 72 caratteri.",
            "비밀번호가 너무 깁니다. 72자 이내로 입력해 주세요.");
        put("auth.noMailer",
            "本站还没有配置邮件服务，暂时不能注册或找回密码，请直接联系我们",
            "Email is not set up on this site yet, so registration and password reset are unavailable. Please contact us directly.",
            "このサイトではメール送信が設定されていないため、登録とパスワード再設定は現在ご利用いただけません。直接お問い合わせください。",
            "El correo aún no está configurado en este sitio, así que el registro y el restablecimiento de contraseña no están disponibles. Contáctenos directamente.",
            "O e-mail ainda não está configurado neste site, por isso o registro e a redefinição de senha não estão disponíveis. Fale conosco diretamente.",
            "На сайте ещё не настроена почта, поэтому регистрация и сброс пароля пока недоступны. Свяжитесь с нами напрямую.",
            "L'email non è ancora configurata su questo sito, quindi registrazione e reimpostazione password non sono disponibili. Contattaci direttamente.",
            "이 사이트에는 아직 메일이 설정되지 않아 가입과 비밀번호 재설정을 사용할 수 없습니다. 직접 문의해 주세요.");
        put("auth.signInRequired",
            "请先登录",
            "Please sign in first.",
            "先にログインしてください。",
            "Inicie sesión primero.",
            "Entre primeiro.",
            "Сначала войдите.",
            "Accedi prima.",
            "먼저 로그인해 주세요.");

        /* ---- 发信 ---- */
        put("mail.unavailable",
            "本站还没有配置邮件服务，这一步暂时用不了，请直接联系我们",
            "Email is not set up on this site yet, so this step is unavailable. Please contact us directly.",
            "このサイトではメール送信が設定されていないため、この操作は現在ご利用いただけません。直接お問い合わせください。",
            "El correo aún no está configurado en este sitio, así que este paso no está disponible. Contáctenos directamente.",
            "O e-mail ainda não está configurado neste site, por isso esta etapa não está disponível. Fale conosco diretamente.",
            "На сайте ещё не настроена почта, поэтому этот шаг пока недоступен. Свяжитесь с нами напрямую.",
            "L'email non è ancora configurata su questo sito, quindi questo passaggio non è disponibile. Contattaci direttamente.",
            "이 사이트에는 아직 메일이 설정되지 않아 이 단계를 사용할 수 없습니다. 직접 문의해 주세요.");
        put("mail.failed",
            "邮件发送失败，请稍后再试或直接联系我们",
            "The email could not be sent. Please try again later or contact us directly.",
            "メールを送信できませんでした。しばらくしてからもう一度お試しいただくか、直接ご連絡ください。",
            "No se pudo enviar el correo. Inténtelo más tarde o contáctenos directamente.",
            "Não foi possível enviar o e-mail. Tente novamente mais tarde ou fale conosco diretamente.",
            "Не удалось отправить письмо. Попробуйте позже или свяжитесь с нами напрямую.",
            "Impossibile inviare l'email. Riprova più tardi o contattaci direttamente.",
            "메일을 보내지 못했습니다. 잠시 후 다시 시도하거나 직접 문의해 주세요.");

        /* ---- 地址簿 ---- */
        put("addr.empty",
            "没有收到地址内容",
            "No address was received.",
            "住所の内容が受信できませんでした。",
            "No se recibió ninguna dirección.",
            "Nenhum endereço foi recebido.",
            "Адрес не получен.",
            "Nessun indirizzo ricevuto.",
            "주소 내용을 받지 못했습니다.");
        put("addr.recipientRequired",
            "请填写收件人",
            "Please enter the recipient.",
            "宛名を入力してください。",
            "Indique el destinatario.",
            "Informe o destinatário.",
            "Укажите получателя.",
            "Inserisci il destinatario.",
            "받는 분을 입력해 주세요.");
        put("addr.countryRequired",
            "请填写国家",
            "Please enter the country.",
            "国を入力してください。",
            "Indique el país.",
            "Informe o país.",
            "Укажите страну.",
            "Inserisci il paese.",
            "국가를 입력해 주세요.");
        put("addr.tooMany",
            "最多只能保存 {0} 条收货地址，请先删掉不用的",
            "You can save up to {0} addresses. Delete one you no longer use first.",
            "保存できる住所は {0} 件までです。使わないものを先に削除してください。",
            "Puede guardar hasta {0} direcciones. Elimine antes una que ya no use.",
            "Você pode salvar até {0} endereços. Exclua antes um que não usa mais.",
            "Можно сохранить не более {0} адресов. Сначала удалите ненужный.",
            "Puoi salvare al massimo {0} indirizzi. Elimina prima uno che non usi più.",
            "배송지는 최대 {0}개까지 저장할 수 있습니다. 쓰지 않는 배송지를 먼저 삭제해 주세요.");
        put("addr.missing",
            "这条地址不存在",
            "This address does not exist.",
            "この住所は存在しません。",
            "Esta dirección no existe.",
            "Este endereço não existe.",
            "Такого адреса нет.",
            "Questo indirizzo non esiste.",
            "존재하지 않는 주소입니다.");

        /* ---- 头像 ---- */
        put("avatar.badPreset",
            "认不出这个头像色块",
            "That avatar colour is not recognised.",
            "そのアバターの色は認識できません。",
            "No se reconoce ese color de avatar.",
            "Essa cor de avatar não é reconhecida.",
            "Этот цвет аватара не распознан.",
            "Quel colore dell'avatar non è riconosciuto.",
            "인식할 수 없는 아바타 색상입니다.");
        put("avatar.tooBig",
            "这张图太大了，请换一张",
            "That image is too large. Please choose another one.",
            "画像が大きすぎます。別の画像を選んでください。",
            "Esa imagen es demasiado grande. Elija otra.",
            "Essa imagem é muito grande. Escolha outra.",
            "Изображение слишком большое. Выберите другое.",
            "L'immagine è troppo grande. Scegline un'altra.",
            "이미지가 너무 큽니다. 다른 이미지를 선택해 주세요.");
        put("avatar.badFormat",
            "头像格式不对",
            "That avatar format is not supported.",
            "そのアバターの形式には対応していません。",
            "Ese formato de avatar no es compatible.",
            "Esse formato de avatar não é aceito.",
            "Такой формат аватара не поддерживается.",
            "Formato dell'avatar non supportato.",
            "지원하지 않는 아바타 형식입니다.");

        /* ---- 下单 / 查单 ---- */
        put("order.empty",
            "订单内容不能为空",
            "The request is empty.",
            "ご依頼内容が空です。",
            "La solicitud está vacía.",
            "A solicitação está vazia.",
            "Запрос пуст.",
            "La richiesta è vuota.",
            "요청 내용이 비어 있습니다.");
        put("order.noLines",
            "清单是空的",
            "Your request list is empty.",
            "見積依頼リストが空です。",
            "Su lista de solicitud está vacía.",
            "Sua lista de solicitação está vazia.",
            "Список запроса пуст.",
            "La tua lista di richiesta è vuota.",
            "견적 요청 목록이 비어 있습니다.");
        put("order.tooManyLines",
            "一张单最多 {0} 项",
            "A request can contain at most {0} items.",
            "1 回のご依頼は最大 {0} 品目までです。",
            "Una solicitud puede contener como máximo {0} artículos.",
            "Uma solicitação pode ter no máximo {0} itens.",
            "В одном запросе может быть не более {0} позиций.",
            "Una richiesta può contenere al massimo {0} articoli.",
            "한 번의 요청에는 최대 {0}개 품목까지 담을 수 있습니다.");
        put("order.contactRequired",
            "联系人不能为空",
            "Please tell us your name.",
            "お名前を入力してください。",
            "Indique su nombre.",
            "Informe o seu nome.",
            "Укажите ваше имя.",
            "Inserisci il tuo nome.",
            "성함을 입력해 주세요.");
        put("order.emailRequired",
            "邮箱不能为空",
            "Please enter your email address.",
            "メールアドレスを入力してください。",
            "Introduzca su correo electrónico.",
            "Informe o seu e-mail.",
            "Укажите адрес электронной почты.",
            "Inserisci la tua email.",
            "이메일 주소를 입력해 주세요.");
        put("order.countryRequired",
            "国家不能为空",
            "Please tell us the destination country.",
            "配送先の国を入力してください。",
            "Indique el país de destino.",
            "Informe o país de destino.",
            "Укажите страну назначения.",
            "Inserisci il paese di destinazione.",
            "도착 국가를 입력해 주세요.");
        put("order.badEmail",
            "邮箱格式不正确",
            "Please enter a valid email address.",
            "有効なメールアドレスを入力してください。",
            "Introduzca una dirección de correo válida.",
            "Informe um endereço de e-mail válido.",
            "Введите корректный адрес электронной почты.",
            "Inserisci un indirizzo email valido.",
            "유효한 이메일 주소를 입력해 주세요.");
        put("order.lineNoCode",
            "清单里有一项没有商品编码",
            "One of the items has no product code.",
            "商品コードのない品目があります。",
            "Uno de los artículos no tiene código de producto.",
            "Um dos itens não tem código de produto.",
            "У одной из позиций нет кода товара.",
            "Uno degli articoli non ha il codice prodotto.",
            "제품 코드가 없는 품목이 있습니다.");
        put("order.badQty",
            "商品 {0} 的数量不正确",
            "The quantity for product {0} is not valid.",
            "製品 {0} の数量が正しくありません。",
            "La cantidad del producto {0} no es válida.",
            "A quantidade do produto {0} não é válida.",
            "Неверное количество для товара {0}.",
            "La quantità per il prodotto {0} non è valida.",
            "제품 {0}의 수량이 올바르지 않습니다.");
        put("order.qtyTooBig",
            "商品 {0} 的数量超出上限",
            "The quantity for product {0} is above the limit.",
            "製品 {0} の数量が上限を超えています。",
            "La cantidad del producto {0} supera el límite.",
            "A quantidade do produto {0} está acima do limite.",
            "Количество для товара {0} превышает лимит.",
            "La quantità per il prodotto {0} supera il limite.",
            "제품 {0}의 수량이 한도를 초과했습니다.");
        put("order.unavailable",
            "这些商品已下架或不存在，请从清单里移除后重试：{0}",
            "These products are no longer available. Remove them from your list and try again: {0}",
            "次の製品は現在ご用意できません。リストから削除してもう一度お試しください：{0}",
            "Estos productos ya no están disponibles. Elimínelos de su lista y vuelva a intentarlo: {0}",
            "Estes produtos não estão mais disponíveis. Remova-os da sua lista e tente novamente: {0}",
            "Эти товары больше недоступны. Удалите их из списка и попробуйте снова: {0}",
            "Questi prodotti non sono più disponibili. Rimuovili dalla lista e riprova: {0}",
            "다음 제품은 더 이상 구매할 수 없습니다. 목록에서 제거한 뒤 다시 시도해 주세요: {0}");
        put("order.tooFrequent",
            "提交过于频繁，请稍后再试，或直接邮件联系我们",
            "You are sending too quickly. Please try again in a moment, or email us directly.",
            "送信の間隔が短すぎます。しばらくしてからもう一度お試しいただくか、直接メールでご連絡ください。",
            "Está enviando demasiado rápido. Inténtelo de nuevo en un momento o escríbanos directamente.",
            "Você está enviando muito rápido. Tente novamente em instantes ou envie-nos um e-mail.",
            "Слишком частые отправки. Попробуйте чуть позже или напишите нам напрямую.",
            "Stai inviando troppo velocemente. Riprova tra un momento o scrivici direttamente.",
            "너무 빠르게 보내고 있습니다. 잠시 후 다시 시도하거나 직접 이메일로 문의해 주세요.");
        put("order.lookupFields",
            "请填写订单号和下单时用的邮箱",
            "Please enter the reference number and the email address you used.",
            "受付番号と、ご依頼時に使ったメールアドレスを入力してください。",
            "Introduzca el número de referencia y el correo que utilizó.",
            "Informe o número de referência e o e-mail que você usou.",
            "Введите номер запроса и адрес почты, который вы использовали.",
            "Inserisci il numero di riferimento e l'email che hai usato.",
            "접수번호와 요청 시 사용한 이메일 주소를 입력해 주세요.");
        put("order.lookupTooFrequent",
            "查询过于频繁，请稍后再试，或直接回复我们发给你的确认邮件",
            "Too many lookups. Please try again later, or simply reply to the confirmation email we sent you.",
            "検索回数が多すぎます。しばらくしてからもう一度お試しいただくか、お送りした確認メールにご返信ください。",
            "Demasiadas consultas. Inténtelo más tarde o responda al correo de confirmación que le enviamos.",
            "Muitas consultas. Tente novamente mais tarde ou responda ao e-mail de confirmação que enviamos.",
            "Слишком много запросов. Попробуйте позже или просто ответьте на наше письмо с подтверждением.",
            "Troppe ricerche. Riprova più tardi, oppure rispondi all'email di conferma che ti abbiamo inviato.",
            "조회가 너무 많습니다. 잠시 후 다시 시도하거나 보내드린 확인 메일에 답장해 주세요.");
        put("order.notFound",
            "没有找到这张单。请核对订单号，以及下单时填的那个邮箱地址",
            "We could not find that request. Check the reference number and the email address you used.",
            "該当するご依頼が見つかりません。受付番号と、ご依頼時に使ったメールアドレスをご確認ください。",
            "No encontramos esa solicitud. Compruebe el número de referencia y el correo que utilizó.",
            "Não encontramos essa solicitação. Verifique o número de referência e o e-mail que você usou.",
            "Запрос не найден. Проверьте номер и адрес почты, который вы использовали.",
            "Non troviamo questa richiesta. Controlla il numero di riferimento e l'email che hai usato.",
            "해당 요청을 찾을 수 없습니다. 접수번호와 사용한 이메일 주소를 확인해 주세요.");
        put("order.missing",
            "订单不存在",
            "This request does not exist.",
            "このご依頼は存在しません。",
            "Esta solicitud no existe.",
            "Esta solicitação não existe.",
            "Такого запроса нет.",
            "Questa richiesta non esiste.",
            "존재하지 않는 요청입니다.");

        /* ---- 在线咨询 ---- */
        put("chat.empty",
            "说点什么再发",
            "Type a message first.",
            "メッセージを入力してから送信してください。",
            "Escriba un mensaje antes de enviar.",
            "Escreva uma mensagem antes de enviar.",
            "Сначала введите сообщение.",
            "Scrivi un messaggio prima di inviare.",
            "메시지를 입력한 뒤 보내 주세요.");
        put("chat.initFailed",
            "会话初始化失败，刷新页面再试",
            "The chat could not be started. Refresh the page and try again.",
            "チャットを開始できませんでした。ページを再読み込みしてもう一度お試しください。",
            "No se pudo iniciar el chat. Actualice la página e inténtelo de nuevo.",
            "Não foi possível iniciar o chat. Atualize a página e tente novamente.",
            "Не удалось начать чат. Обновите страницу и попробуйте снова.",
            "Impossibile avviare la chat. Aggiorna la pagina e riprova.",
            "채팅을 시작할 수 없습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.");
        put("chat.tooFast",
            "发得太快了，歇一会儿再发。急的话可以直接提交询盘，我们看到就回",
            "You are sending too fast. Wait a moment before the next message — or send an inquiry and we will reply as soon as we see it.",
            "送信が速すぎます。少し待ってから送信してください。お急ぎの場合はお問い合わせフォームからどうぞ。確認次第ご返信します。",
            "Está enviando demasiado rápido. Espere un momento; si tiene prisa, envíe una consulta y responderemos en cuanto la veamos.",
            "Você está enviando muito rápido. Aguarde um pouco; se for urgente, envie uma consulta e responderemos assim que a virmos.",
            "Слишком быстро. Подождите немного; если срочно, отправьте запрос — ответим, как только увидим.",
            "Stai inviando troppo velocemente. Attendi un momento; se hai fretta, invia una richiesta e risponderemo appena la vediamo.",
            "너무 빠르게 보내고 있습니다. 잠시 후 다시 보내 주세요. 급하시면 문의를 남겨 주시면 확인 즉시 답변드립니다.");
        put("chat.sessionMissing",
            "缺少会话编码",
            "The chat session is missing.",
            "チャットセッションがありません。",
            "Falta la sesión de chat.",
            "A sessão de chat está faltando.",
            "Сессия чата отсутствует.",
            "Manca la sessione della chat.",
            "채팅 세션 정보가 없습니다.");
        put("chat.notFound",
            "会话不存在",
            "This chat session does not exist.",
            "このチャットセッションは存在しません。",
            "Esta sesión de chat no existe.",
            "Esta sessão de chat não existe.",
            "Такой сессии чата нет.",
            "Questa sessione di chat non esiste.",
            "존재하지 않는 채팅 세션입니다.");

        /* ---- 引导式下单（2026-09-28，OrderWizardService.resolve）。{0} 是字段名，已按买家语言 ---- */
        put("wizard.closed",
            "在线定制暂未开放，请把需求写在询价单里发给我们",
            "Online configuration is not open at the moment. Please send us your needs through a request.",
            "オンライン構成は現在ご利用いただけません。ご要望は見積依頼でお送りください。",
            "La configuración en línea no está disponible por ahora. Envíenos sus necesidades mediante una solicitud.",
            "A configuração online não está disponível no momento. Envie suas necessidades por meio de uma solicitação.",
            "Онлайн-конфигуратор сейчас недоступен. Отправьте нам свои пожелания через запрос.",
            "La configurazione online al momento non è disponibile. Inviaci le tue esigenze tramite una richiesta.",
            "온라인 구성은 현재 이용할 수 없습니다. 요청서로 필요 사항을 보내 주세요.");
        put("wizard.required",
            "请填写「{0}」",
            "Please complete “{0}”.",
            "「{0}」を入力してください。",
            "Complete «{0}».",
            "Preencha “{0}”.",
            "Заполните «{0}».",
            "Compila «{0}».",
            "'{0}' 항목을 입력해 주세요.");
        put("wizard.badOption",
            "「{0}」的选项已经变了，请刷新页面后重新选择",
            "The choices for “{0}” have changed. Please refresh the page and choose again.",
            "「{0}」の選択肢が変更されました。ページを再読み込みして選び直してください。",
            "Las opciones de «{0}» han cambiado. Actualice la página y vuelva a elegir.",
            "As opções de “{0}” mudaram. Atualize a página e escolha novamente.",
            "Варианты для «{0}» изменились. Обновите страницу и выберите заново.",
            "Le opzioni di «{0}» sono cambiate. Aggiorna la pagina e scegli di nuovo.",
            "'{0}'의 선택지가 변경되었습니다. 페이지를 새로고침한 뒤 다시 선택해 주세요.");
        put("wizard.locked",
            "「{0}」里选的「{1}」在当前配置下不能选",
            "“{1}” cannot be chosen for “{0}” with your current configuration.",
            "現在の構成では「{0}」で「{1}」を選べません。",
            "Con su configuración actual no se puede elegir «{1}» en «{0}».",
            "Com a configuração atual não é possível escolher “{1}” em “{0}”.",
            "При текущей конфигурации для «{0}» нельзя выбрать «{1}».",
            "Con la configurazione attuale non puoi scegliere «{1}» per «{0}».",
            "현재 구성에서는 '{0}'에 '{1}'을(를) 선택할 수 없습니다.");
        put("wizard.badNumber",
            "「{0}」请填 {1} 到 {2} 之间的整数",
            "“{0}” must be a whole number between {1} and {2}.",
            "「{0}」には {1}〜{2} の整数を入力してください。",
            "«{0}» debe ser un número entero entre {1} y {2}.",
            "“{0}” deve ser um número inteiro entre {1} e {2}.",
            "«{0}» — целое число от {1} до {2}.",
            "«{0}» deve essere un numero intero tra {1} e {2}.",
            "'{0}'에는 {1}~{2} 사이의 정수를 입력해 주세요.");
        put("wizard.tooLong",
            "「{0}」太长了，最多 {1} 个字",
            "“{0}” is too long (maximum {1} characters).",
            "「{0}」が長すぎます（最大 {1} 文字）。",
            "«{0}» es demasiado largo (máximo {1} caracteres).",
            "“{0}” é longo demais (máximo de {1} caracteres).",
            "«{0}» слишком длинное (не более {1} символов).",
            "«{0}» è troppo lungo (massimo {1} caratteri).",
            "'{0}'이(가) 너무 깁니다(최대 {1}자).");
        put("wizard.badDate",
            "「{0}」的日期格式不对",
            "The date in “{0}” is not valid.",
            "「{0}」の日付が正しくありません。",
            "La fecha de «{0}» no es válida.",
            "A data em “{0}” não é válida.",
            "Неверная дата в поле «{0}».",
            "La data in «{0}» non è valida.",
            "'{0}'의 날짜 형식이 올바르지 않습니다.");
        put("wizard.badGoods",
            "「{0}」里有商品或型号已经下架了，请刷新页面后重新选择",
            "Some products or models in “{0}” are no longer available. Please refresh the page and choose again.",
            "「{0}」の一部の製品またはモデルは取り扱いを終了しました。ページを再読み込みして選び直してください。",
            "Algunos productos o modelos de «{0}» ya no están disponibles. Actualice la página y vuelva a elegir.",
            "Alguns produtos ou modelos em “{0}” não estão mais disponíveis. Atualize a página e escolha novamente.",
            "Некоторые товары или модели в «{0}» больше недоступны. Обновите страницу и выберите заново.",
            "Alcuni prodotti o modelli in «{0}» non sono più disponibili. Aggiorna la pagina e scegli di nuovo.",
            "'{0}'의 일부 제품 또는 모델은 더 이상 구매할 수 없습니다. 페이지를 새로고침한 뒤 다시 선택해 주세요.");
        put("wizard.moq",
            "选了「{0}」时总数量至少 {1}，现在是 {2}",
            "“{0}” needs a total quantity of at least {1} (currently {2}).",
            "「{0}」を選ぶ場合、合計数量は {1} 以上が必要です（現在 {2}）。",
            "«{0}» requiere una cantidad total mínima de {1} (ahora {2}).",
            "“{0}” exige uma quantidade total mínima de {1} (agora {2}).",
            "Для «{0}» общее количество должно быть не меньше {1} (сейчас {2}).",
            "«{0}» richiede una quantità totale di almeno {1} (ora {2}).",
            "'{0}'을(를) 선택하면 총수량이 최소 {1}이어야 합니다(현재 {2}).");
        put("wizard.noLines",
            "请至少选一件商品并填写数量",
            "Please choose at least one product and enter a quantity.",
            "少なくとも 1 つの製品を選び、数量を入力してください。",
            "Elija al menos un producto e indique una cantidad.",
            "Escolha pelo menos um produto e informe uma quantidade.",
            "Выберите хотя бы один товар и укажите количество.",
            "Scegli almeno un prodotto e indica una quantità.",
            "제품을 하나 이상 선택하고 수량을 입력해 주세요.");

        /* ---- 买家 3D 定制方案（2026-09-29）---- */
        put("custom.unavailable",
            "这个商品暂不支持定制",
            "This product can't be customized yet.",
            "この製品はまだカスタマイズできません。",
            "Este producto aún no se puede personalizar.",
            "Este produto ainda não pode ser personalizado.",
            "Этот товар пока нельзя настроить.",
            "Questo prodotto non è ancora personalizzabile.",
            "이 제품은 아직 맞춤 설정할 수 없습니다.");
        put("custom.badFile",
            "图片只支持 PNG 或 JPG，每张不超过 2MB、边长不超过 4096 像素",
            "Images must be PNG or JPG, up to 2 MB each and no larger than 4096 pixels on either side.",
            "画像は PNG または JPG、1 枚 2MB 以下、一辺 4096 ピクセル以下にしてください。",
            "Las imágenes deben ser PNG o JPG, de hasta 2 MB cada una y 4096 píxeles como máximo por lado.",
            "As imagens devem ser PNG ou JPG, com até 2 MB cada e no máximo 4096 pixels por lado.",
            "Изображения — только PNG или JPG, до 2 МБ каждое и не больше 4096 пикселей по стороне.",
            "Le immagini devono essere PNG o JPG, massimo 2 MB ciascuna e al massimo 4096 pixel per lato.",
            "이미지는 PNG 또는 JPG만 가능하며, 장당 2MB 이하, 한 변 4096픽셀 이하여야 합니다.");
        put("custom.badData",
            "方案内容有误，请刷新页面后重试",
            "Something is wrong with this design. Please refresh the page and try again.",
            "デザインの内容に問題があります。ページを再読み込みしてもう一度お試しください。",
            "Hay un problema con este diseño. Actualice la página e inténtelo de nuevo.",
            "Há um problema com este design. Atualize a página e tente novamente.",
            "С этим макетом что-то не так. Обновите страницу и попробуйте снова.",
            "C'è un problema con questo progetto. Aggiorna la pagina e riprova.",
            "디자인 내용에 문제가 있습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.");
        put("custom.empty",
            "请至少贴一个 logo 或换一下开机画面",
            "Add at least one logo or change the start-up screen first.",
            "少なくとも 1 つのロゴを配置するか、起動画面を変更してください。",
            "Añada al menos un logotipo o cambie la pantalla de inicio.",
            "Adicione pelo menos um logotipo ou altere a tela de inicialização.",
            "Добавьте хотя бы один логотип или измените экран включения.",
            "Aggiungi almeno un logo o cambia la schermata di avvio.",
            "로고를 하나 이상 넣거나 시작 화면을 바꿔 주세요.");
        put("custom.agreeRequired",
            "请先确认你有权使用上传的 logo",
            "Please confirm that you have the right to use the uploaded logo.",
            "アップロードしたロゴを使用する権利があることを確認してください。",
            "Confirme que tiene derecho a usar el logotipo que ha subido.",
            "Confirme que você tem o direito de usar o logotipo enviado.",
            "Подтвердите, что у вас есть право использовать загруженный логотип.",
            "Conferma di avere il diritto di usare il logo caricato.",
            "업로드한 로고를 사용할 권리가 있음을 확인해 주세요.");
        put("custom.noteTooLong",
            "备注最多 {0} 字",
            "The note can be at most {0} characters.",
            "備考は {0} 文字以内にしてください。",
            "La nota puede tener como máximo {0} caracteres.",
            "A observação pode ter no máximo {0} caracteres.",
            "Примечание — не более {0} символов.",
            "La nota può contenere al massimo {0} caratteri.",
            "메모는 최대 {0}자까지 입력할 수 있습니다.");
        put("custom.tooFrequent",
            "保存得太频繁了，请稍后再试（你的编辑内容还在页面上）",
            "Too many saves. Please try again later — your edits are still on the page.",
            "保存の回数が多すぎます。しばらくしてからもう一度お試しください（編集内容はページに残っています）。",
            "Demasiados guardados. Inténtelo más tarde; sus cambios siguen en la página.",
            "Muitas tentativas de salvar. Tente novamente mais tarde; suas edições continuam na página.",
            "Слишком много сохранений. Попробуйте позже — ваши правки остались на странице.",
            "Troppi salvataggi. Riprova più tardi: le tue modifiche sono ancora nella pagina.",
            "저장 횟수가 너무 많습니다. 잠시 후 다시 시도해 주세요(편집 내용은 페이지에 남아 있습니다).");
        put("custom.notFound",
            "找不到这个方案",
            "We couldn't find this design.",
            "このデザインが見つかりません。",
            "No encontramos este diseño.",
            "Não encontramos este design.",
            "Этот макет не найден.",
            "Non abbiamo trovato questo progetto.",
            "이 디자인을 찾을 수 없습니다.");

        /* ---- 法语（2026-09-21 起站点多开了 fr，见 SiteInfo.SUPPORTED_LANGS）---- */
        fr("common.error", "Une erreur s'est produite de notre côté. Veuillez réessayer plus tard.");
        fr("auth.verifyLinkInvalid", "Ce lien de confirmation est invalide ou a expiré. Inscrivez-vous à nouveau ou demandez-nous de le renvoyer.");
        fr("auth.accountMissing", "Ce compte n'existe pas.");
        fr("auth.tooManyAttempts", "Trop de tentatives. Patientez un moment puis réessayez.");
        fr("auth.badCredentials", "L'adresse e-mail ou le mot de passe est incorrect.");
        fr("auth.disabled", "Ce compte a été désactivé. Veuillez nous contacter.");
        fr("auth.unverified", "Votre adresse e-mail n'est pas encore confirmée. Nous venons de renvoyer l'e-mail de confirmation : ouvrez le lien, puis connectez-vous.");
        fr("auth.resetLinkInvalid", "Ce lien de réinitialisation est invalide ou a expiré. Veuillez en demander un nouveau.");
        fr("auth.oldPasswordWrong", "Le mot de passe actuel est incorrect.");
        fr("auth.noDomain", "Ce site n'est pas encore entièrement configuré, nous ne pouvons pas envoyer le lien. Veuillez nous contacter.");
        fr("auth.mailQuota", "Nous avons déjà envoyé plusieurs e-mails à cette adresse aujourd'hui. Réessayez demain ou contactez-nous directement.");
        fr("auth.badEmail", "Veuillez saisir une adresse e-mail valide.");
        fr("auth.passwordShort", "Le mot de passe doit contenir au moins {0} caractères.");
        fr("auth.passwordLong", "Le mot de passe est trop long. 72 caractères maximum.");
        fr("auth.noMailer", "L'e-mail n'est pas encore configuré sur ce site : l'inscription et la réinitialisation du mot de passe sont indisponibles. Contactez-nous directement.");
        fr("auth.signInRequired", "Veuillez d'abord vous connecter.");
        fr("mail.unavailable", "L'e-mail n'est pas encore configuré sur ce site : cette étape est indisponible. Contactez-nous directement.");
        fr("mail.failed", "L'e-mail n'a pas pu être envoyé. Réessayez plus tard ou contactez-nous directement.");
        fr("addr.empty", "Aucune adresse reçue.");
        fr("addr.recipientRequired", "Veuillez indiquer le destinataire.");
        fr("addr.countryRequired", "Veuillez indiquer le pays.");
        fr("addr.tooMany", "Vous pouvez enregistrer jusqu'à {0} adresses. Supprimez d'abord une adresse inutilisée.");
        fr("addr.missing", "Cette adresse n'existe pas.");
        fr("avatar.badPreset", "Cette couleur d'avatar n'est pas reconnue.");
        fr("avatar.tooBig", "Cette image est trop volumineuse. Choisissez-en une autre.");
        fr("avatar.badFormat", "Ce format d'avatar n'est pas pris en charge.");
        fr("order.empty", "La demande est vide.");
        fr("order.noLines", "Votre liste de demande est vide.");
        fr("order.tooManyLines", "Une demande peut contenir au maximum {0} articles.");
        fr("order.contactRequired", "Veuillez indiquer votre nom.");
        fr("order.emailRequired", "Veuillez saisir votre adresse e-mail.");
        fr("order.countryRequired", "Veuillez indiquer le pays de destination.");
        fr("order.badEmail", "Veuillez saisir une adresse e-mail valide.");
        fr("order.lineNoCode", "L'un des articles n'a pas de référence produit.");
        fr("order.badQty", "La quantité du produit {0} n'est pas valide.");
        fr("order.qtyTooBig", "La quantité du produit {0} dépasse la limite.");
        fr("order.unavailable", "Ces produits ne sont plus disponibles. Retirez-les de votre liste et réessayez : {0}");
        fr("order.tooFrequent", "Vous envoyez trop vite. Réessayez dans un instant ou écrivez-nous directement.");
        fr("order.lookupFields", "Veuillez saisir le numéro de référence et l'adresse e-mail utilisée.");
        fr("order.lookupTooFrequent", "Trop de recherches. Réessayez plus tard, ou répondez simplement à l'e-mail de confirmation que nous vous avons envoyé.");
        fr("order.notFound", "Nous n'avons pas trouvé cette demande. Vérifiez le numéro de référence et l'adresse e-mail utilisée.");
        fr("order.missing", "Cette demande n'existe pas.");
        fr("chat.empty", "Écrivez d'abord un message.");
        fr("chat.initFailed", "Impossible de démarrer la discussion. Actualisez la page et réessayez.");
        fr("chat.tooFast", "Vous envoyez trop vite. Patientez un instant avant le prochain message, ou envoyez une demande et nous répondrons dès que nous la verrons.");
        fr("chat.sessionMissing", "La session de discussion est manquante.");
        fr("chat.notFound", "Cette session de discussion n'existe pas.");
        fr("wizard.closed", "La configuration en ligne n'est pas ouverte pour le moment. Envoyez-nous vos besoins par une demande.");
        fr("wizard.required", "Veuillez compléter « {0} ».");
        fr("wizard.badOption", "Les choix de « {0} » ont changé. Actualisez la page et choisissez à nouveau.");
        fr("wizard.locked", "« {1} » ne peut pas être choisi pour « {0} » avec votre configuration actuelle.");
        fr("wizard.badNumber", "« {0} » doit être un nombre entier entre {1} et {2}.");
        fr("wizard.tooLong", "« {0} » est trop long ({1} caractères au maximum).");
        fr("wizard.badDate", "La date de « {0} » n'est pas valide.");
        fr("wizard.badGoods", "Certains produits ou modèles de « {0} » ne sont plus disponibles. Actualisez la page et choisissez à nouveau.");
        fr("wizard.moq", "« {0} » exige une quantité totale d'au moins {1} (actuellement {2}).");
        fr("wizard.noLines", "Choisissez au moins un produit et indiquez une quantité.");
        fr("custom.unavailable", "Ce produit ne peut pas encore être personnalisé.");
        fr("custom.badFile", "Les images doivent être au format PNG ou JPG, 2 Mo maximum chacune et 4096 pixels maximum par côté.");
        fr("custom.badData", "Ce design comporte une erreur. Actualisez la page et réessayez.");
        fr("custom.empty", "Ajoutez au moins un logo ou modifiez l'écran de démarrage.");
        fr("custom.agreeRequired", "Veuillez confirmer que vous avez le droit d'utiliser le logo importé.");
        fr("custom.noteTooLong", "La note peut contenir au maximum {0} caractères.");
        fr("custom.tooFrequent", "Trop d'enregistrements. Réessayez plus tard : vos modifications sont toujours sur la page.");
        fr("custom.notFound", "Nous n'avons pas trouvé ce design.");
    }

    /** 给一个键补法语那一份。分开写是因为 put 已经有八个参数，再加一个读不动了 */
    private static void fr(String key, String text)
    {
        Map<String, String> row = TEXT.get(key);
        if (row != null)
        {
            row.put("fr", text);
        }
    }

    /** 按语言取一句并填 {0} {1}…。缺键退回英文，英文也缺退回中文，都缺返回键名 */
    public static String render(String key, String lang, Object... args)
    {
        Map<String, String> row = TEXT.get(key);
        if (row == null)
        {
            return key;
        }
        String code = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        String text = row.get(code);
        if (text == null)
        {
            text = row.get("en");
        }
        if (text == null)
        {
            text = row.get("zh");
        }
        return fill(text == null ? key : text, args);
    }

    /** 中文那一份：抛异常时的 message，进日志和管理台 */
    public static String zh(String key, Object... args)
    {
        return render(key, "zh", args);
    }

    /** 带文案键的 {@link ServiceException}。message 是中文，响应出去时按请求语言换（ApiExceptionHandler） */
    public static ServiceException error(String key, int code, Object... args)
    {
        return new ServiceException(zh(key, args), code, key, args);
    }

    private static String fill(String text, Object... args)
    {
        if (args == null)
        {
            return text;
        }
        String out = text;
        for (int i = 0; i < args.length; i++)
        {
            out = out.replace("{" + i + "}", args[i] == null ? "" : String.valueOf(args[i]));
        }
        return out;
    }
}
