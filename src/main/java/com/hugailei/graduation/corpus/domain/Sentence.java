package com.hugailei.graduation.corpus.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author HU Gailei
 * @date 2018/9/25
 * <p>
 * description: 句子信息
 * </p>
 **/
@Entity
@Table(name = "tb_sentence")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Sentence implements Serializable {

    //@GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @Column(name = "sentence")
    private String sentence;

    @Column(name = "text_id")
    private Long textId;

    @Column(name = "word_count")
    private Integer wordCount;
}
