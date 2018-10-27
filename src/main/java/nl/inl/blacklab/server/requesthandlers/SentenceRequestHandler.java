package nl.inl.blacklab.server.requesthandlers;

import com.hugailei.graduation.corpus.constants.CorpusConstant;
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
import org.apache.lucene.document.Document;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 例句查询处理类
 *
 * @author HU Gailei
 */
@Slf4j
public class SentenceRequestHandler extends RequestHandler {
    public SentenceRequestHandler(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
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
            int totalPages = (int) Math.ceil( (double)totalHits/ (double)pageSize );

            ds.startItem("result").startMap();
            ds.entry("status", CorpusConstant.SUCCESS);
            ds.entry("code", CorpusConstant.SUCCESS_CODE);
            ds.entry("msg", "");
            ds.entry("error", "");
            ds.startDataEntry("data");
            ds.startEntry(false,"pageNo").value(pageNo).endEntry();
            ds.entry("pageSize", pageSize);
            ds.entry("orderBy", null);
            ds.entry("orderDir", null);
            ds.entry("nextPage", pageNo>=totalPages?totalPages:pageNo+1);
            ds.entry("prePage", pageNo<=1?1:pageNo-1);
            ds.entry("totalPages", totalPages);
            ds.entry("totalItems", totalHits);
            ds.entry("startIndexNo", searchParam.getInteger("first"));
            ds.entry("hasNextPage", pageNo>=totalPages?false:true);
            ds.entry("hasPrePage", pageNo<=1?false:true);

            ds.startEntry("page").startList();
            Map<Integer, String> pids = new HashMap<>();
            for (Hit hit: window) {
                ds.startItem("hit").startMap();

                // Find pid
                String pid = pids.get(hit.doc);
                if (pid == null) {
                    Document document = searcher.document(hit.doc);
                    pid = getDocumentPid(searcher, hit.doc, document);
                    pids.put(hit.doc, pid);
                }

                boolean useOrigContent = searchParam.getString("usecontent").equals("orig");

                ds.entry("id", pid+hit.start+hit.end);
                if (useOrigContent) {
                    // Add concordance from original XML
                    Concordance c = window.getConcordance(hit);
                    ds.startEntry("sentence").plain(c.match()).endEntry();
                }
                else {
                    String left = "", match = "", right = "";
                    // Add KWIC info
                    if(window!=null) {
                        Kwic c = window.getKwic(hit);
                        List<String> matchWordsList = c.getMatch( "word" );
                        for(String word : matchWordsList) {
                            match = match + word + " ";
                        }

                    }
                    ds.entry("sentence", match);

                }
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
