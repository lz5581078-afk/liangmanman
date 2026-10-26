package com.liangmanman.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.liangmanman.common.BizException;
import com.liangmanman.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 拍包装识别文字：腾讯云通用印刷体识别（GeneralBasicOCR 2018-11-19）。
 * ocrMock=true 时返回一段示例文字，方便开发联调。
 */
@Slf4j
@Service
public class OcrService {

    private final TencentCloudClient tencent;
    private final boolean mock;

    public OcrService(TencentCloudClient tencent, AppProperties props) {
        this.tencent = tencent;
        this.mock = props.tencent().ocrMock();
    }

    public List<String> recognize(byte[] image) {
        if (mock) {
            return List.of("演示牌", "复合肥 15-15-15", "高效 · 速溶", "净含量：50kg",
                    "肥料登记证号：农肥（2024）准字0001号", "生产企业：演示肥业有限公司", "6901234567892");
        }
        if (!tencent.configured()) {
            throw new BizException("拍照识别暂未开通，请直接输入商品名");
        }
        try {
            JsonNode resp = tencent.call("ocr", "2018-11-19", "GeneralBasicOCR",
                    Map.of("ImageBase64", Base64.getEncoder().encodeToString(image)));
            List<String> lines = new ArrayList<>();
            for (JsonNode d : resp.path("TextDetections")) {
                String t = d.path("DetectedText").asText("").trim();
                if (!t.isEmpty()) lines.add(t);
            }
            return lines;
        } catch (TencentCloudClient.TencentCloudException e) {
            // 图片里没有文字时腾讯云返回 FailedOperation.ImageNoText
            if (e.getCode() != null && e.getCode().contains("NoText")) {
                return List.of();
            }
            log.warn("OCR 失败：{}", e.getMessage());
            throw new BizException("识别失败，请换个角度再拍一张");
        }
    }
}
