import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class SimilarDocs {
    public static void main(String[] args) throws IOException {

        String usage = "java org.apache.lucene.SimilarDocs"
                + " [-index INDEXPATH] [-doc docID] [-field FIELD]  [-top TOP] [-rep REP]\n\n"
                + "The N documents more similar to D are ordered and watched";

        String usageREP = "The ony valid values for rev are 'bin', 'tf' and 'tfxidf'";

        String indexPath = null;
        int docID = -1;
        String field = null;
        int top = -1;
        String rep = null;

        for(int i=0;i<args.length;i++){
            switch (args[i]){
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-doc":
                    docID = Integer.parseInt(args[++i]);
                    break;
                case "-field":
                    field = args[++i];
                    break;
                case "-top":
                    top = Integer.parseInt(args[++i]);
                    break;
                case "-rep":
                    rep = args[++i];
                    break;
                default:
            }
        }


        // Checks that the parameters needed are not null or negative
        if(indexPath==null || docID<0 || field==null || top<0 || rep==null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        // Checks that the value of rev is one of the valid ones
        else if(!rep.equals("bin") && !rep.equals("tf") && !rep.equals("tfxidf")){
            System.err.println("Usage of flag rep: " + usageREP);
            System.exit(1);
        }


        // Take all the terms of the files indexed
        IndexReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        final Terms terms = MultiTerms.getTerms(indexReader, field);

        if(terms!=null){
            List<RealVector> ejemploLista =  termsFrequencies(indexReader,field, terms);

            ejemploLista = getREP(rep, ejemploLista);
            Map<Integer,Double> similarities = getSimilarities(docID, ejemploLista);

            similarities = orderSimilarities(similarities);

            List<Integer> claves = new ArrayList<>(similarities.keySet());

            System.out.println("Similarities of the doc " + docID + " (" + indexReader.document(docID).get("path")  + ")");

            System.out.println("DocID\t\t\tPath");
            for(int i = 0; i<top && i< claves.size() ;i++){

                System.out.println(claves.get(i)+"\t\t\t"+indexReader.document(claves.get(i)).get("path"));
            }

        }

    }

    /**
     *
     * It is in charge of calculating the term frequencies in each document
     *
     * @param indexReader   IndexReader assigned to the index whose path was passed by the user
     * @param field Field we are going to work with
     * @param terms All the terms of all files indexed
     * @return List of RealVector with the terms and their frequencies for each document
     * @throws IOException
     */

    public static List<RealVector> termsFrequencies(IndexReader indexReader, String field, Terms terms) throws IOException {
        TermsEnum termsEnum = terms.iterator();
        List<RealVector> ejemploLista = new ArrayList<RealVector>();
        for (int i = 0; i<indexReader.numDocs();i++){
            ejemploLista.add(new ArrayRealVector(Math.toIntExact(terms.size())));
            ejemploLista.get(i).set(0);
        }

        int termsCount = 0;

        while (termsEnum.next() != null){
            String termString = termsEnum.term().utf8ToString();
            System.out.println(termsCount + " : " + termString);
            PostingsEnum postingsEnum = MultiTerms.getTermPostingsEnum(indexReader, field, new BytesRef(termString));

            //List to know for each term what documents have it
            Map<Integer, Integer> listDocs = new HashMap<>();

            int numDoc;
            while ((numDoc = postingsEnum.nextDoc()) != PostingsEnum.NO_MORE_DOCS){
                listDocs.put(numDoc, postingsEnum.freq());
            }

            for(int i = 0; i<indexReader.numDocs();i++){
                if(listDocs.containsKey(i)){
                    ejemploLista.get(i).setEntry(termsCount,listDocs.get(i));
                }
            }
            termsCount++;
        }
        for (int i = 0; i<indexReader.numDocs();i++){
            System.out.println(ejemploLista.get(i));
        }
        return ejemploLista;
    }


    /**
     *
     * Orders by similarities the different documents
     *
     * @param similarities Pair of values (docID, similarity)
     * @return similarities ordered from major to minor
     */
    public static Map<Integer, Double> orderSimilarities(Map<Integer, Double> similarities) {

        List<Map.Entry<Integer,Double>> list = new LinkedList<Map.Entry<Integer,Double>>(similarities.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>() {
            @Override
            public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        Collections.reverse(list);
        Map<Integer,Double> sortedFrequencies = new LinkedHashMap<Integer,Double>();
        for (Map.Entry<Integer,Double> entry: list){
            sortedFrequencies.put(entry.getKey(), entry.getValue());
        }
        return sortedFrequencies;
    }


    /**
     *
     * Calculates the similarities for each case
     *
     * @param v1 RealVector 1 of the values
     * @param v2 RealVector2 of the values
     * @return Returns it similarity
     */
    private static double getCosineSimilarity(RealVector v1, RealVector v2) {
        return (v1.dotProduct(v2)) / (v1.getNorm() * v2.getNorm());
    }


    /**
     *
     * It calculates all the similarities from a document with the rest
     *
     * @param docID Number of document by Lucene
     * @param ejemploLista List of all the real vectors for each document
     * @return
     */
    public static Map<Integer, Double> getSimilarities(int docID, List<RealVector> ejemploLista) {
        Map<Integer,Double> similarities = new HashMap<>();
        for(int i = 0; i<ejemploLista.size(); i++){
            if(i!=docID){
                similarities.put(i,getCosineSimilarity(ejemploLista.get(i), ejemploLista.get(docID)));
            }
        }
        return similarities;
    }

    /**
     *
     * It is in charged of the type of representation
     *
     * @param rep Representation that can be 'bin', 'tf', or 'tfxidf'
     * @param ejemploLista List of all the real vectors for each document
     * @return
     */
    public static List<RealVector> getREP(String rep, List<RealVector> ejemploLista) {
        if(Objects.equals(rep, "bin")){
            ejemploLista = repBin(ejemploLista);
        } else if(Objects.equals(rep,"tf")){
        } else if(Objects.equals(rep, "tfxidf")){
            ejemploLista = repTfxidf(ejemploLista);
        }
        return ejemploLista;
    }

    /**
     *
     * It is in charged of the tfxidf representation
     *
     * @param ejemploLista List of all the real vectors for each document
     * @return
     */
    private static List<RealVector> repTfxidf(List<RealVector> ejemploLista) {
        int numDocs = ejemploLista.size();
        List<Integer> numDocsAppearance = new ArrayList<>(ejemploLista.get(0).getDimension());
        for(int i = 0; i<ejemploLista.get(0).getDimension();i++){
            int num = 0;
            for(int j = 0; j< ejemploLista.size();j++){
                if(ejemploLista.get(j).getEntry(i)>0){
                    num++;
                }
            }
            numDocsAppearance.add(i,num);
        }
        RealVector idf = new ArrayRealVector(ejemploLista.get(0).getDimension());
        for(int i = 0; i<ejemploLista.get(0).getDimension(); i++){
            idf.setEntry(i, Math.log10((double)numDocs/(double)numDocsAppearance.get(i)));
        }

        for(int i = 0; i<ejemploLista.size();i++){
            for(int j = 0; j<ejemploLista.get(i).getDimension();j++){
                ejemploLista.get(i).setEntry(j, ejemploLista.get(i).getEntry(j)*idf.getEntry(j));
            }
        }

        System.out.println();
        for (int i = 0; i< ejemploLista.size();i++){
            System.out.println(ejemploLista.get(i));
        }
        return ejemploLista;

    }


    /**
     * It is in charge of the bin representation
     *
     * @param ejemploLista List of all the real vectors for each document
     * @return
     */
    private static List<RealVector> repBin(List<RealVector> ejemploLista) {
        for(int i = 0; i<ejemploLista.size();i++){
            for(int j = 0; j<ejemploLista.get(i).getDimension();j++){
                if(ejemploLista.get(i).getEntry(j) > 0){
                    ejemploLista.get(i).setEntry(j,1);
                }
            }
        }
        System.out.println();
        for (int i = 0; i< ejemploLista.size();i++){
            System.out.println(ejemploLista.get(i));
        }
        return ejemploLista;
    }
}