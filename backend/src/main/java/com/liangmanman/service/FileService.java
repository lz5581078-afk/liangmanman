package com.liangmanman.service;

import com.liangmanman.common.BizException;
import com.liangmanman.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 图片上传。当前存本地磁盘；上线前换成腾讯云 COS（接口返回值不变，前端无需改）。
 * 只认真实的图片文件头，不信任文件名和 Content-Type。
 */
@Service
public class FileService {

    private static final Map<String, byte[]> MAGIC = Map.of(
            "jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "webp", new byte[]{0x52, 0x49, 0x46, 0x46});

    private final Path root;
    private final Path privateRoot;
    private final String baseUrl;

    public FileService(AppProperties props) {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        this.privateRoot = Path.of(props.privateDir()).toAbsolutePath().normalize();
        this.baseUrl = props.publicBaseUrl().replaceAll("/+$", "");
    }

    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("没有收到图片");
        }
        byte[] head = new byte[12];
        int n;
        try (InputStream in = file.getInputStream()) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new BizException("图片读取失败，请重试");
        }
        String ext = detect(head, n);
        if (ext == null) {
            throw new BizException("只支持 jpg、png、webp 图片");
        }
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = root.resolve(month);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(name));
        } catch (IOException e) {
            throw new BizException("图片保存失败，请重试");
        }
        return baseUrl + "/files/" + month + "/" + name;
    }

    /** 私密图片（身份证照）：存到不对外公开的目录，返回本地路径 */
    public Path savePrivateImage(MultipartFile file, String name) {
        if (file == null || file.isEmpty()) {
            throw new BizException("没有收到图片");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException("图片读取失败，请重试");
        }
        String ext = detect(bytes, Math.min(bytes.length, 12));
        if (ext == null || "webp".equals(ext)) {
            throw new BizException("请上传 jpg 或 png 照片");
        }
        if (bytes.length > 5 * 1024 * 1024) {
            throw new BizException("照片太大了，请重拍一张（不超过 5MB）");
        }
        Path target = privateRoot.resolve(name + "." + ext);
        try {
            Files.createDirectories(privateRoot);
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new BizException("图片保存失败，请重试");
        }
        return target;
    }

    public byte[] readPrivate(String path) {
        Path p = Path.of(path).toAbsolutePath().normalize();
        if (!p.startsWith(privateRoot)) {
            throw new IllegalArgumentException("非法路径");
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new BizException("证件照已失效，请重新上传");
        }
    }

    public void deletePrivate(String path) {
        try {
            Path p = Path.of(path).toAbsolutePath().normalize();
            if (p.startsWith(privateRoot)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // 删除失败不影响业务
        }
    }

    private String detect(byte[] head, int n) {
        for (Map.Entry<String, byte[]> e : MAGIC.entrySet()) {
            byte[] m = e.getValue();
            if (n < m.length) continue;
            boolean match = true;
            for (int i = 0; i < m.length; i++) {
                if (head[i] != m[i]) {
                    match = false;
                    break;
                }
            }
            if (!match) continue;
            // RIFF 头还要确认是 WEBP
            if (e.getKey().equals("webp") && !(n >= 12 && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P')) {
                continue;
            }
            return e.getKey();
        }
        return null;
    }
}
