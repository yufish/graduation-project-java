package com.hugailei.graduation.corpus.service;

import com.hugailei.graduation.corpus.dto.SentenceDto;

import java.util.List;

/**
 * @author HU Gailei
 * @date 2018/10/1
 * <p>
 * description:
 * </p>
 **/
public interface SentenceService {
    /**
     * 从数据库中查询句子
     *
     * @return
     */
    List<SentenceDto> searchSentenceFromDataBase();
}