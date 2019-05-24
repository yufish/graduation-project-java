package com.hugailei.graduation.corpus.service;

import java.util.List;
import java.util.Set;

/**
 * @author HU Gailei
 * @date 2019/1/10
 * <p>
 * description:
 * </p>
 **/
public interface RankWordService {
    /**
     * 查询大于等于指定等级的所有词汇
     *
     * @param rankNum
     * @return
     */
    Set<String> findMoreDifficultRankWord(int rankNum);

    /**
     * 查询指定等级的词汇
     *
     * @param rankNum
     * @return
     */
    List<String> findWordByRankNum(int rankNum);
}