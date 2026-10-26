package com.liangmanman.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 搜索同义词：一行是一组意思相同的词，搜其中任何一个都会带上整组 */
@Data
@TableName("lm_search_word")
public class SearchWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private String synonyms;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public List<String> terms() {
        List<String> out = new ArrayList<>();
        if (word != null && !word.isBlank()) out.add(word.trim().toLowerCase());
        if (synonyms != null) {
            for (String t : synonyms.trim().split("\\s+")) {
                if (!t.isBlank() && !out.contains(t.toLowerCase())) out.add(t.toLowerCase());
            }
        }
        return out;
    }
}
