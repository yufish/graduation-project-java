package com.hugailei.graduation.corpus.dao;

import com.hugailei.graduation.corpus.domain.StudentSentence;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author HU Gailei
 * @date 2018/10/7
 * <p>
 * description:
 * <p/>
 */
public interface StudentSentenceDao extends JpaRepository<StudentSentence, Long> {
}
