package com.ruoyi.station.console.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.ruoyi.station.console.domain.SiteInfo;

/**
 * 发给买家的三封信（邮箱验证 / 重置密码 / 下单回执）的文案，按语言。
 *
 * <p>语言集合和前台 {@code station-site/app/composables/useShopCopy.ts} 一致，也是
 * {@link SiteInfo#SUPPORTED_LANGS} 那八种。缺键退回英文（{@link #t}），
 * 不会发出一封带键名的信。</p>
 *
 * <p>为什么放这里而不是前台传整句：邮件是后端发的，正文里要拼订单号、链接、金额，
 * 让前台传一整封信过来等于让浏览器决定我们的邮件说什么——蜜罐都拦不住那种滥用。
 * 前台只传一个语言码，其余都在这儿。</p>
 *
 * <p>⚠ 起订量单位（{@link #unit}）也在这里翻：单位是商家在商品上填的一个短串
 * （pcs / 件 / set），库里不分语言。前台 {@code app/lib/units.ts} 有同一张表，
 * 两边的键名要一致；认不出的原样返回。</p>
 */
public final class BuyerMailCopy
{
    private BuyerMailCopy()
    {
    }

    private static final Map<String, Map<String, String>> COPY = new HashMap<>();
    private static final Map<String, Map<String, String>> UNITS = new HashMap<>();
    private static final Map<String, String> UNIT_KEYS = new HashMap<>();

    static
    {
        Map<String, String> en = new LinkedHashMap<>();
        en.put("hello", "Hello,");
        en.put("helloName", "Hello {name},");
        en.put("signoff", "— {site}");
        en.put("verifySubject", "Confirm your email - {site}");
        en.put("verifyIntro", "Please confirm your email address for {site} by opening the link below:");
        en.put("verifyExpires", "The link expires in {n} hours.");
        en.put("verifyIgnore", "If you did not create an account, just ignore this email — no account is active until this link is opened.");
        en.put("resetSubject", "Reset your password - {site}");
        en.put("resetIntro", "We received a request to reset the password for your {site} account.");
        en.put("resetExpires", "The link expires in {n} hour(s).");
        en.put("resetIgnore", "If you did not request this, ignore this email — your current password stays unchanged.");
        en.put("orderSubject", "We received your request {ref} - {site}");
        en.put("orderThanks", "Thank you — we have your request. Our team will reply by email with a full quotation including freight, usually within one working day.");
        en.put("orderRef", "Reference: {ref}");
        en.put("orderItems", "What you asked us about:");
        en.put("orderTotal", "Indicative total: {currency} {amount}");
        en.put("orderInquire", "({n} item(s) are quoted on request and are not included in that figure.)");
        en.put("orderNoPayment", "This figure is based on list prices. Freight and taxes are not included, and no payment has been taken — this website does not take payments.");
        en.put("orderOpen", "You can open your request here:");
        en.put("orderOtherDevice", "On another device it will ask for this email address to confirm it is you.");
        en.put("orderReply", "Just reply to this email if anything changed.");
        COPY.put("en", en);

        Map<String, String> zh = new LinkedHashMap<>();
        zh.put("hello", "您好，");
        zh.put("helloName", "{name}，您好：");
        zh.put("signoff", "— {site}");
        zh.put("verifySubject", "请确认您的邮箱 - {site}");
        zh.put("verifyIntro", "请打开下面的链接，确认这是您在 {site} 使用的邮箱：");
        zh.put("verifyExpires", "链接 {n} 小时内有效。");
        zh.put("verifyIgnore", "如果您没有注册过账号，忽略这封邮件即可——不点链接账号不会生效。");
        zh.put("resetSubject", "重置您的密码 - {site}");
        zh.put("resetIntro", "我们收到了重置您 {site} 账号密码的请求。");
        zh.put("resetExpires", "链接 {n} 小时内有效。");
        zh.put("resetIgnore", "如果不是您本人发起的，忽略这封邮件即可，现在的密码不会变。");
        zh.put("orderSubject", "已收到您的询价 {ref} - {site}");
        zh.put("orderThanks", "谢谢，您的询价我们收到了。我们会通过邮件回复含运费的完整报价，通常在一个工作日内。");
        zh.put("orderRef", "单号：{ref}");
        zh.put("orderItems", "您询价的商品：");
        zh.put("orderTotal", "参考合计：{currency} {amount}");
        zh.put("orderInquire", "（其中 {n} 项价格面议，未计入上面的数字。）");
        zh.put("orderNoPayment", "这个数字按标价计算，不含运费和税费；本网站不收款，您没有支付任何费用。");
        zh.put("orderOpen", "您可以在这里查看这张单：");
        zh.put("orderOtherDevice", "换一台设备打开时，会请您输入这个邮箱地址以确认身份。");
        zh.put("orderReply", "有任何变化，直接回复这封邮件即可。");
        COPY.put("zh", zh);

        Map<String, String> ja = new LinkedHashMap<>();
        ja.put("hello", "こんにちは。");
        ja.put("helloName", "{name} 様");
        ja.put("signoff", "— {site}");
        ja.put("verifySubject", "メールアドレスの確認 - {site}");
        ja.put("verifyIntro", "下のリンクを開いて、{site} でお使いのメールアドレスを確認してください。");
        ja.put("verifyExpires", "リンクの有効期限は {n} 時間です。");
        ja.put("verifyIgnore", "アカウントを作成した覚えがない場合は、このメールを無視してください。リンクを開かない限りアカウントは有効になりません。");
        ja.put("resetSubject", "パスワードの再設定 - {site}");
        ja.put("resetIntro", "{site} のアカウントについて、パスワード再設定のご依頼を受け付けました。");
        ja.put("resetExpires", "リンクの有効期限は {n} 時間です。");
        ja.put("resetIgnore", "ご自身の操作でない場合は、このメールを無視してください。現在のパスワードはそのままです。");
        ja.put("orderSubject", "お見積依頼 {ref} を受け付けました - {site}");
        ja.put("orderThanks", "ご依頼ありがとうございます。送料込みの正式なお見積りを、通常 1 営業日以内にメールでお送りします。");
        ja.put("orderRef", "受付番号：{ref}");
        ja.put("orderItems", "ご依頼内容：");
        ja.put("orderTotal", "参考合計：{currency} {amount}");
        ja.put("orderInquire", "（うち {n} 点は個別見積のため、上記の金額には含まれていません。）");
        ja.put("orderNoPayment", "この金額は定価に基づく参考値で、送料と税金は含まれていません。当サイトでは決済を行わないため、お支払いは発生していません。");
        ja.put("orderOpen", "ご依頼はこちらから確認できます：");
        ja.put("orderOtherDevice", "別の端末で開く場合は、本人確認のためこのメールアドレスの入力をお願いします。");
        ja.put("orderReply", "変更がありましたら、このメールにそのままご返信ください。");
        COPY.put("ja", ja);

        Map<String, String> es = new LinkedHashMap<>();
        es.put("hello", "Hola:");
        es.put("helloName", "Hola, {name}:");
        es.put("signoff", "— {site}");
        es.put("verifySubject", "Confirme su correo - {site}");
        es.put("verifyIntro", "Confirme su dirección de correo para {site} abriendo el siguiente enlace:");
        es.put("verifyExpires", "El enlace caduca en {n} horas.");
        es.put("verifyIgnore", "Si no creó ninguna cuenta, ignore este correo: ninguna cuenta estará activa hasta que se abra el enlace.");
        es.put("resetSubject", "Restablezca su contraseña - {site}");
        es.put("resetIntro", "Hemos recibido una solicitud para restablecer la contraseña de su cuenta en {site}.");
        es.put("resetExpires", "El enlace caduca en {n} hora(s).");
        es.put("resetIgnore", "Si usted no lo solicitó, ignore este correo: su contraseña actual no cambia.");
        es.put("orderSubject", "Hemos recibido su solicitud {ref} - {site}");
        es.put("orderThanks", "Gracias, hemos recibido su solicitud. Le responderemos por correo con una cotización completa que incluye el flete, normalmente en un día laborable.");
        es.put("orderRef", "Referencia: {ref}");
        es.put("orderItems", "Lo que nos ha solicitado:");
        es.put("orderTotal", "Total orientativo: {currency} {amount}");
        es.put("orderInquire", "({n} artículo(s) se cotizan bajo petición y no están incluidos en esa cifra.)");
        es.put("orderNoPayment", "Esta cifra se basa en los precios de lista. No incluye flete ni impuestos, y no se ha cobrado nada: este sitio web no acepta pagos.");
        es.put("orderOpen", "Puede abrir su solicitud aquí:");
        es.put("orderOtherDevice", "En otro dispositivo se le pedirá esta dirección de correo para confirmar que es usted.");
        es.put("orderReply", "Si algo cambia, responda a este correo.");
        COPY.put("es", es);

        Map<String, String> pt = new LinkedHashMap<>();
        pt.put("hello", "Olá,");
        pt.put("helloName", "Olá, {name},");
        pt.put("signoff", "— {site}");
        pt.put("verifySubject", "Confirme o seu e-mail - {site}");
        pt.put("verifyIntro", "Confirme o seu endereço de e-mail para {site} abrindo o link abaixo:");
        pt.put("verifyExpires", "O link expira em {n} horas.");
        pt.put("verifyIgnore", "Se você não criou uma conta, ignore este e-mail: nenhuma conta fica ativa até que o link seja aberto.");
        pt.put("resetSubject", "Redefina a sua senha - {site}");
        pt.put("resetIntro", "Recebemos um pedido para redefinir a senha da sua conta em {site}.");
        pt.put("resetExpires", "O link expira em {n} hora(s).");
        pt.put("resetIgnore", "Se não foi você, ignore este e-mail: a sua senha atual continua a mesma.");
        pt.put("orderSubject", "Recebemos a sua solicitação {ref} - {site}");
        pt.put("orderThanks", "Obrigado, recebemos a sua solicitação. Responderemos por e-mail com um orçamento completo incluindo o frete, normalmente em um dia útil.");
        pt.put("orderRef", "Referência: {ref}");
        pt.put("orderItems", "O que você nos pediu:");
        pt.put("orderTotal", "Total indicativo: {currency} {amount}");
        pt.put("orderInquire", "({n} item(ns) são cotados sob consulta e não estão incluídos nesse valor.)");
        pt.put("orderNoPayment", "Este valor é baseado nos preços de tabela. Frete e impostos não estão incluídos, e nada foi cobrado: este site não recebe pagamentos.");
        pt.put("orderOpen", "Você pode abrir a sua solicitação aqui:");
        pt.put("orderOtherDevice", "Em outro dispositivo, será pedido este endereço de e-mail para confirmar que é você.");
        pt.put("orderReply", "Se algo mudar, basta responder a este e-mail.");
        COPY.put("pt", pt);

        Map<String, String> ru = new LinkedHashMap<>();
        ru.put("hello", "Здравствуйте!");
        ru.put("helloName", "Здравствуйте, {name}!");
        ru.put("signoff", "— {site}");
        ru.put("verifySubject", "Подтвердите вашу почту - {site}");
        ru.put("verifyIntro", "Подтвердите адрес электронной почты для {site}, открыв ссылку ниже:");
        ru.put("verifyExpires", "Ссылка действует {n} ч.");
        ru.put("verifyIgnore", "Если вы не создавали аккаунт, просто проигнорируйте это письмо: аккаунт не активируется, пока ссылка не открыта.");
        ru.put("resetSubject", "Сброс пароля - {site}");
        ru.put("resetIntro", "Мы получили запрос на сброс пароля для вашего аккаунта на {site}.");
        ru.put("resetExpires", "Ссылка действует {n} ч.");
        ru.put("resetIgnore", "Если это были не вы, проигнорируйте письмо: текущий пароль останется прежним.");
        ru.put("orderSubject", "Мы получили ваш запрос {ref} - {site}");
        ru.put("orderThanks", "Спасибо, ваш запрос получен. Мы ответим по электронной почте полным предложением с учётом доставки, обычно в течение одного рабочего дня.");
        ru.put("orderRef", "Номер: {ref}");
        ru.put("orderItems", "Что вы запросили:");
        ru.put("orderTotal", "Ориентировочная сумма: {currency} {amount}");
        ru.put("orderInquire", "({n} поз. — цена по запросу, в сумму выше не включены.)");
        ru.put("orderNoPayment", "Сумма рассчитана по прайс-листу. Доставка и налоги не включены, оплата не списывалась: этот сайт не принимает платежи.");
        ru.put("orderOpen", "Ваш запрос можно открыть здесь:");
        ru.put("orderOtherDevice", "На другом устройстве потребуется ввести этот адрес почты для подтверждения.");
        ru.put("orderReply", "Если что-то изменилось, просто ответьте на это письмо.");
        COPY.put("ru", ru);

        Map<String, String> it = new LinkedHashMap<>();
        it.put("hello", "Buongiorno,");
        it.put("helloName", "Buongiorno {name},");
        it.put("signoff", "— {site}");
        it.put("verifySubject", "Conferma la tua email - {site}");
        it.put("verifyIntro", "Conferma il tuo indirizzo email per {site} aprendo il link qui sotto:");
        it.put("verifyExpires", "Il link scade tra {n} ore.");
        it.put("verifyIgnore", "Se non hai creato un account, ignora questa email: nessun account è attivo finché il link non viene aperto.");
        it.put("resetSubject", "Reimposta la password - {site}");
        it.put("resetIntro", "Abbiamo ricevuto una richiesta di reimpostare la password del tuo account su {site}.");
        it.put("resetExpires", "Il link scade tra {n} ora/e.");
        it.put("resetIgnore", "Se non sei stato tu, ignora questa email: la password attuale resta invariata.");
        it.put("orderSubject", "Abbiamo ricevuto la tua richiesta {ref} - {site}");
        it.put("orderThanks", "Grazie, abbiamo ricevuto la tua richiesta. Ti risponderemo via email con un preventivo completo, trasporto incluso, di norma entro un giorno lavorativo.");
        it.put("orderRef", "Riferimento: {ref}");
        it.put("orderItems", "Cosa ci hai chiesto:");
        it.put("orderTotal", "Totale indicativo: {currency} {amount}");
        it.put("orderInquire", "({n} articolo/i con prezzo su richiesta, non inclusi in questa cifra.)");
        it.put("orderNoPayment", "La cifra si basa sui prezzi di listino. Trasporto e tasse esclusi; nessun pagamento è stato addebitato: questo sito non accetta pagamenti.");
        it.put("orderOpen", "Puoi aprire la tua richiesta qui:");
        it.put("orderOtherDevice", "Da un altro dispositivo ti verrà chiesto questo indirizzo email per confermare che sei tu.");
        it.put("orderReply", "Se qualcosa cambia, rispondi semplicemente a questa email.");
        COPY.put("it", it);

        Map<String, String> ko = new LinkedHashMap<>();
        ko.put("hello", "안녕하세요.");
        ko.put("helloName", "{name} 님, 안녕하세요.");
        ko.put("signoff", "— {site}");
        ko.put("verifySubject", "이메일 확인 - {site}");
        ko.put("verifyIntro", "아래 링크를 열어 {site}에서 사용하는 이메일 주소를 확인해 주세요.");
        ko.put("verifyExpires", "링크는 {n}시간 동안 유효합니다.");
        ko.put("verifyIgnore", "계정을 만든 적이 없다면 이 메일을 무시하셔도 됩니다. 링크를 열기 전까지 계정은 활성화되지 않습니다.");
        ko.put("resetSubject", "비밀번호 재설정 - {site}");
        ko.put("resetIntro", "{site} 계정의 비밀번호 재설정 요청을 받았습니다.");
        ko.put("resetExpires", "링크는 {n}시간 동안 유효합니다.");
        ko.put("resetIgnore", "본인이 요청하지 않았다면 이 메일을 무시하세요. 현재 비밀번호는 그대로 유지됩니다.");
        ko.put("orderSubject", "견적 요청 {ref}을(를) 받았습니다 - {site}");
        ko.put("orderThanks", "감사합니다. 요청을 받았습니다. 운임을 포함한 정식 견적을 보통 1영업일 이내에 이메일로 보내드립니다.");
        ko.put("orderRef", "접수번호: {ref}");
        ko.put("orderItems", "요청하신 제품:");
        ko.put("orderTotal", "참고 합계: {currency} {amount}");
        ko.put("orderInquire", "({n}개 품목은 별도 견적 대상이며 위 금액에 포함되지 않았습니다.)");
        ko.put("orderNoPayment", "이 금액은 정가 기준이며 운임과 세금은 포함되지 않았습니다. 이 사이트는 결제를 받지 않으므로 청구된 금액은 없습니다.");
        ko.put("orderOpen", "요청 내용은 여기에서 확인할 수 있습니다:");
        ko.put("orderOtherDevice", "다른 기기에서 열 때는 본인 확인을 위해 이 이메일 주소를 입력하시게 됩니다.");
        ko.put("orderReply", "변경 사항이 있으면 이 메일에 답장해 주세요.");
        COPY.put("ko", ko);

        Map<String, String> fr = new LinkedHashMap<>();
        fr.put("hello", "Bonjour,");
        fr.put("helloName", "Bonjour {name},");
        fr.put("signoff", "— {site}");
        fr.put("verifySubject", "Confirmez votre adresse e-mail - {site}");
        fr.put("verifyIntro", "Veuillez confirmer votre adresse e-mail pour {site} en ouvrant le lien ci-dessous :");
        fr.put("verifyExpires", "Le lien expire dans {n} heures.");
        fr.put("verifyIgnore", "Si vous n'avez pas créé de compte, ignorez simplement cet e-mail : aucun compte n'est actif tant que ce lien n'est pas ouvert.");
        fr.put("resetSubject", "Réinitialisez votre mot de passe - {site}");
        fr.put("resetIntro", "Nous avons reçu une demande de réinitialisation du mot de passe de votre compte {site}.");
        fr.put("resetExpires", "Le lien expire dans {n} heure(s).");
        fr.put("resetIgnore", "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : votre mot de passe actuel reste inchangé.");
        fr.put("orderSubject", "Nous avons bien reçu votre demande {ref} - {site}");
        fr.put("orderThanks", "Merci, nous avons bien reçu votre demande. Notre équipe vous répondra par e-mail avec un devis complet incluant le transport, généralement sous un jour ouvré.");
        fr.put("orderRef", "Référence : {ref}");
        fr.put("orderItems", "Ce que vous nous avez demandé :");
        fr.put("orderTotal", "Total indicatif : {currency} {amount}");
        fr.put("orderInquire", "({n} article(s) sur devis, non inclus dans ce montant.)");
        fr.put("orderNoPayment", "Ce montant est basé sur les prix catalogue. Transport et taxes non inclus ; aucun paiement n'a été prélevé, ce site n'encaisse pas de paiement.");
        fr.put("orderOpen", "Vous pouvez consulter votre demande ici :");
        fr.put("orderOtherDevice", "Sur un autre appareil, cette adresse e-mail vous sera demandée pour confirmer votre identité.");
        fr.put("orderReply", "Si quelque chose change, répondez simplement à cet e-mail.");
        COPY.put("fr", fr);

        /* ---- 起订量单位。键名和前台 app/lib/units.ts 一致 ---- */
        String[][] aliases = {
            { "pcs", "pcs", "pc", "piece", "pieces", "件", "个", "只", "枚", "颗", "支" },
            { "set", "set", "sets", "套" },
            { "pair", "pair", "pairs", "双", "对" },
            { "unit", "unit", "units", "台" },
            { "box", "box", "boxes", "盒" },
            { "carton", "carton", "cartons", "ctn", "ctns", "箱" },
            { "roll", "roll", "rolls", "卷" },
            { "bag", "bag", "bags", "袋" },
            { "pack", "pack", "packs", "包" },
            { "bottle", "bottle", "bottles", "瓶" },
            { "sheet", "sheet", "sheets", "张" },
            { "pallet", "pallet", "pallets", "托", "托盘" },
            { "dozen", "dozen", "doz", "打" },
            { "kg", "kg", "kgs", "千克", "公斤" },
            { "g", "g", "gram", "grams", "克" },
            { "ton", "ton", "tons", "t", "吨" },
            { "m", "m", "meter", "meters", "metre", "metres", "米" },
            { "cm", "cm", "厘米" },
            { "mm", "mm", "毫米" },
            { "l", "l", "liter", "liters", "litre", "litres", "升" },
            { "ml", "ml", "毫升" },
            { "sqm", "sqm", "m2", "m²", "平方米", "平米" }
        };
        for (String[] row : aliases)
        {
            for (int i = 1; i < row.length; i++)
            {
                UNIT_KEYS.put(row[i].toLowerCase(Locale.ROOT), row[0]);
            }
        }
        unitTable("en", "pcs", "set", "pair", "unit", "box", "carton", "roll", "bag", "pack", "bottle",
                "sheet", "pallet", "dozen", "kg", "g", "t", "m", "cm", "mm", "L", "ml", "m²");
        unitTable("zh", "件", "套", "双", "台", "盒", "箱", "卷", "袋", "包", "瓶",
                "张", "托", "打", "千克", "克", "吨", "米", "厘米", "毫米", "升", "毫升", "平方米");
        unitTable("ja", "個", "セット", "組", "台", "箱", "カートン", "ロール", "袋", "パック", "本",
                "枚", "パレット", "ダース", "kg", "g", "t", "m", "cm", "mm", "L", "ml", "m²");
        unitTable("es", "uds.", "juego", "par", "unidad", "caja", "cartón", "rollo", "bolsa", "paquete", "botella",
                "hoja", "palé", "docena", "kg", "g", "t", "m", "cm", "mm", "L", "ml", "m²");
        unitTable("pt", "un.", "conjunto", "par", "unidade", "caixa", "caixa de papelão", "rolo", "saco", "pacote", "garrafa",
                "folha", "palete", "dúzia", "kg", "g", "t", "m", "cm", "mm", "L", "ml", "m²");
        unitTable("ru", "шт.", "компл.", "пара", "ед.", "коробка", "короб", "рулон", "мешок", "упак.", "бутылка",
                "лист", "паллета", "дюжина", "кг", "г", "т", "м", "см", "мм", "л", "мл", "м²");
        unitTable("it", "pz.", "set", "paio", "unità", "scatola", "cartone", "rotolo", "sacco", "confezione", "bottiglia",
                "foglio", "pallet", "dozzina", "kg", "g", "t", "m", "cm", "mm", "L", "ml", "m²");
        unitTable("ko", "개", "세트", "쌍", "대", "박스", "카톤", "롤", "봉지", "팩", "병",
                "장", "팔레트", "다스", "kg", "g", "t", "m", "cm", "mm", "L", "ml", "m²");
        unitTable("fr", "pcs", "jeu", "paire", "unité", "boîte", "carton", "rouleau", "sac", "paquet", "bouteille",
                "feuille", "palette", "douzaine", "kg", "g", "t", "m", "cm", "mm", "L", "ml", "m²");
    }

    private static final String[] UNIT_ORDER = {
        "pcs", "set", "pair", "unit", "box", "carton", "roll", "bag", "pack", "bottle",
        "sheet", "pallet", "dozen", "kg", "g", "ton", "m", "cm", "mm", "l", "ml", "sqm"
    };

    private static void unitTable(String lang, String... labels)
    {
        Map<String, String> table = new HashMap<>();
        for (int i = 0; i < UNIT_ORDER.length && i < labels.length; i++)
        {
            table.put(UNIT_ORDER[i], labels[i]);
        }
        UNITS.put(lang, table);
    }

    /** 把请求里的语言码收敛成站点开了的那一种；不认识、没传都退回站点默认语言 */
    public static String resolve(SiteInfo site, String lang)
    {
        if (site == null)
        {
            return "en";
        }
        return site.resolveLang(lang);
    }

    /** 取一句文案；缺键退回英文，英文也缺就返回键名（那是写代码时的错，不该发生） */
    public static String t(String lang, String key)
    {
        Map<String, String> table = lang == null ? null : COPY.get(lang.toLowerCase(Locale.ROOT));
        String text = table == null ? null : table.get(key);
        if (text == null)
        {
            text = COPY.get("en").get(key);
        }
        return text == null ? key : text;
    }

    /** 取一句并填占位符：{@code t(lang, "orderRef", "ref", "DD0001")} */
    public static String t(String lang, String key, String... kv)
    {
        String text = t(lang, key);
        for (int i = 0; i + 1 < kv.length; i += 2)
        {
            text = text.replace("{" + kv[i] + "}", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return text;
    }

    /**
     * 起订量单位翻成当前语言。商家填的是 pcs / 件 / set 这种短串，认得出的按表翻，
     * 认不出的（{@code 1000pcs/ctn} 这种自由文本）原样返回——原样总比错译好。
     */
    public static String unit(String lang, String raw)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            return "";
        }
        String key = UNIT_KEYS.get(raw.trim().toLowerCase(Locale.ROOT));
        if (key == null)
        {
            return raw.trim();
        }
        Map<String, String> table = lang == null ? null : UNITS.get(lang.toLowerCase(Locale.ROOT));
        String label = table == null ? null : table.get(key);
        if (label == null)
        {
            label = UNITS.get("en").get(key);
        }
        return label == null ? raw.trim() : label;
    }

    /** 带语言前缀的站内路径：默认语言不带前缀，和前台 useLocalePath 同一条规矩 */
    public static String langPath(SiteInfo site, String lang, String path)
    {
        String resolved = resolve(site, lang);
        boolean isDefault = site == null || resolved.equalsIgnoreCase(site.defaultLang());
        return (isDefault ? "" : "/" + resolved) + path;
    }
}
