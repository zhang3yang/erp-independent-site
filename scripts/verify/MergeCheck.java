import java.util.*;

/**
 * SiteOpenService 的三层合并 + 拼接真值表。
 *
 * overlay 和 compose 那一组是从 SiteOpenService.java 里**逐字拷**过来的
 * （下面的 check-merge-copy.js 会拿真源码比一遍，防止这份悄悄和真代码不一样；
 * 它比的是去掉注释之后的代码，所以这里不用把那几大段 javadoc 也抄一遍）。
 * siteContent 这里只保留合并那几步，JSON 解析那部分没动过，不用测。
 */
public class MergeCheck {

    // ======== 以下与 SiteOpenService 中同名方法逐字一致 ========
    private static void overlay(Map<String, Object> merged, Object layer)
    {
        if (!(layer instanceof Map))
        {
            return;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) layer).entrySet())
        {
            if (entry.getKey() == null)
            {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object incoming = entry.getValue();
            Object current = merged.get(key);
            if (current instanceof Map && incoming instanceof Map)
            {
                // 拷一份再改：底下那份可能是共享层的对象，直接改会污染它
                Map<String, Object> combined = new LinkedHashMap<>();
                ((Map<?, ?>) current).forEach((k, v) -> combined.put(String.valueOf(k), v));
                ((Map<?, ?>) incoming).forEach((k, v) -> combined.put(String.valueOf(k), v));
                merged.put(key, combined);
            }
            else
            {
                merged.put(key, incoming);
            }
        }
    }
    // ======== 拷贝结束 ========

    // ======== 以下到「拼接结束」为止，与 SiteOpenService 里那份逐字一致 ========

    private static void compose(Map<String, Object> merged)
    {
        joinRows(merged, merged, "nav", "navText");
        joinRows(merged, merged, "highlights", "highlightText");
        joinRows(merged, merged, "capabilities", "capText");
        joinNested(merged, "hero", "slides", "slideText");
        joinNested(merged, "about", "stats", "aboutStatText");
        joinNested(merged, "reach", "stats", "reachStatText");
        joinScalar(merged, "bento", "stat", "statValue", "value", "statLabel", "label");
        // 2026-09-21 新增的四块。都走 joinNested：列表在自己那个键底下的 items 里，
        // 文案袋子仍然放语言层顶层（见本方法第 2 条规矩）
        joinNested(merged, "trust", "items", "trustText");
        joinNested(merged, "reviews", "items", "reviewText");
        joinNested(merged, "partners", "items", "partnerText");
        joinNested(merged, "faq", "items", "faqText");
    }

    private static void joinRows(Map<String, Object> merged, Map<String, Object> owner,
            String listKey, String textKey)
    {
        // 先删：列表不存在时，文案袋子也不该漏到接口返回里去
        Object textValue = merged.remove(textKey);
        Object listValue = owner.get(listKey);
        if (!(listValue instanceof List))
        {
            return;
        }
        Map<?, ?> text = textValue instanceof Map ? (Map<?, ?>) textValue : null;

        List<Object> out = new ArrayList<>();
        for (Object row : (List<?>) listValue)
        {
            if (!(row instanceof Map))
            {
                // 脏数据（字符串、数字）原样留着，交给前台那边的判断去处理
                out.add(row);
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            ((Map<?, ?>) row).forEach((k, v) -> item.put(String.valueOf(k), v));
            Object id = item.get("id");
            Object bag = text == null || id == null ? null : text.get(String.valueOf(id));
            if (bag instanceof Map)
            {
                ((Map<?, ?>) bag).forEach((k, v) -> item.put(String.valueOf(k), v));
            }
            out.add(item);
        }
        owner.put(listKey, out);
    }

    private static void joinNested(Map<String, Object> merged, String parentKey, String listKey, String textKey)
    {
        Object parent = merged.get(parentKey);
        if (!(parent instanceof Map))
        {
            // 这一块整个没配。袋子还是要删掉，不然它会单独出现在返回里
            merged.remove(textKey);
            return;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        ((Map<?, ?>) parent).forEach((k, v) -> copy.put(String.valueOf(k), v));
        joinRows(merged, copy, listKey, textKey);
        merged.put(parentKey, copy);
    }

    private static void joinScalar(Map<String, Object> merged, String parentKey, String targetKey,
            String sharedKey, String sharedField, String textKey, String textField)
    {
        Object parent = merged.get(parentKey);
        if (!(parent instanceof Map))
        {
            return;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        ((Map<?, ?>) parent).forEach((k, v) -> copy.put(String.valueOf(k), v));
        Object sharedValue = copy.remove(sharedKey);
        Object textValue = copy.remove(textKey);
        if (sharedValue != null || textValue != null)
        {
            Map<String, Object> target = new LinkedHashMap<>();
            Object old = copy.get(targetKey);
            if (old instanceof Map)
            {
                ((Map<?, ?>) old).forEach((k, v) -> target.put(String.valueOf(k), v));
            }
            if (sharedValue != null)
            {
                target.put(sharedField, sharedValue);
            }
            if (textValue != null)
            {
                target.put(textField, textValue);
            }
            copy.put(targetKey, target);
        }
        merged.put(parentKey, copy);
    }

    // ======== 拼接结束 ========

    /** siteContent 的合并部分 */
    static Map<String, Object> siteContent(Map<String, Object> root, String defaultLang, String language)
    {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet())
        {
            if (!"i18n".equals(entry.getKey()) && !"v".equals(entry.getKey()))
            {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        Object i18n = root.get("i18n");
        if (i18n instanceof Map)
        {
            Map<?, ?> byLang = (Map<?, ?>) i18n;
            if (defaultLang != null && !defaultLang.equalsIgnoreCase(language))
            {
                overlay(merged, byLang.get(defaultLang));
            }
            overlay(merged, byLang.get(language));
        }
        compose(merged);
        return merged;
    }

    // ---------------- 小工具 ----------------
    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
    static int failed = 0;
    static void expect(String name, Object actual, Object want) {
        boolean ok = Objects.equals(String.valueOf(actual), String.valueOf(want));
        if (!ok) failed++;
        System.out.println((ok ? "  ok   " : "  失败 ") + name
                + (ok ? "" : "\n         想要: " + want + "\n         得到: " + actual));
    }

    public static void main(String[] args) {

        System.out.println("1. v2 老数据：图片和电话都在语言层，共享层只有 logo");
        Map<String, Object> v2 = map(
            "v", 2, "logo", "L",
            "i18n", map(
                "en", map("companyName", "EN Co",
                          "hero", map("title", "EN Hero", "image", "/en.jpg"),
                          "contact", map("address", "EN Addr", "phone", "+86 1")),
                "ja", map("companyName", "JA Co",
                          "hero", map("title", "JA Hero", "image", "/ja.jpg"),
                          "contact", map("address", "JA Addr", "phone", "+86 1"))));
        Map<String, Object> r1 = siteContent(v2, "en", "ja");
        expect("日语拿到日语的标题", ((Map<?, ?>) r1.get("hero")).get("title"), "JA Hero");
        expect("日语拿到日语的图", ((Map<?, ?>) r1.get("hero")).get("image"), "/ja.jpg");
        expect("logo 还是共享层的", r1.get("logo"), "L");

        System.out.println("2. v3：共享层的对象和语言层的对象要合一层");
        Map<String, Object> v3 = map(
            "v", 3, "logo", "L",
            "hero", map("image", "/shared.jpg"),
            "contact", map("phone", "+86 1", "email", "a@b.c"),
            "reach", map("hq", map("name", "Tianjin")),
            "order", Arrays.asList("hero", "about"),
            "i18n", map(
                "en", map("hero", map("title", "EN Hero"), "contact", map("address", "EN Addr")),
                "ja", map("hero", map("title", "JA Hero"))));
        Map<String, Object> r2 = siteContent(v3, "en", "en");
        expect("共享的图 + 语言的标题", r2.get("hero"), "{image=/shared.jpg, title=EN Hero}");
        expect("共享的电话邮箱 + 语言的地址", r2.get("contact"), "{phone=+86 1, email=a@b.c, address=EN Addr}");
        expect("order 原样透传", r2.get("order"), "[hero, about]");
        expect("v 不出现在返回里", r2.containsKey("v"), false);
        expect("i18n 不出现在返回里", r2.containsKey("i18n"), false);

        System.out.println("3. 只译了一半：缺的那项按项退回默认语言（2026-09-10 那次改动的重点）");
        Map<String, Object> r3 = siteContent(v3, "en", "ja");
        expect("日语有自己的标题", ((Map<?, ?>) r3.get("hero")).get("title"), "JA Hero");
        expect("日语没配地址 → 退回英文的", ((Map<?, ?>) r3.get("contact")).get("address"), "EN Addr");
        expect("共享的图还在", ((Map<?, ?>) r3.get("hero")).get("image"), "/shared.jpg");

        System.out.println("4. 合并不能污染共享层那份对象（同一次请求里连着取两种语言）");
        Map<String, Object> shared = map(
            "v", 3, "hero", map("image", "/shared.jpg"),
            "i18n", map("en", map("hero", map("title", "EN")), "ja", map("hero", map("title", "JA"))));
        siteContent(shared, "en", "ja");
        Map<?, ?> heroAfter = (Map<?, ?>) shared.get("hero");
        expect("共享层的 hero 没被写脏", heroAfter.toString(), "{image=/shared.jpg}");

        System.out.println("5. 数组是整份替换，不按下标拼");
        Map<String, Object> arr = map(
            "v", 3, "nav", Arrays.asList("S1", "S2", "S3"),
            "i18n", map("en", map("nav", Arrays.asList("E1"))));
        expect("语言层给了一条就是一条", siteContent(arr, "en", "en").get("nav"), "[E1]");

        System.out.println("6. 语言压根没配 → 整份退回默认语言");
        expect("德语退回英文", ((Map<?, ?>) siteContent(v3, "en", "de").get("hero")).get("title"), "EN Hero");

        System.out.println("7. v1 老数据（没有 i18n 键）原样返回");
        Map<String, Object> v1 = map("companyName", "Old Co", "nav", Arrays.asList("N"));
        expect("v1 不动", siteContent(v1, "en", "ja").get("companyName"), "Old Co");

        System.out.println("8. 默认语言就是当前语言时不重复铺");
        expect("英文站取英文", ((Map<?, ?>) siteContent(v3, "en", "en").get("hero")).get("title"), "EN Hero");

        /* ================= 以下是 v4：结构共享一份、文案分语言 ================= */

        Map<String, Object> v4 = map(
            "v", 4, "logo", "L",
            "nav", Arrays.asList(map("id", "n1", "url", "/products"), map("id", "n2", "url", "/blog")),
            "highlights", Arrays.asList(map("id", "h1", "icon", "★")),
            "hero", map("slides", Arrays.asList(map("id", "s1", "image", "/a.jpg"),
                                                map("id", "s2", "image", "/b.jpg"))),
            "about", map("image", "/about.jpg", "stats", Arrays.asList(map("id", "t1", "value", "15+"))),
            "bento", map("statValue", "60+"),
            "i18n", map(
                "en", map("navText", map("n1", map("title", "Products"), "n2", map("title", "Blog")),
                          "highlightText", map("h1", map("title", "EN Fast", "desc", "EN desc")),
                          "slideText", map("s1", map("title", "EN One"), "s2", map("title", "EN Two")),
                          "aboutStatText", map("t1", map("label", "Years")),
                          "bento", map("statLabel", "Countries")),
                "ja", map("navText", map("n1", map("title", "製品")),
                          "slideText", map("s2", map("title", "JA Two")))));

        System.out.println("9. v4：共享的条目 + 语言层的文案，靠 id 拼起来");
        Map<String, Object> r9 = siteContent(v4, "en", "en");
        expect("导航拼成前台那种形状", r9.get("nav"),
                "[{id=n1, url=/products, title=Products}, {id=n2, url=/blog, title=Blog}]");
        expect("特色：共享的图标 + 语言的文字", r9.get("highlights"),
                "[{id=h1, icon=★, title=EN Fast, desc=EN desc}]");
        expect("数字亮点：共享的数值 + 语言的说明",
                ((Map<?, ?>) r9.get("about")).get("stats"), "[{id=t1, value=15+, label=Years}]");
        expect("粉色数字卡：共享的数值 + 语言的说明",
                ((Map<?, ?>) r9.get("bento")).get("stat"), "{value=60+, label=Countries}");
        expect("about 的共享配图还在", ((Map<?, ?>) r9.get("about")).get("image"), "/about.jpg");

        System.out.println("10. 文案袋子是存储细节，不许出现在接口返回里");
        expect("navText 没漏出去", r9.containsKey("navText"), false);
        expect("highlightText 没漏出去", r9.containsKey("highlightText"), false);
        expect("slideText 没漏出去", r9.containsKey("slideText"), false);
        expect("aboutStatText 没漏出去", r9.containsKey("aboutStatText"), false);
        expect("bento 里不留 statValue/statLabel", r9.get("bento"), "{stat={value=60+, label=Countries}}");

        System.out.println("11. 只译了几条：按 id 退回默认语言，没译的那条不会空着");
        Map<String, Object> r11 = siteContent(v4, "en", "ja");
        expect("译了的用日语", ((Map<?, ?>) ((List<?>) r11.get("nav")).get(0)).get("title"), "製品");
        expect("没译的那条退回英文", ((Map<?, ?>) ((List<?>) r11.get("nav")).get(1)).get("title"), "Blog");
        expect("条数和网址是共享的，日语不会少一条", ((List<?>) r11.get("nav")).size(), 2);

        System.out.println("12. 袋子放在语言层顶层才有「按条退回」——藏在 hero 底下就没有了");
        List<?> jaSlides = (List<?>) ((Map<?, ?>) r11.get("hero")).get("slides");
        expect("第二张用日语", ((Map<?, ?>) jaSlides.get(1)).get("title"), "JA Two");
        expect("第一张日语没译 → 退回英文", ((Map<?, ?>) jaSlides.get(0)).get("title"), "EN One");
        expect("图是共享的", ((Map<?, ?>) jaSlides.get(0)).get("image"), "/a.jpg");

        System.out.println("13. v3 老数据：整条都在语言层、没有 id → 原样放行");
        Map<String, Object> old = map(
            "v", 3,
            "i18n", map(
                "en", map("nav", Arrays.asList(map("title", "Home", "url", "/")),
                          "highlights", Arrays.asList(map("icon", "★", "title", "Fast", "desc", "D")),
                          "bento", map("stat", map("value", "15+", "label", "Years"))),
                "ja", map("nav", Arrays.asList(map("title", "ホーム", "url", "/")))));
        Map<String, Object> r13 = siteContent(old, "en", "ja");
        expect("老导航照常显示", r13.get("nav"), "[{title=ホーム, url=/}]");
        expect("老特色照常显示（日语没配 → 退回英文）", r13.get("highlights"),
                "[{icon=★, title=Fast, desc=D}]");
        expect("老的粉色数字卡照常显示",
                ((Map<?, ?>) r13.get("bento")).get("stat"), "{value=15+, label=Years}");

        System.out.println("14. 拼接也不能污染共享层（连着取两种语言）");
        siteContent(v4, "en", "en");
        siteContent(v4, "en", "ja");
        expect("共享层的 nav 还是只有 id 和网址",
                v4.get("nav"), "[{id=n1, url=/products}, {id=n2, url=/blog}]");
        expect("共享层的 hero 没被写脏",
                v4.get("hero"), "{slides=[{id=s1, image=/a.jpg}, {id=s2, image=/b.jpg}]}");
        expect("共享层的 bento 没被写脏", v4.get("bento"), "{statValue=60+}");

        System.out.println("15. 只配了结构、一种语言的文案都还没填");
        Map<String, Object> bare = map(
            "v", 4, "nav", Arrays.asList(map("id", "n1", "url", "/products")),
            "i18n", map("en", map("companyName", "Co")));
        expect("导航条目还在，只是没有名字",
                siteContent(bare, "en", "en").get("nav"), "[{id=n1, url=/products}]");

        /* ====== 2026-09-13 加的三样：按钮链接（共享）、图片说明（分语言）====== */

        System.out.println("16. Banner 按钮链接在共享层，和语言层的按钮文字合到一个 hero 底下");
        Map<String, Object> v16 = map(
            "v", 4,
            "hero", map("slides", Arrays.asList(map("id", "s1", "image", "/a.jpg")),
                        "btn1Url", "/products", "btn2Url", "#inquiry"),
            "about", map("image", "/about.jpg"),
            "i18n", map(
                "en", map("hero", map("title", "EN Hero", "btn1", "View products"),
                          "slideText", map("s1", map("alt", "EN alt")),
                          "about", map("title", "About", "imageAlt", "EN about alt")),
                "ja", map("hero", map("title", "JA Hero"),
                          "slideText", map("s1", map("title", "JA One")))));

        Map<String, Object> r16 = siteContent(v16, "en", "en");
        Map<?, ?> hero16 = (Map<?, ?>) r16.get("hero");
        expect("共享的链接和语言层的文字都在", hero16.get("btn1Url"), "/products");
        expect("次按钮链接也在", hero16.get("btn2Url"), "#inquiry");
        expect("按钮文字没被挤掉", hero16.get("btn1"), "View products");
        // ⚠ 这一条是「同一个父键底下必须各用各的键名」那条规矩的回归测试：
        //   btn1（语言层）和 btn1Url（共享层）重名的话，语言层那份会把共享层整个换掉
        expect("图片说明按 id 拼进幻灯片",
                ((List<?>) hero16.get("slides")).get(0).toString(),
                "{id=s1, image=/a.jpg, alt=EN alt}");
        expect("关于我们的图片说明跟着 about 合过来",
                ((Map<?, ?>) r16.get("about")).get("imageAlt"), "EN about alt");
        expect("about 的共享配图没被语言层挤掉",
                ((Map<?, ?>) r16.get("about")).get("image"), "/about.jpg");

        System.out.println("17. 链接是共享的：切到日语一个字都不变");
        Map<String, Object> r17 = siteContent(v16, "en", "ja");
        Map<?, ?> hero17 = (Map<?, ?>) r17.get("hero");
        expect("日语下链接不变", hero17.get("btn1Url"), "/products");
        expect("日语没配 about → 整块退回英文",
                ((Map<?, ?>) r17.get("about")).get("imageAlt"), "EN about alt");

        /*
         * ⚠⚠ 退回的粒度是**条目**，不是字段。这一条最容易想当然，写下来钉住：
         *
         *   overlay 只合一层。`slideText` 在顶层，所以它自己会和默认语言那份合一次
         *   ——合的是「有没有 s1 这一条」。一旦日语给了 s1，**s1 那整个小对象**
         *   就被日语那份整个换掉，里面缺的字段不会再逐个退回英文。
         *
         * 所以「日语只译了标题、没填图片说明」的结果是日语下那张图**没有** alt，
         * 而不是沿用英文的 alt。这是对的：一句英文说明配在日语页面上，
         * 对读屏用户和 Google 图片都是错的信号，不如没有（没有 = 纯装饰图）。
         *
         * 整条都没译的那种才按条退回，见第 12 组。
         */
        Map<?, ?> jaSlide = (Map<?, ?>) ((List<?>) hero17.get("slides")).get(0);
        expect("日语译了标题", jaSlide.get("title"), "JA One");
        expect("同一条里没填的字段不会逐个退回（退回按条目，不按字段）", jaSlide.get("alt"), null);

        expect("共享层的 hero 没被写脏", v16.get("hero"),
                "{slides=[{id=s1, image=/a.jpg}], btn1Url=/products, btn2Url=#inquiry}");

        System.out.println(failed == 0 ? "\n全部通过" : "\n有 " + failed + " 条不通过");
        if (failed > 0) System.exit(1);
    }
}
