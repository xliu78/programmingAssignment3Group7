package  main;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BasicStats;
import org.apache.lucene.search.similarities.SimilarityBase;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import edu.unh.cs.treccar_v2.Data;

public class TFIDF_anc_apc {

    static private String INDEX_DIRECTORY;
    static private QueryParser parser = null;
    static private Integer docNum = 100;

    private static IndexReader getInedexReader(String path) throws IOException {
        return DirectoryReader.open(FSDirectory.open((new File(path).toPath())));
    }


    public TFIDF_anc_apc(String index){
        this.INDEX_DIRECTORY = index;
    }
    public static SimilarityBase getAncApcSimilarityBase() throws IOException {

        SimilarityBase ancApcSim = new SimilarityBase() {

            @Override
            protected float score(BasicStats stats, float freq, float docLen) {
                return freq;
            }

            @Override
            public String toString() {
                return null;
            }
        };

        return ancApcSim;
    }

    public  static String findHeadling(String str){


        int firstIndex = str.indexOf('\'');

        StringBuffer builder = new StringBuffer();

        String res = "";

        for (int i = firstIndex+1; i < str.length();i++ ){
            if (str.charAt(i) == '\''){
                break;
            }
            res += str.charAt(i);

        }
        return res;
    }


    public static void retrieveAllAncApcResultsByQuryHeading(List<Data.Page> queryList, String path){
        String method = "AncApc";
        List<String> runFileStrList = new ArrayList<String>();
        if (queryList != null) {

            HashMap<String, Double> result_map = null;
            for (Data.Page p : queryList) {

                List<Data.PageSkeleton> tmp = p.getSkeleton();
                String queryStr = null;
                for (Data.PageSkeleton i : tmp){
//                    System.out.println(i.toString());
//                    System.out.println(findHeadling(i.toString()));
                    queryStr = findHeadling(i.toString());

                }

//                String queryStr = p.getPageId();
                result_map = getRankedDocuments(queryStr);
                int i = 0;
                for (Entry<String, Double> entry : result_map.entrySet()) {

                    String runFileString = queryStr + " Q0 " + entry.getKey() + " " + i + " " + entry.getValue()
                            + " Group7-" + method;
                    runFileStrList.add(runFileString);
                    i++;
                }

            }


            //System.out.println("runFileStrlI" + runFileStrList.size() +"  result map : "+result_map.size());
        }


        // Write run file function
        if (runFileStrList.size() > 0) {
            writeStrListToRunFile(runFileStrList, path);
        } else {
            System.out.println("No result for run file.");
        }
    }

    public static void retrieveAllAncApcResults(List<Data.Page> queryList, String path) {
        String method = "AncApc";
        List<String> runFileStrList = new ArrayList<String>();
        if (queryList != null) {

            HashMap<String, Double> result_map = null;
            for (Data.Page p : queryList) {
                String queryStr = p.getPageId();
                result_map = getRankedDocuments(queryStr);
                int i = 0;
                for (Entry<String, Double> entry : result_map.entrySet()) {

                    String runFileString = queryStr + " Q0 " + entry.getKey() + " " + i + " " + entry.getValue()
                            + " Group7-" + method;
                    runFileStrList.add(runFileString);
                    i++;
                }

            }


            System.out.println("runFileStrlI" + runFileStrList.size() +"  result map : "+result_map.size());
        }

        if (runFileStrList.size() > 0) {
            writeStrListToRunFile(runFileStrList, path);
        } else {
            System.out.println("No result for run file.");
        }
    }

    public static HashMap<String, Double> getRankedDocuments(String queryStr) {

        HashMap<Term, Float> qTerm_norm = new HashMap<Term, Float>();
        HashMap<String, Integer> doc_maxTF = new HashMap<String, Integer>();

        HashMap<String, ArrayList<Float>> doc_wtList = new HashMap<String, ArrayList<Float>>();
        HashMap<String, Float> doc_cos = new HashMap<String, Float>();

        HashMap<String, Double> doc_score = new HashMap<String, Double>();

        try {
            IndexReader ir = getInedexReader(INDEX_DIRECTORY);
            IndexSearcher se = new IndexSearcher(ir);
            se.setSimilarity(getAncApcSimilarityBase());
            parser = new QueryParser("parabody", new StandardAnalyzer());

            doc_maxTF = getMapOfDocWithMaxTF(ir);
            //System.out.println("doc_maxTF size: "+doc_maxTF.size());
            qTerm_norm = getNormMapForEachQueryTerm(ir, queryStr);
            //System.out.println("qTerm_norm size: "+qTerm_norm.size());
            // System.out.println(qTerm_norm);
            // Get Cosine value.
            for (Term qTerm : qTerm_norm.keySet()) {
                Query q = parser.parse(qTerm.text());

                TopDocs topDocs = se.search(q, docNum);

                ScoreDoc[] hits = topDocs.scoreDocs;

                for (int i = 0; i < hits.length; i++) {
                    Document doc = se.doc(hits[i].doc);
                    String docId = doc.get("paraid");

                    int tf = (int) hits[i].score;
                    int max_tf = doc_maxTF.get(docId);

                    float a = getAugmentedWt(tf, max_tf);

                    if (doc_wtList.containsKey(docId)) {
                        ArrayList<Float> wt_list = doc_wtList.get(docId);
                        wt_list.add(a);
                        doc_wtList.put(docId, wt_list);
                    } else {
                        ArrayList<Float> wt_list = new ArrayList<Float>();
                        wt_list.add(a);
                        doc_wtList.put(docId, wt_list);
                    }
                }
            }

            for (String docId : doc_wtList.keySet()) {

                doc_cos.put(docId, getCosine(doc_wtList.get(docId)));

            }

            // To ensure the accuracy, doing another search.

            for (Term qTerm : qTerm_norm.keySet()) {
                Query q = parser.parse(qTerm.text());

                TopDocs topDocs = se.search(q, docNum);

                ScoreDoc[] hits = topDocs.scoreDocs;

                float q_norm = qTerm_norm.get(qTerm); // apc

                for (int i = 0; i < hits.length; i++) {
                    Document doc = se.doc(hits[i].doc);
                    String docId = doc.get("paraid");

                    int tf = (int) hits[i].score;
                    int max_tf = doc_maxTF.get(docId);

                    float a = getAugmentedWt(tf, max_tf);
                    float c = doc_cos.get(docId);

                    if (doc_score.containsKey(docId)) {
                        double score = doc_score.get(docId) + ((double) a * c) * q_norm;
                        doc_score.put(docId, score);
                    } else {
                        double score = ((double) a * c) * q_norm;
                        doc_score.put(docId, score);
                    }
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
        }
        //System.out.println("doc_wtList : "+doc_wtList.size() + "  doc_cos : "+doc_cos.size() + " doc_score: "+doc_score.size() );
        return sortByValue(doc_score);
    }

    public static HashMap<String, Integer> getMapOfDocWithMaxTF(IndexReader ir) throws IOException {

        HashMap<String, Integer> result_map = new HashMap<String, Integer>();

        for (int i = 0; i < ir.maxDoc(); i++) {
            Document doc = ir.document(i);
            String docId = doc.get("paraid");

            Terms terms = ir.getTermVector(i, "content");
            if (terms != null) {

                TermsEnum itr = terms.iterator();
                BytesRef term = null;
                PostingsEnum postings = null;

                ArrayList<Integer> tfList = new ArrayList<Integer>();
                while ((term = itr.next()) != null) {
                    try {
                        postings = itr.postings(postings, PostingsEnum.FREQS);
                        postings.nextDoc();
                        int freq = postings.freq();

                        tfList.add(freq);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                int max_tf = Collections.max(tfList);

                result_map.put(docId, max_tf);
            }
        }

        return result_map;
    }

    public static HashMap<Term, Float> getNormMapForEachQueryTerm(IndexReader ir, String queryStr) throws IOException {

        HashMap<Term, Integer> term_tf = new HashMap<Term, Integer>();

        HashMap<Term, Float> term_wt = new HashMap<Term, Float>();

        HashMap<Term, Float> term_norm = new HashMap<Term, Float>();
        int num_N = ir.maxDoc();

        // Get unique terms from query, and the term freq.
        if (queryStr != null) {
            for (String termStr : queryStr.split(" ")) {
                Term term = new Term("parabody", termStr);
                if (term_tf.containsKey(term)) {
                    int freq = term_tf.get(term) + 1;
                    term_tf.put(term, freq);
                } else {
                    term_tf.put(term, 1);
                }
            }
        }

        int max_tf = Collections.max(term_tf.values());

        ArrayList<Float> wt_list = new ArrayList<Float>();

        for (Term term : term_tf.keySet()) {
            float wt;
            int tf = term_tf.get(term);
            int df = (ir.docFreq(term) == 0) ? 1 : ir.docFreq(term);

            float a = getAugmentedWt(tf, max_tf);

            float p = (float) (Math.log10((num_N - df) / df));
            if (p < 0) {
                p = 0;
            }

            wt = (float) a * p;
            wt_list.add(wt);
            term_wt.put(term, wt);
        }

        float c = getCosine(wt_list);

        for (Term term : term_wt.keySet()) {
            float norm = (float) term_wt.get(term) * c;
            term_norm.put(term, norm);
        }
        return term_norm;
    }

    public static float getAugmentedWt(int tf, int max_tf) {

        return (float) (0.5 + (0.5 * tf / max_tf));
    }

    public static float getCosine(List<Float> wt_list) {
        float c = 0;
        float pow_sum = 0;

        for (float f : wt_list) {
            pow_sum = (float) (pow_sum + Math.pow(f, 2));

        }
        c = (float) (1.0 / Math.sqrt(pow_sum));

        return c;
    }

    public static void writeStrListToRunFile(List<String> strList, String path) {

        BufferedWriter bWriter = null;
        FileWriter fWriter = null;

        try {
            fWriter = new FileWriter(path);
            bWriter = new BufferedWriter(fWriter);

            for (String line : strList) {

                bWriter.write(line);
                bWriter.newLine();
            }

            System.out.println("TFIDF_anc_apc writing results to: \t\t" + path);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bWriter != null) {
                    bWriter.close();
                }
                if (fWriter != null) {
                    fWriter.close();
                }
            } catch (IOException ee) {
                ee.printStackTrace();
            }
        }

    }

    private static HashMap<String, Double> sortByValue(Map<String, Double> unsortMap) {

        List<Map.Entry<String, Double>> list = new LinkedList<Map.Entry<String, Double>>(unsortMap.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        HashMap<String, Double> sortedMap = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

}
