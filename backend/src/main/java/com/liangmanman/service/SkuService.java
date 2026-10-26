package com.liangmanman.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liangmanman.common.BizException;
import com.liangmanman.entity.StandardProduct;
import com.liangmanman.mapper.StandardProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 商品库（具体一款）的识别规则，只在这里维护一份：
 * 条码相同 → 同一款；登记证号 + 规格都相同 → 同一款。只有登记证号、没有规格时不算确定。
 */
@Service
public class SkuService {

    private final StandardProductMapper mapper;

    public SkuService(StandardProductMapper mapper) {
        this.mapper = mapper;
    }

    /** 包装上认出来的、或老板填的“身份信息” */
    public record Identity(String barcode, String regNo, String spec, String brand, String name, String manufacturer) {
        public boolean identifiable() {
            return barcode != null || (regNo != null && spec != null);
        }
    }

    public static String normBarcode(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\s", "");
        return d.matches("\\d{8,14}") ? d : null;
    }

    /** 登记证号统一写法：去空格、全角括号换半角、字母大写 */
    public static String normRegNo(String s) {
        if (s == null) return null;
        String r = s.replaceAll("\\s", "").replace('（', '(').replace('）', ')').toUpperCase(Locale.ROOT);
        return r.isEmpty() ? null : (r.length() > 64 ? r.substring(0, 64) : r);
    }

    /** 规格统一写法：50KG / 50 千克 / 50公斤 → 50kg */
    public static String normSpec(String s) {
        if (s == null) return null;
        String r = s.replaceAll("\\s", "")
                .replaceAll("(?i)(千克|公斤|kg)$", "kg")
                .replaceAll("(?i)(毫升|ml)$", "ml")
                .replaceAll("(克|G)$", "g")
                .replaceAll("(?<!m)(升|l)$", "L");
        return r.isEmpty() ? null : (r.length() > 32 ? r.substring(0, 32) : r);
    }

    /** EAN-13 / EAN-8 校验位 */
    public static boolean validEan(String code) {
        if (code == null || !(code.matches("\\d{13}") || code.matches("\\d{8}"))) return false;
        int sum = 0;
        int n = code.length();
        for (int i = 0; i < n - 1; i++) {
            int d = code.charAt(n - 2 - i) - '0';
            sum += (i % 2 == 0) ? d * 3 : d;
        }
        return (10 - sum % 10) % 10 == code.charAt(n - 1) - '0';
    }

    /** 精确找到这一款；找不到返回 null */
    public StandardProduct match(Identity id) {
        String barcode = normBarcode(id.barcode());
        if (barcode != null) {
            StandardProduct s = mapper.selectOne(new LambdaQueryWrapper<StandardProduct>()
                    .eq(StandardProduct::getBarcode, barcode).last("LIMIT 1"));
            if (s != null) return s;
        }
        String regNo = normRegNo(id.regNo());
        String spec = normSpec(id.spec());
        if (regNo != null && spec != null) {
            return mapper.selectOne(new LambdaQueryWrapper<StandardProduct>()
                    .eq(StandardProduct::getRegNo, regNo).eq(StandardProduct::getSpec, spec).last("LIMIT 1"));
        }
        return null;
    }

    /** 同一个登记证号下的所有款（不同规格） */
    public List<StandardProduct> byRegNo(String regNo) {
        String r = normRegNo(regNo);
        if (r == null) return List.of();
        return mapper.selectList(new LambdaQueryWrapper<StandardProduct>().eq(StandardProduct::getRegNo, r).last("LIMIT 20"));
    }

    /** 搜索框里输的是条码或登记证号时，精确命中的款 */
    public List<StandardProduct> exactByCode(String keyword) {
        String barcode = normBarcode(keyword);
        if (barcode != null) {
            return mapper.selectList(new LambdaQueryWrapper<StandardProduct>().eq(StandardProduct::getBarcode, barcode));
        }
        String r = normRegNo(keyword);
        if (r != null && r.matches(".*\\d{4,}.*")) {
            return mapper.selectList(new LambdaQueryWrapper<StandardProduct>().eq(StandardProduct::getRegNo, r).last("LIMIT 20"));
        }
        return List.of();
    }

    public StandardProduct require(Long id) {
        StandardProduct s = id == null ? null : mapper.selectById(id);
        if (s == null) throw BizException.notFound("这款商品");
        return s;
    }

    /**
     * 上架时用：已有这一款就用它；没有、但条码或“登记证号 + 规格”齐全，就自动建档（待核实）。
     * 信息不够确定一款时返回 null，商品先不挂款，等运营归一。
     */
    public StandardProduct findOrCreate(Identity id, Long categoryId, String image, Long storeId) {
        StandardProduct found = match(id);
        if (found != null) {
            enrich(found, id);
            return found;
        }
        if (!id.identifiable()) return null;
        StandardProduct s = new StandardProduct();
        s.setCategoryId(categoryId);
        s.setBrand(trim(id.brand(), 32));
        s.setName(id.name() == null || id.name().isBlank() ? "未命名商品" : trim(id.name(), 64));
        s.setSpec(normSpec(id.spec()));
        s.setRegNo(normRegNo(id.regNo()));
        s.setBarcode(normBarcode(id.barcode()));
        s.setManufacturer(trim(id.manufacturer(), 64));
        s.setImage(image);
        s.setStatus(StandardProduct.PENDING);
        s.setSourceStoreId(storeId);
        mapper.insert(s);
        return s;
    }

    /**
     * 同一款在别家店拍到了更多身份信息（比如先前只有条码，这次包装上还认出了登记证号），补上空着的字段，
     * 以后按登记证号也能认出来。已有的不覆盖。
     */
    public void enrich(StandardProduct s, Identity id) {
        String barcode = normBarcode(id.barcode());
        String regNo = normRegNo(id.regNo());
        String spec = normSpec(id.spec());
        boolean changed = false;
        if (s.getBarcode() == null && barcode != null
                && mapper.selectCount(new LambdaQueryWrapper<StandardProduct>().eq(StandardProduct::getBarcode, barcode)) == 0) {
            s.setBarcode(barcode);
            changed = true;
        }
        if (s.getRegNo() == null && regNo != null && (spec == null || s.getSpec() == null || spec.equals(s.getSpec()))) {
            s.setRegNo(regNo);
            changed = true;
        }
        if (s.getSpec() == null && spec != null) {
            s.setSpec(spec);
            changed = true;
        }
        if (s.getManufacturer() == null && id.manufacturer() != null) {
            s.setManufacturer(trim(id.manufacturer(), 64));
            changed = true;
        }
        if (changed) mapper.updateById(s);
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
