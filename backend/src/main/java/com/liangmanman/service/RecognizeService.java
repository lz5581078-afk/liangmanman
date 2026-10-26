package com.liangmanman.service;

import com.liangmanman.dto.SkuView;
import com.liangmanman.entity.StandardProduct;
import com.liangmanman.integration.OcrService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 拍包装识别：OCR 出文字后，提取条码、登记证号、规格、品牌、商品名、生产企业，
 * 再到商品库里精确找“这一款”（条码，或登记证号 + 规格），找不到给几个候选。
 */
@Service
public class RecognizeService {

    /** 登记证号 / 审定编号：农药 PD、LS、WP、WL；肥料 “农肥(2020)准字1234号”“鲁农肥(2018)临字0123号”；种子 “国审玉20210001” */
    private static final List<Pattern> REG_NOS = List.of(
            Pattern.compile("(PD|LS|WP|WL)\\s?\\d{6,}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\\u4e00-\\u9fa5]{0,4}肥\\s?[（(]\\s?\\d{4}\\s?[）)]\\s?[准临]字\\s?[（(]?\\d{3,6}[）)]?号"),
            Pattern.compile("(国审|[\\u4e00-\\u9fa5]审)\\s?(玉|麦|稻|棉|豆|油|麻|薯)\\s?\\d{6,}"));
    private static final Pattern SPEC = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|KG|Kg|g|G|ml|mL|ML|L|千克|公斤|克|毫升|升|斤)");
    private static final Pattern BARCODE = Pattern.compile("(?<!\\d)(\\d{13})(?!\\d)");
    private static final Pattern BRAND = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z]{2,8})牌");
    /** 包装上常见、但不是商品名的词 */
    private static final List<String> NOISE = List.of("净含量", "生产日期", "保质期", "地址", "电话", "厂家", "有限公司",
            "执行标准", "批号", "登记证", "注意事项", "使用方法", "警示", "毒性", "生产许可", "邮编", "网址", "准字", "审定");

    private final OcrService ocr;
    private final ProductService productService;
    private final SkuService skuService;

    public RecognizeService(OcrService ocr, ProductService productService, SkuService skuService) {
        this.ocr = ocr;
        this.productService = productService;
        this.skuService = skuService;
    }

    /**
     * @param keyword    猜出的商品名（买家拍照搜索时拿去搜）
     * @param match      商品库里精确认出的这一款；没认出为空
     * @param candidates 可能是的款（不含 match），让人挑
     */
    public record Result(List<String> lines, String keyword, String brand, String spec, String regNo, String barcode,
                         String manufacturer, SkuView match, List<SkuView> candidates) {}

    public Result recognize(byte[] image) {
        return parse(ocr.recognize(image));
    }

    Result parse(List<String> lines) {
        if (lines.isEmpty()) {
            return new Result(List.of(), null, null, null, null, null, null, null, List.of());
        }
        String all = String.join(" ", lines);
        String regNo = null;
        for (Pattern p : REG_NOS) {
            Matcher m = p.matcher(all);
            if (m.find()) {
                regNo = SkuService.normRegNo(m.group());
                break;
            }
        }
        String spec = null;
        Matcher s = SPEC.matcher(all);
        if (s.find()) spec = SkuService.normSpec(s.group(1) + s.group(2));
        String barcode = null;
        Matcher b = BARCODE.matcher(all.replace(" ", ""));
        while (b.find()) {
            if (SkuService.validEan(b.group(1))) {
                barcode = b.group(1);
                break;
            }
        }
        String brand = null;
        Matcher br = BRAND.matcher(all);
        if (br.find()) brand = br.group(1) + "牌";
        String manufacturer = null;
        for (String line : lines) {
            if (line.contains("有限公司")) {
                manufacturer = line.replaceAll("^.*[：:]", "").trim();
                break;
            }
        }
        String keyword = guessName(lines, brand);

        StandardProduct match = skuService.match(new SkuService.Identity(barcode, regNo, spec, brand, keyword, manufacturer));
        Map<Long, StandardProduct> found = new LinkedHashMap<>();
        if (regNo != null) {
            skuService.byRegNo(regNo).forEach(p -> found.put(p.getId(), p));
        }
        if (keyword != null) {
            for (String kw : keywordVariants(keyword)) {
                if (found.size() >= 10) break;
                productService.standardProducts(kw, null).forEach(p -> found.putIfAbsent(p.getId(), p));
            }
        }
        if (match != null) found.remove(match.getId());
        List<SkuView> candidates = new ArrayList<>(found.values().stream().limit(10).map(SkuView::of).toList());
        return new Result(lines, keyword, brand, spec, regNo, barcode, manufacturer, SkuView.of(match), candidates);
    }

    /** 商品名：第一行含汉字、不是说明性文字、不只是规格、也不只是品牌的文字 */
    static String guessName(List<String> lines, String brand) {
        for (String line : lines) {
            String t = line.trim();
            if (!t.matches(".*[\\u4e00-\\u9fa5].*")) continue;
            if (NOISE.stream().anyMatch(t::contains)) continue;
            if (brand != null) t = t.replace(brand, "").trim();
            String withoutSpec = SPEC.matcher(t).replaceAll("").trim();
            if (withoutSpec.replaceAll("[^\\u4e00-\\u9fa5]", "").length() < 2) continue;
            return t.length() > 32 ? t.substring(0, 32) : t;
        }
        return null;
    }

    /** 整行搜不到时，退到只用汉字部分、再退到前 3 个字 */
    private static List<String> keywordVariants(String keyword) {
        List<String> out = new ArrayList<>();
        out.add(keyword);
        String chinese = keyword.replaceAll("[^\\u4e00-\\u9fa5]", "");
        if (!chinese.isEmpty() && !chinese.equals(keyword)) out.add(chinese);
        if (chinese.length() > 3) out.add(chinese.substring(0, 3));
        return out;
    }
}
