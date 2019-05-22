package nl.inl.blacklab.server.requesthandlers;

import com.hugailei.graduation.corpus.constants.CorpusConstant;
import com.hugailei.graduation.corpus.service.StudentRankWordService;
import com.hugailei.graduation.corpus.util.SentenceRankUtil;
import lombok.extern.slf4j.Slf4j;
import nl.inl.blacklab.search.*;
import nl.inl.blacklab.search.grouping.HitGroup;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitPropValue;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.*;
import nl.inl.blacklab.server.search.BlsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * @author HU Gailei
 * @date 2018/10/1
 * <p>
 * description: 对语料库中的句子进行查询
 * </p>
 **/
@Slf4j
@Component
public class SentenceRequestHandler extends RequestHandler {

    @Autowired
    private StudentRankWordService studentRankWordService;

    public SentenceRequestHandler(BlackLabServer servlet,
                                  HttpServletRequest request,
                                  User user,
                                  String indexName,
                                  String urlResource,
                                  String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        if (BlsConfig.traceRequestHandling) {
            log.info("SentenceRequestHandler | handle start");
        }
        Job search = null;
        JobHitsGrouped searchGrouped = null;
        JobHitsWindow searchWindow = null;
        JobHitsTotal total = null;

        // Do we want to view a single group after grouping?
        String groupBy = searchParam.getString("group");
        if (groupBy == null) {
            groupBy = "";
        }
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup == null) {
            viewGroup = "";
        }
        try {
            HitsWindow window;
            HitGroup group = null;
            boolean block = isBlockingOperation();
            if (groupBy.length() > 0 && viewGroup.length() > 0) {

                // TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)

                // Yes. Group, then show hits from the specified group
                searchGrouped = (JobHitsGrouped) searchMan.search(user, searchParam.hitsGrouped(), block);
                search = searchGrouped;
                search.incrRef();

                // If search is not done yet, indicate this to the user
                if (!search.finished()) {
                    return Response.busy(ds, servlet);
                }

                // Search is done; construct the results object
                HitGroups groups = searchGrouped.getGroups();

                HitPropValue viewGroupVal = null;
                viewGroupVal = HitPropValue.deserialize(searchGrouped.getHits(), viewGroup);
                if (viewGroupVal == null) {
                    return Response.badRequest(ds, "ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
                }

                group = groups.getGroup(viewGroupVal);
                if (group == null) {
                    return Response.badRequest(ds, "GROUP_NOT_FOUND", "Group not found: " + viewGroup);
                } else {
                    group.getHits().settings().setContextSize( 500 );
                }

                String sortBy = searchParam.getString("sort");
                HitProperty sortProp = sortBy != null && sortBy.length() > 0 ? HitProperty.deserialize(group.getHits(), sortBy) : null;
                Hits hitsSorted;
                if (sortProp != null) {
                    hitsSorted = group.getHits().sortedBy(sortProp);
                } else {
                    hitsSorted = group.getHits();
                }

                int first = searchParam.getInteger("first");
                if (first < 0) {
                    first = 0;
                }
                int number = searchParam.getInteger("number");
                if (number < 0 || number > searchMan.config().maxPageSize()) {
                    number = searchMan.config().defaultPageSize();
                }
                if (!hitsSorted.sizeAtLeast(first)) {
                    return Response.badRequest(ds, "HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");
                }
                window = hitsSorted.window(first, number);

            }
            else {
                searchWindow = (JobHitsWindow) searchMan.search(user, searchParam.hitsWindow(), block);
                search = searchWindow;
                search.incrRef();
                total = (JobHitsTotal) searchMan.search(user, searchParam.hitsTotal(), searchParam.getBoolean("waitfortotal"));

                if (!search.finished()) {
                    return Response.busy(ds, servlet);
                }

                window = searchWindow.getWindow();
            }

            if (searchParam.getString("calc").equals("colloc")) {
                dataStreamCollocations(ds, window.getOriginalHits());
                return HTTP_OK;
            }

            Searcher searcher = search.getSearcher();
            Hits hits = searchWindow != null ? hits = searchWindow.getWindow().getOriginalHits() : group.getHits();

            double totalTime;
            if (total != null) {
                totalTime = total.threwException() ? -1 : total.userWaitTime();
            } else {
                totalTime = searchGrouped.threwException() ? -1 : searchGrouped.userWaitTime();
            }

            boolean countFailed = totalTime < 0;
            int totalHits = -1;
            if (hits != null) {
                // We have a hits object we can query for this information
                totalHits =  countFailed ? -1 : hits.countSoFarHitsCounted();
            }
            int pageNo = (searchParam.getInteger("first")/searchParam.getInteger("number")) + 1 ;
            int pageSize =  searchParam.getInteger("number") < 0 || searchParam.getInteger("number") > searchMan.config().maxPageSize() ? searchMan.config().defaultPageSize() : searchParam.getInteger("number");
            double totalPages = Math.ceil( (double)totalHits/ (double)pageSize );

            ds.startItem("result").startMap();
            ds.entry("status", CorpusConstant.SUCCESS);
            ds.entry("code", CorpusConstant.SUCCESS_CODE);
            ds.entry("msg", "");
            ds.entry("error", "");
            ds.startDataEntry("data");
            ds.startEntry(false,"pageNumber").value(pageNo).endEntry();
            ds.entry("pageSize", pageSize);
            ds.entry("totalPages", totalPages);
            ds.entry("totalElements", totalHits);

            ds.startEntry("page").startList();
            Set<String> difficultWordSet = new HashSet<>();
            Set<String> rankWordSet = new HashSet<>();
            Set<String> studentRankWordSet = new HashSet<>();
            // 获取等级
            int rankNum = 0;
            if (request.getParameter("rank_num") != null) {
                // 获取等级
                rankNum = Integer.valueOf(request.getParameter("rank_num"));
                // 获取高难度词汇
                difficultWordSet = CorpusConstant.RANK_NUM_TO_DIFFICULT_WORD_SET.get(rankNum);
                // 获取当前等级下的词汇
                rankWordSet = CorpusConstant.RANK_NUM_TO_WORD_SET.get(rankNum);
                // 获取学生已掌握的等级词汇
                Long studentId = (Long)request.getSession().getAttribute("student_id");
                studentRankWordSet = studentRankWordService.getStudentRankWord(studentId, rankNum);
            }

            // 得到句子列表，对句子进行难度排序
            List<String> sentenceList = new ArrayList<>();
            for (Hit hit : window) {
                String sentence = "";
                for (String word : window.getKwic(hit).getMatch("word")) {
                    sentence += word + " ";
                }
                sentenceList.add(sentence.trim());
            }
            sentenceList = SentenceRankUtil.orderSentenceByRankNumAsc(sentenceList);
            Map<String, String> sentence2LabeledSentence = new HashMap<>();
            for (Hit hit : window) {
                String labeledSentence = "";
                // Add KWIC info
                Kwic c = window.getKwic(hit);
                List<String> sentenceWordList = new ArrayList<>();
                sentenceWordList.addAll(c.getMatch( "word" ));
                if (request.getParameter("rank_num") != null) {
                    List<String> sentenceLemmaList = c.getMatch("lemma");
                    for (int i = 0; i < sentenceLemmaList.size(); i++) {
                        if (difficultWordSet.contains(sentenceLemmaList.get(i)) && !studentRankWordSet.contains(sentenceLemmaList.get(i))) {
                            String temp = sentenceWordList.get(i);
                            sentenceWordList.set(i, CorpusConstant.DIFFICULT_WORD_STRENGTHEN_OPEN_LABEL + temp + CorpusConstant.DIFFICULT_WORD_STRENGTHEN_CLOSE_LABEL);
                        } else if (rankWordSet.contains(sentenceLemmaList.get(i)) && !studentRankWordSet.contains(sentenceLemmaList.get(i))) {
                            String temp = sentenceWordList.get(i);
                            sentenceWordList.set(i, CorpusConstant.RANK_WORD_STRENGTHEN_OPEN_LABEL + temp + CorpusConstant.RANK_WORD_STRENGTHEN_CLOSE_LABEL);
                        }
                    }
                }
                for (String word : sentenceWordList) {
                    labeledSentence = labeledSentence + word + " ";
                }
                labeledSentence = labeledSentence.trim();
                String sentence = "";
                for (String word : window.getKwic(hit).getMatch("word")) {
                    sentence += word + " ";
                }
                sentence2LabeledSentence.put(sentence.trim(), labeledSentence);
            }

            int id = 0;
            for (String sentence : sentenceList) {
                ds.startItem("hit").startMap();
                String labeledSentence = sentence2LabeledSentence.get(sentence);
                ds.entry("id", id ++);
                String finalSentence = labeledSentence.replaceAll("''","\"")
                        .replaceAll("``", "\"")
                        .replaceAll("-LLB-", "[")
                        .replaceAll("-LRB-", "]");
                ds.entry("sentence", finalSentence);
                ds.entry("corpus", searchParam.getIndexName());
                ds.endMap().endItem();
            }
            ds.endList().endEntry();
            ds.endDataEntry("data");
            ds.endMap().endItem();
            if (BlsConfig.traceRequestHandling) {
                log.info("SentenceRequestHandler | handle end");
            }
            return HTTP_OK;
        } catch (Exception e) {
            log.error("SentenceRequestHandler | error: {}", e);
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        } finally {
            if (search != null) {
                search.decrRef();
            }
            if (searchWindow != null) {
                searchWindow.decrRef();
            }
            if (searchGrouped != null) {
                searchGrouped.decrRef();
            }
            if (total != null) {
                total.decrRef();
            }
        }
    }

    private void dataStreamCollocations(DataStream ds, Hits originalHits) {
        originalHits.settings().setContextSize(searchParam.getInteger("wordsaroundhit"));
        ds.startMap().startEntry("tokenFrequencies").startMap();
        TermFrequencyList tfl = originalHits.getCollocations();
        tfl.sort();
        for (TermFrequency tf: tfl) {
            ds.attrEntry("token", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry().endMap();
    }

}
