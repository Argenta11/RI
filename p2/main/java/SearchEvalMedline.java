import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;


import java.io.*;
import java.nio.file.Paths;
import java.util.*;

public class SearchEvalMedline {

    //Lists for the different metrics (P@n, Recall@n, AP@n)
    static List<Double> pnList = new ArrayList<>();
    static List<Double> recallList = new ArrayList<>();
    static List<Double> apnList = new ArrayList<>();

    //It contains all the strings that we want to show through the console or with the file
    static StringBuilder console = new StringBuilder();


    public static void main(String[] args) throws IOException, ParseException {


        //Message that we show in the case there is any error
        String usage = "java org.apache.lucene.SearchEvalMedline"
                + " [-search jm LAMBDA| tfidf] [-indexin INDEX_PATH] [-cut CUT] [-top TOP] [-queries QUERIES]\n\n"
                + "It searchs and evaluates different queries in the index"
                + "\n We can search by jm or tfidf"
                + "We need also a value for the top documents we want to obtain after the evaluation"
                + "Finally we also need the queries to work with. It can be all|int|int1-int2";

        //Message that we show in the case that the search is jm and there is not any lambda value.
        String usageSearchJM = "java.org.apache.lucene.SearchEvalMedline"
                + " The parameter -search in the case is jm, we need also the value of lambda.\n";


        String search = null;           //Type of search
        float lambda = -1;              //Value for lambda in the case is jm
        String indexin=null;            //Index that we are working with
        int cut = -1;                   //Number of docs we are going to use for the different metrics
        int top = -1;                   //Number of top docs we are going to show
        String queries = null;          //Queries that we are working with
        boolean allQueries = false;     //Boolean that means if we work with all or not all the queries
        List<Integer> nQueries = new ArrayList<>(); //Number of queries we are going to work wth in the case we are not working with all of them

        //Reads all the arguments introduced by the user
        for (int i = 0; i< args.length;i++){
            switch (args[i]){
                case "-indexin":
                    indexin = args[++i];
                    break;
                case "-cut":
                    cut = Integer.parseInt(args[++i]);
                    break;
                case "-top":
                    top = Integer.parseInt(args[++i]);
                    break;
                case "-queries":
                    queries = args[++i];
                    //If we are working with all the queries, the boolean value changes to be true
                    if(Objects.equals(queries, "all")){
                        allQueries = true;
                    }
                    break;
                case "-search":
                    search = args[++i];
                    if (Objects.equals(search, "jm")){
                        lambda = Float.parseFloat(args[++i]);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        //Make all the checks of the arguments
        if (!allQueries){
            nQueries = parsearNumberQueries(queries);
         }

        if (indexin == null || cut == -1 || top == -1 || queries == null || search == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if (search.equals("jm") && (lambda < 0 || lambda > 1)){
            System.err.println("Usage of search: " + usageSearchJM);
            System.exit(1);
        }

        //IndexReader that reads the index created
        IndexReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexin)));
        //It allows searching over the indexReader
        IndexSearcher searcher = new IndexSearcher(indexReader);

        //Similarity we are going to work with
        Similarity similarity = null;
        if(search.equals("jm")){
            similarity = new LMJelinekMercerSimilarity(lambda);
        } else if (search.equals("tfidf")){
            similarity = new ClassicSimilarity();
        } else {
            System.err.println("Bad usage of the parameter search");
            System.exit(1);
        }
        searcher.setSimilarity(similarity);

        //Creates an analyzer
        Analyzer analyzer = new StandardAnalyzer();
        //We are going to work with the contents of the docs
        QueryParser queryParser = new QueryParser("Contents", analyzer);


        //Work with file and the different metrics
        parsearArchivo(indexReader, searcher, queryParser, top, cut, allQueries, nQueries);

        //Calculates the average of the metrics
        mediaMetricas();
        //Creates the file .csv
        crearcsv(search,String.valueOf(cut),queries);
        //Shows the top docs and the values of the metrics through the console
        imprimir();
        //Creates the file .txt
        creartxt(search, lambda,queries,top);

    }

    /**
     *
     * @param search Type of search
     * @param lambda Value of lambda
     * @param queries Queries we are working with
     * @param top Number of top docs we want to show
     *
     * Creates the file .txt with the top docs for the queries,
     *         and also the value of the metrics and its average
     */

    private static void creartxt(String search, float lambda, String queries, int top) {
        //There can be two types of file name depending on the type of search
        String rutaTxt;
        if(Objects.equals(search, "jm")){
            rutaTxt = "medline.jm." + top + ".hits.lambda." + lambda + ".q" + queries + ".txt";
        } else {
            rutaTxt = "medline.tfidf." + top + ".hits.q" + queries + ".txt";
        }

        //FileWriter to create the file
        FileWriter fileWriter = null;
        //It allows writing in the file
        PrintWriter printWriter;

        try{
            //Init fileWriter and printWriter
            fileWriter = new FileWriter(rutaTxt);
            printWriter = new PrintWriter(fileWriter);

            //Writes the content of the file that is stored in the StringBuilder console
            printWriter.println(console.toString());

        } catch (Exception e){
            e.printStackTrace();
        } finally {
            try{
                //Closes the FileWriter
                if (fileWriter != null){
                    fileWriter.close();
                }
            }
            catch(Exception e2){
                e2.printStackTrace();
            }
        }



    }

    /**
     * Allows showing in the console the top docs for each query
     */
    private static void imprimir() {
        System.out.println(console.toString());
    }

    /**
     * Calculates, for all the queries we are working with, the average of the three metrics
     */
    private static void mediaMetricas() {

        //Init the counters
        double sumPn = 0;
        double sumRecall = 0;
        double sumAPn = 0;

        //Update the counters
        for (int i = 0; i<pnList.size();i++){
            sumPn += pnList.get(i);
            sumRecall += recallList.get(i);
            sumAPn += apnList.get(i);
        }

        //Calculates the metrics and store the result in the StringBuilder
        console.append("P@n (Global): ").append(sumPn / pnList.size()).append("\n");
        console.append("Recall@n (Global): ").append(sumRecall / recallList.size()).append("\n");
        console.append("MAP@n (Global): ").append(sumAPn / apnList.size()).append("\n");

    }

    /**
     *
     * @param queries Value of the argument queries
     * @return List of the value or values that limit the start and end idQuery to work with
     *
     * Allows to obtain the interval ids of the queries
     */

    private static List<Integer> parsearNumberQueries(String queries) {
        //Divide the argument by the "-". In the case there are two
        String[] split = queries.split("-");
        List<Integer> nQueries = new ArrayList<>();
        for (int i = 0; i<split.length;i++){
            nQueries.add(Integer.parseInt(split[i]));
        }
        return nQueries;
    }

    /**
     *
     * @param search Value of the argument search
     * @param cut Number of docs we are using to calculate the metrics
     * @param queries Queries we are working with
     * @throws IOException
     *
     * Creates the file .csv showing the query and the values of the different metrics.
     * We also show the average of all of them
     *
     */
    private static void crearcsv(String search, String cut, String queries) throws IOException {
        String nombre = "medline."+search+"."+cut+".cut"+"q"+queries+".csv";
        FileWriter csvWriter = new FileWriter(nombre);
        int inicio=1;
        if(!Objects.equals(queries, "all")){
            String[] a = queries.split("-");
            inicio = Integer.parseInt(a[0]);
        }
        //Creates the first part of the File
        String header = "Query,\tP@n,\tRecall@n,\tAP@n\n";
        csvWriter.append(header);
        for(int i=0;i<pnList.size();i++){
            csvWriter.append(String.valueOf(inicio+i));
            csvWriter.append(",\t");
            csvWriter.append(String.valueOf(pnList.get(i)));
            csvWriter.append(",\t");
            csvWriter.append(String.valueOf(recallList.get(i)));
            csvWriter.append(",\t");
            csvWriter.append(String.valueOf(apnList.get(i)));
            csvWriter.append("\n");
        }
        double sumPn=0;
        double sumRecall=0;
        double sumAPn=0;
        for (int i = 0; i<pnList.size();i++){
            sumPn += pnList.get(i);
            sumRecall += recallList.get(i);
            sumAPn += apnList.get(i);
        }
        //Creates the average part of the .csv
        csvWriter.append("Promedio,\t");
        csvWriter.append(String.valueOf(sumPn/pnList.size()));
        csvWriter.append(",\t");
        csvWriter.append(String.valueOf(sumRecall/recallList.size()));
        csvWriter.append(",\t");
        csvWriter.append(String.valueOf(sumAPn/apnList.size()));
        csvWriter.append("\n");
        csvWriter.flush();
        csvWriter.close();
    }

    /**
     *
     * @param linea Line of the type '.I 29'
     * @return the id of the query
     *
     * Obtains the value of the query id from the line
     *
     */

    private static String obtenerId(String linea){
        StringBuilder Id= new StringBuilder();
        for(int a=3; a<linea.length();a++){
            Id.append(linea.charAt(a));
        }
        return Id.toString();
    }

    /**
     *
     * @param indexReader refers to the index
     * @param searcher type of IndexSearcher we are using
     * @param queryParser parser of the query to work with the field contents
     * @param top number of top docs we want to show
     * @param cut number of docs we are going to use to calculate the metrics
     * @param allQueries if we are using all the queries or not
     * @param nQueries number of queries we are using
     * @throws IOException
     * @throws ParseException
     *
     * It parses the field, and calls the function queryExe to execute all the queries
     *
     */
    private static void parsearArchivo(IndexReader indexReader, IndexSearcher searcher, QueryParser queryParser,
                                       int top, int cut, boolean allQueries, List<Integer> nQueries) throws IOException, ParseException {

        //Allows knowing if we are using intervals or not
        boolean intervalo;
        if (allQueries){
            intervalo = false;
        } else intervalo = nQueries.size() > 1;


        //Open the file we are going to parse
        File doc = new File("src/med/MED.QRY");
        //Buffer to read the file
        BufferedReader reader = new BufferedReader(new FileReader(doc));
        String linea;
        String queryID="";
        StringBuilder contents= new StringBuilder();
        while ((linea = reader.readLine()) != null){
            if(!linea.equals("")){
                if(linea.charAt(0)=='.' && linea.charAt(1)=='I'){
                    if(!queryID.equals("")) {

                        //We look if the queryId is in the interval of values we want
                        if(allQueries || (!intervalo && Integer.parseInt(queryID) == nQueries.get(0)) ||
                        (intervalo && Integer.parseInt(queryID)>=nQueries.get(0) && Integer.parseInt(queryID)<=nQueries.get(1))) {
                            queryExe(indexReader, searcher, queryParser, contents.toString(), queryID, top, cut);
                        }
                    }
                    contents = new StringBuilder();
                    queryID = obtenerId(linea);
                }else if(linea.charAt(0)=='.' && linea.charAt(1)=='W'){

                }else{
                    contents.append(linea);
                    contents.append("\n");
                }
            }
        }
        //We look if the queryId is in the interval of values we want
        if(allQueries || (!intervalo && Integer.parseInt(queryID) == nQueries.get(0)) ||
                (intervalo && Integer.parseInt(queryID)>=nQueries.get(0) && Integer.parseInt(queryID)<=nQueries.get(1))) {
            queryExe(indexReader, searcher, queryParser, contents.toString(), queryID, top, cut);
        }
    }

    /**
     *
     * @param indexReader indexReader refers to the index
     * @param searcher type of IndexSearcher we are using
     * @param queryParser parser of the query to work with the field contents
     * @param queryContents contents of the query
     * @param queryID id of the query we are working with
     * @param top number of top docs we want to show
     * @param cut number of docs we are using to calculate the metrics
     * @throws ParseException
     * @throws IOException
     *
     * Executes the query and obtains the top docs we were looking for
     *
     */

    private static void queryExe(IndexReader indexReader, IndexSearcher searcher, QueryParser queryParser,
                                 String queryContents, String queryID, int top, int cut) throws ParseException, IOException {

        console.append("QUERY ").append(queryID).append(":").append("\n");
        console.append(queryContents).append("\n");

        //The content must be in lowercase
        Query query = queryParser.parse(QueryParser.escape(queryContents)); //El contenido en minúscula

        //Obtain the relevance docs for each query
        Map<Integer, List<Integer>> relevances = parsearRelevancias();

        //In order, we only calculate once the topDocs we obtain the maximum of both values
        int max = Math.max(top,cut);
        int relevants = 0;

        double sumPrecision = 0;

        //Obtain the topDocs for the query order by the score
        TopDocs topDocs = searcher.search(query,max);

        for (int i = 0; i<topDocs.scoreDocs.length; i++){

            //If we want that doc to be shown, we append the values of the indexed field and its score
            //If the value of i is not bigger than the top's one
            if(i<top){
                console.append("DocIDMedline: ").append(indexReader.document(topDocs.scoreDocs[i].doc).get("DocIDMedline")).append("\n");
                console.append("Score: ").append(topDocs.scoreDocs[i].score).append("\n").append("\n");
                console.append("Contents: ").append(indexReader.document(topDocs.scoreDocs[i].doc).get("Contents")).append("\n");

            }
            //Searches if it's relevant the doc for the query
            if (isRelevant(relevances, Integer.parseInt(indexReader.document(topDocs.scoreDocs[i].doc).get("DocIDMedline")), Integer.parseInt(queryID))){
                if(i<cut){
                    relevants+=1;
                    //Calculates the precision for each relevant doc
                    sumPrecision += (double) relevants/(i+1);
                }
                if(i<top){
                    console.append("Es relevante" + "\n" + "\n");
                }
            } else {
                if(i<top){
                    console.append("No es relevante" + "\n" + "\n");
                }
            }
        }


        //Calculates the metrics for each query
        double pnValue = pn(cut,relevants);
        double recallValue = recall(relevances.get(Integer.parseInt(queryID)).size(), relevants);
        double apnValue = apn(sumPrecision, relevances.get(Integer.parseInt(queryID)).size());

        pnList.add(pnValue);
        recallList.add(recallValue);
        apnList.add(apnValue);

        console.append("P@n: ").append(pnValue).append("\n");
        console.append("Recall@n: ").append(recallValue).append("\n");
        console.append("AP@n: ").append(apnValue).append("\n");

        console.append("-------------------------------").append("\n");


    }

    /**
     *
     * @param cut Number of docs we are using to calculate the metrics
     * @param relevants Number of relevant docs in the first cut positions
     * @return the value of the metric
     *
     * Calculates the value of the P@n metric
     *
     */
    private static double pn(int cut, int relevants){
        return (double) relevants/cut;
    }

    /**
     *
     * @param totalRelevants number of relevant docs for the query
     * @param relevants number of relevant docs for the query in the first cut positions
     * @return
     *
     * Calculates the value of the Recall@n metric
     *
     **/
    private static double recall(int totalRelevants, int relevants){
        return (double) relevants/totalRelevants;
    }

    /**
     *
     * @param sumPrecision Addition of the precisions for the relevant docs in the first cut positions
     * @param size Number of relevant docs for the query
     * @return
     *
     * Calculates the value of the AP@n metric
     *
     */
    private static double apn(double sumPrecision, int size) {
        return sumPrecision /size;
    }


    /**
     *
     * @param relevances Relevance docs for each query
     * @param docID DocID to search for
     * @param queryID QueryID to look for
     * @return
     *
     * Answers if the doc is relevant for that query
     *
     */

    private static boolean isRelevant(Map<Integer,List<Integer>> relevances, int docID, int queryID){

        if(relevances!= null){
            for (int i = 0; i<relevances.get(queryID).size(); i++){
                if(relevances.get(queryID).get(i) < docID){
                } else if(docID == relevances.get(queryID).get(i)){
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;

    }

    /**
     *
     * @return Map with the queryID, and a list of the relevant docs for that query
     * @throws IOException
     * @throws ParseException
     *
     * Obtains the relevant docs for each query from the File "MED.REL"
     *
     */



    private static Map<Integer, List<Integer>> parsearRelevancias() throws IOException, ParseException {


        File doc = new File("src/med/MED.REL");
        BufferedReader reader = new BufferedReader(new FileReader(doc));
        String linea;
        Map<Integer,List<Integer>> relevances = new HashMap<>();
        int queryID = -1;

        while ((linea = reader.readLine()) != null){
            if(!linea.equals("")){
                String[] split =  linea.split(" ");
                if(Integer.parseInt(split[0]) != queryID){
                    queryID = Integer.parseInt(split[0]);
                    relevances.put(queryID, new ArrayList<>());
                    relevances.get(queryID).add(Integer.parseInt(split[2]));
                } else {
                    relevances.get(queryID).add(Integer.parseInt(split[2]));
                }
            }
        }
        return relevances;
    }
}
