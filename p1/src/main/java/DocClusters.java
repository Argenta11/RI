import org.apache.commons.math3.linear.RealVector;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocClusters {

    /**
     *
     * @param ejemploLista  List of Real Vectors that correspond to each of the documents
     * @param indexReader   IndexReader assigned to the index whose path was passed by the user
     * @return It returns the dataset that correspond to the .csv that was created
     * @throws IOException
     */
    private static DataSet obtener_csv(java.util.List<RealVector> ejemploLista, IndexReader indexReader) throws IOException {
        try{

            // Variable initialization
            FileWriter csvWriter = new FileWriter("sample.csv");
            int max = ejemploLista.get(0).getDimension();

            // Writing the header
            for (int i=0; i<max-1;i++){
                String a = "Termino" + i;
                csvWriter.append(a);
                csvWriter.append(",\t");
            }
            String a = "Termino" + String.valueOf(max-1);
            csvWriter.append(a);
            csvWriter.append("\n");

            // For each document a line is written with the data
            for (int i = 0; i<indexReader.numDocs();i++){
                RealVector vector = ejemploLista.get(i);
                List<String> datos_aux = new ArrayList<String>();
                for (int j=0; j<max;j++){
                    datos_aux.add(String.valueOf(vector.getEntry(j)));
                }
                csvWriter.append(String.join(",\t", datos_aux));
                csvWriter.append("\n");
            }

            csvWriter.flush();
            csvWriter.close();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new DataSet("sample.csv");
    }

    /**
     * This method is responsible for starting the execution of the program.
     * It is the project's main method.
     * @param args Array with parameters added by the user
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // Variable initialization
        String usage = "java org.apache.lucene.DocClusters"
                + " [-index INDEXPATH] [-field CAMPO] [-doc D] [-top N] [-rep REP] [-k K]\n\n"
                + "The N documents more similar to D are ordered and watched"
                + "The k-means algorithm produces K clusters with the N terms and are visualized\n";
        String indexPath = null;
        String campo = null;
        String d = null;
        int n = -1;
        int k = -1;
        String rep = null;

        // Loop in which we will obtain the parameters given by the user
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    campo = args[++i];
                    break;
                case "-doc":
                    d = args[++i];
                    break;
                case "-top":
                    n = Integer.parseInt(args[++i]);
                    break;
                case "-rep":
                    rep = args[++i];
                    break;
                case "-k":
                    k = Integer.parseInt(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        // Check that mandatory parameters exist
        if (indexPath == null || campo == null || d == null || n < 0 || rep == null || k < 0) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        if(!rep.equals("bin") && !rep.equals("tf") && !rep.equals("tfxidf")){
            System.err.println("rep value is not valid");
            System.exit(1);
        }

        // We create an indexReader and get the terms of all the documents through it
        IndexReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        final Terms terms = MultiTerms.getTerms(indexReader, campo);

        //Whenever terms exist, we perform the sorting process
        if(terms!=null){

            // We call the method that stores values term by term
            java.util.List<RealVector> ejemploLista =  SimilarDocs.termsFrequencies(indexReader,campo, terms);

            // Printing the resulting list
            for (int i = 0; i<indexReader.numDocs();i++){
                System.out.println(ejemploLista.get(i));
            }

            // We call the corresponding functions
            ejemploLista = SimilarDocs.getREP(rep, ejemploLista);
            Map<Integer,Double> similarities = SimilarDocs.getSimilarities(Integer.parseInt(d), ejemploLista);
            similarities = SimilarDocs.orderSimilarities(similarities);

            // Printing results
            List<Integer> claves = new ArrayList<>(similarities.keySet());
            System.out.println("Similarities of the doc " + d + " (" + indexReader.document(Integer.parseInt(d)).get("path")  + ")");
            System.out.println("DocID\t\t\tPath");

            for(int i = 0; i<n && i< claves.size() ;i++){
                System.out.println(claves.get(i)+"\t\t\t"+indexReader.document(claves.get(i)).get("path"));
            }

            // We obtein dataset to apply tbe Kmeans method
            DataSet data =obtener_csv(ejemploLista,indexReader);
            KMeans.kmeans(data,k);
            data.createCsvOutput("sampleClustered.csv");
        }

    }
}
