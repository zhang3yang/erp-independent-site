package com.ruoyi.system.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 月表聚合查询的调用范围检查。
 *
 * <h3>要钉住的是什么</h3>
 * <p>业务表按 {@code yyyyMM} 分月，表名是 {@code ${tableSuffix}} 拼进 SQL 的。一条
 * <b>带聚合</b>的查询（sum/count/max/min）通常在回答「这张单累计已经用掉多少配额」——
 * 已入库多少、已退多少、已收多少钱。这类问题的答案<b>横跨所有月份</b>：
 * 8/30 建的待审核单和 9/1 建的那张属于同一张采购单，只查当月就等于漏掉一半。</p>
 *
 * <p>漏掉的后果不是报错，是<b>校验静默失效</b>——超收、超退、超额认领都能通过，
 * 直到对账时才发现。已经真实发生过一次：{@code selectPendingInboundQty} 只查当月，
 * 跨月建的两张待审核入库单都能通过「剩余未到货数量」校验，采购单收货量翻倍。</p>
 *
 * <h3>怎么判的</h3>
 * <p>对每条「单月表 + 带聚合」的 select，找出它在 {@code src/main/java} 里的调用点，
 * 在所在方法里解出「哪些变量装着全部月表后缀」，再要求<b>这次调用的实参里真的用到了其中一个</b>。</p>
 *
 * <p>后缀变量按三条来源认定，并做一轮传递（{@code List<String> s = ...Suffixes();
 * for (String x : s)} 里 {@code x} 也算）：</p>
 * <ul>
 *   <li>{@code for (String x : ...TableSuffixes() / validSuffixes(...) / validDescending(...))}；</li>
 *   <li>{@code List<String> x = ...TableSuffixes() / validSuffixes(...) / validDescending(...)}；</li>
 *   <li>方法签名收 {@code List<String> xxxSuffixes}，即遍历由上层负责。</li>
 * </ul>
 *
 * <p><b>判据必须落到实参上，不能只看方法体里出没出现过后缀来源。</b>只看方法体的话，
 * 一个方法里既有别处用的后缀列表、又有一次只传当月的调用，测试照样是绿的——
 * 这正是本测试第一版的漏洞，是拿真实 bug 做变异验证时发现的。</p>
 *
 * <p>语句自己用 {@code <foreach collection="suffixes">} union 了全部月份的，
 * 不在检查范围内——它本身就是跨月的。</p>
 *
 * <h3>新增查询被这个测试拦下来怎么办</h3>
 * <p>先想清楚它回答的是不是「累计用掉多少」。是 → 把调用方改成遍历全部月表，或者让 SQL
 * 自己 union；不是（点查、按月列表、单张单据内部求和） → 加进下面的 {@link #SINGLE_MONTH_BY_DESIGN}
 * 并写明理由。<b>别为了让测试变绿而随手加白名单</b>，这个测试存在的全部意义就是逼这一次思考。</p>
 */
class CrossMonthAggregateScopeTest
{
    /**
     * 按设计就该只看一个月的语句，key 是 {@code 文件名#语句id}。
     *
     * <p>加进来必须写清楚为什么单月是对的。</p>
     */
    private static final Map<String, String> SINGLE_MONTH_BY_DESIGN = new LinkedHashMap<>();
    static
    {
        SINGLE_MONTH_BY_DESIGN.put("InventoryLedgerMapper.xml#selectLedgerCount",
                "分页总数，跟着列表查的就是那一个月");
        SINGLE_MONTH_BY_DESIGN.put("BuyerMapper.xml#countOrdersInMonth",
                "方法名就写明了按月统计");
        SINGLE_MONTH_BY_DESIGN.put("PurchaseReceiveMapper.xml#selectList",
                "按月列表，月份由调用方选");
        SINGLE_MONTH_BY_DESIGN.put("ShipBatchMapper.xml#selectList",
                "按月列表，月份由调用方选");
        SINGLE_MONTH_BY_DESIGN.put("ShipBatchMapper.xml#selectByCode",
                "按单号点查，月份随单号一起传进来；查错月份返回 null，调用方报「批次不存在」");
        SINGLE_MONTH_BY_DESIGN.put("ShipBatchItemMapper.xml#sumQtyByShipCode",
                "汇总一张批次自己的明细，批次和明细必定同月");
        SINGLE_MONTH_BY_DESIGN.put("DashboardMapper.xml#selectSalesAmountByCurrency",
                "首页看板的「本月销售额」，一个自然月就是它要回答的问题本身，"
                + "不是在累计某张单用掉多少配额——跨月加起来反而把卡片上的口径算错");
    }

    /** 月表名插值：${tableSuffix} / ${suffix} 之类 */
    private static final Pattern MONTH_INTERPOLATION =
            Pattern.compile("\\$\\{\\s*\\w*[Ss]uffix\\w*\\s*\\}");
    /** 聚合函数 —— 有它才说明这条查询在回答「累计多少」 */
    private static final Pattern AGGREGATE =
            Pattern.compile("\\b(sum|count|max|min)\\s*\\(", Pattern.CASE_INSENSITIVE);
    /** 语句自己 union 了全部月份 */
    private static final Pattern SELF_UNION =
            Pattern.compile("<foreach[^>]*collection=\"(suffixes|tableSuffixes)\"");
    private static final Pattern SELECT =
            Pattern.compile("<select\\b([^>]*)>(.*?)</select>", Pattern.DOTALL);
    private static final Pattern SQL_FRAGMENT =
            Pattern.compile("<sql\\b[^>]*\\bid=\"([^\"]+)\"[^>]*>(.*?)</sql>", Pattern.DOTALL);
    private static final Pattern INCLUDE =
            Pattern.compile("<include\\b[^>]*\\brefid=\"([^\"]+)\"[^>]*/?>");
    private static final Pattern STATEMENT_ID = Pattern.compile("id=\"([^\"]+)\"");
    /** 方法声明行：4 个空格缩进 + 访问修饰符 */
    private static final Pattern METHOD_DECL =
            Pattern.compile("^ {4}(?:public|private|protected)\\s[^;]*$", Pattern.MULTILINE);
    /** 后缀来源：拿到「全部月表后缀」的那几种写法 */
    private static final Pattern SUFFIX_SOURCE =
            Pattern.compile("TableSuffixes\\s*\\(|validSuffixes\\s*\\(|validDescending\\s*\\(");
    /** 方法签名 / 局部变量里的 List&lt;String&gt; xxxSuffixes，遍历交给上层 */
    private static final Pattern SUFFIX_LIST_DECL =
            Pattern.compile("List\\s*<\\s*String\\s*>\\s*(\\w*[Ss]uffixes)\\b");
    /** List&lt;String&gt; x = 后缀来源(...) */
    private static final Pattern SUFFIX_LIST_ASSIGN =
            Pattern.compile("List\\s*<\\s*String\\s*>\\s*(\\w+)\\s*=([^;]*);", Pattern.DOTALL);
    /** for (String x : 迭代源) */
    private static final Pattern SUFFIX_FOR_EACH =
            Pattern.compile("for\\s*\\(\\s*(?:final\\s+)?String\\s+(\\w+)\\s*:([^)]*)\\)", Pattern.DOTALL);

    @Test
    void everyMonthTableAggregateIsQueriedAcrossAllMonths() throws IOException
    {
        List<RiskyStatement> risky = collectRiskyStatements();
        // 扫不到东西多半是路径变了，静默通过等于这个测试白写
        assertFalse(risky.isEmpty(), "没有扫到任何「单月表 + 聚合」的语句，检查 mapper 目录路径是不是变了");

        Map<String, String> javaSources = loadJavaSources();
        List<String> problems = new ArrayList<>();

        for (RiskyStatement statement : risky)
        {
            if (SINGLE_MONTH_BY_DESIGN.containsKey(statement.key()))
            {
                continue;
            }
            List<CallSite> callSites = findCallSites(javaSources, statement.id);
            if (callSites.isEmpty())
            {
                // 没人调用的聚合查询：要么是死代码，要么调用点写法没被识别出来。
                // 两种都该有人看一眼，不放过。
                problems.add(statement.key() + " —— 没找到任何调用点。"
                        + "如果是死代码请删掉，如果是按设计只看一个月请加进 SINGLE_MONTH_BY_DESIGN");
                continue;
            }
            for (CallSite site : callSites)
            {
                if (!site.iteratesAllMonths())
                {
                    problems.add(statement.key() + " 在 " + site.file + ":" + site.line
                            + "（方法 " + site.method + "）只传了一个月表后缀。"
                            + "带聚合的月表查询要遍历全部月份，"
                            + "参考 SalesReturnServiceImpl.loadReturnable 的写法");
                }
            }
        }

        if (!problems.isEmpty())
        {
            fail("月表聚合查询的调用范围不对（详见本测试类的类注释）：\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    /**
     * 白名单不能烂掉：里面的每一条都必须仍然对得上一条真实的「单月表 + 聚合」语句。
     * 语句改名或改成跨月之后忘了清白名单，下一次真出问题时就少一道防线。
     */
    @Test
    void whitelistHasNoStaleEntries() throws IOException
    {
        Set<String> actual = collectRiskyStatements().stream()
                .map(RiskyStatement::key).collect(Collectors.toCollection(TreeSet::new));
        List<String> stale = SINGLE_MONTH_BY_DESIGN.keySet().stream()
                .filter(key -> !actual.contains(key)).collect(Collectors.toList());
        if (!stale.isEmpty())
        {
            fail("SINGLE_MONTH_BY_DESIGN 里有已经对不上的条目，请删掉：" + stale);
        }
    }

    // ------------------------------------------------------------------
    // 扫描
    // ------------------------------------------------------------------

    private List<RiskyStatement> collectRiskyStatements() throws IOException
    {
        List<RiskyStatement> result = new ArrayList<>();
        for (Path file : listFiles(resolve("src/main/resources/mapper"), ".xml"))
        {
            String source = read(file);
            Map<String, String> fragments = new LinkedHashMap<>();
            Matcher fragment = SQL_FRAGMENT.matcher(source);
            while (fragment.find())
            {
                fragments.put(fragment.group(1), fragment.group(2));
            }
            Matcher select = SELECT.matcher(source);
            while (select.find())
            {
                String body = inlineIncludes(select.group(2), fragments, 0);
                if (!MONTH_INTERPOLATION.matcher(body).find()) continue;
                if (!AGGREGATE.matcher(body).find()) continue;
                if (SELF_UNION.matcher(body).find()) continue;
                Matcher id = STATEMENT_ID.matcher(select.group(1));
                if (!id.find()) continue;
                result.add(new RiskyStatement(file.getFileName().toString(), id.group(1)));
            }
        }
        return result;
    }

    private String inlineIncludes(String body, Map<String, String> fragments, int depth)
    {
        if (depth > 6) return body;
        Matcher include = INCLUDE.matcher(body);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (include.find())
        {
            out.append(body, last, include.start());
            String referenced = fragments.get(include.group(1));
            out.append(referenced == null ? include.group() : inlineIncludes(referenced, fragments, depth + 1));
            last = include.end();
        }
        out.append(body.substring(last));
        return out.toString();
    }

    private Map<String, String> loadJavaSources() throws IOException
    {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Path file : listFiles(resolve("src/main/java"), ".java"))
        {
            sources.put(file.getFileName().toString(), read(file));
        }
        return sources;
    }

    private List<CallSite> findCallSites(Map<String, String> javaSources, String statementId)
    {
        Pattern call = Pattern.compile("\\.\\s*" + Pattern.quote(statementId) + "\\s*\\(");
        List<CallSite> sites = new ArrayList<>();
        for (Map.Entry<String, String> entry : javaSources.entrySet())
        {
            String source = entry.getValue();
            // Mapper 接口里的声明不是调用点
            if (entry.getKey().endsWith("Mapper.java")) continue;
            Matcher matcher = call.matcher(source);
            while (matcher.find())
            {
                sites.add(new CallSite(entry.getKey(), source, matcher.start(),
                        readArguments(source, matcher.end())));
            }
        }
        return sites;
    }

    /** 从 {@code (} 之后读到配平的 {@code )}，取出这次调用的实参原文 */
    private static String readArguments(String source, int afterOpeningParen)
    {
        int depth = 1;
        for (int i = afterOpeningParen; i < source.length(); i++)
        {
            char c = source.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return source.substring(afterOpeningParen, i);
        }
        return source.substring(afterOpeningParen);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 兼容从模块目录和仓库根目录两种工作目录跑测试 */
    private Path resolve(String relative)
    {
        Path direct = Paths.get(relative);
        if (Files.isDirectory(direct)) return direct;
        Path fromRoot = Paths.get("ruoyi-system").resolve(relative);
        if (Files.isDirectory(fromRoot)) return fromRoot;
        throw new IllegalStateException("找不到目录：" + relative + "（当前工作目录 "
                + Paths.get("").toAbsolutePath() + "）");
    }

    private List<Path> listFiles(Path root, String extension) throws IOException
    {
        try (Stream<Path> walk = Files.walk(root))
        {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .collect(Collectors.toList());
        }
    }

    private String read(Path path)
    {
        try
        {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static final class RiskyStatement
    {
        private final String file;
        private final String id;

        RiskyStatement(String file, String id)
        {
            this.file = file;
            this.id = id;
        }

        String key()
        {
            return file + "#" + id;
        }
    }

    /** 一个调用点，连同它所在方法的正文和这次调用的实参 */
    private static final class CallSite
    {
        private final String file;
        private final int line;
        private final String method;
        private final String methodBody;
        private final String arguments;

        CallSite(String file, String source, int offset, String arguments)
        {
            this.file = file;
            this.line = countLines(source, offset);
            this.arguments = arguments;
            // 往回找最近一个方法声明，往后找下一个，中间就是方法体。
            // 括号配平的严谨解析在这里不值得——判据只是「方法体里有没有遍历后缀」，
            // 多带上或少带上几行相邻代码都不改变结论。
            int start = 0;
            int end = source.length();
            String signature = "?";
            Matcher declaration = METHOD_DECL.matcher(source);
            while (declaration.find())
            {
                if (declaration.start() <= offset)
                {
                    start = declaration.start();
                    signature = declaration.group().trim();
                }
                else
                {
                    end = declaration.start();
                    break;
                }
            }
            this.method = signature.length() > 80 ? signature.substring(0, 80) + "…" : signature;
            this.methodBody = source.substring(start, end);
        }

        /**
         * 这次调用的实参里，有没有用到装着全部月表后缀的变量。
         *
         * <p>先在方法体里解出「后缀变量」的集合，再看实参引不引用它们。只看方法体
         * 出没出现过后缀来源是不够的——同一个方法里完全可能一边有正确的后缀列表、
         * 一边有一次只传当月的调用。</p>
         */
        boolean iteratesAllMonths()
        {
            Set<String> suffixVars = new LinkedHashSet<>();

            // ① 方法签名 / 局部声明里的 List<String> xxxSuffixes
            Matcher declared = SUFFIX_LIST_DECL.matcher(methodBody);
            while (declared.find()) suffixVars.add(declared.group(1));

            // ② List<String> x = 后缀来源(...)
            Matcher assigned = SUFFIX_LIST_ASSIGN.matcher(methodBody);
            while (assigned.find())
            {
                if (SUFFIX_SOURCE.matcher(assigned.group(2)).find()) suffixVars.add(assigned.group(1));
            }

            // ③ for (String x : 后缀来源(...) 或 已知的后缀变量)
            //    跑两轮做传递闭包：List<String> s = ...Suffixes(); for (String x : s) 里 x 也算
            for (int round = 0; round < 2; round++)
            {
                Matcher loop = SUFFIX_FOR_EACH.matcher(methodBody);
                while (loop.find())
                {
                    String iterable = loop.group(2);
                    boolean fromSuffixes = SUFFIX_SOURCE.matcher(iterable).find()
                            || suffixVars.stream().anyMatch(v -> referencedIn(iterable, v));
                    if (fromSuffixes) suffixVars.add(loop.group(1));
                }
            }

            return suffixVars.stream().anyMatch(v -> referencedIn(arguments, v));
        }

        /** 整词匹配，避免 suffix 命中 tableSuffix 这种包含关系 */
        private static boolean referencedIn(String text, String identifier)
        {
            return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(identifier) + "(?![A-Za-z0-9_])")
                    .matcher(text).find();
        }

        private static int countLines(String source, int offset)
        {
            int line = 1;
            for (int i = 0; i < offset; i++)
            {
                if (source.charAt(i) == '\n') line++;
            }
            return line;
        }
    }
}
